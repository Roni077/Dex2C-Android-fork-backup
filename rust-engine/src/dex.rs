//! Zero-copy Dalvik Executable (DEX) binary parser for Dex2C Studio.
//! Operates directly on borrowed memory slices (&[u8]) with zero JVM heap allocations.

use std::borrow::Cow;

#[derive(Debug, Clone, Copy)]
pub struct DexHeader {
    pub file_size: u32,
    pub string_ids_size: u32,
    pub string_ids_off: u32,
    pub type_ids_size: u32,
    pub type_ids_off: u32,
    pub proto_ids_size: u32,
    pub proto_ids_off: u32,
    pub field_ids_size: u32,
    pub field_ids_off: u32,
    pub method_ids_size: u32,
    pub method_ids_off: u32,
    pub class_defs_size: u32,
    pub class_defs_off: u32,
}

pub struct DexFile<'a> {
    pub data: &'a [u8],
    pub header: DexHeader,
}

#[derive(Debug, Clone, Copy)]
pub struct MethodRef<'a> {
    pub class_desc: &'a str,
    pub name: &'a str,
    pub return_type: &'a str,
}

#[derive(Debug, Clone, Copy)]
pub struct ClassMethodSummary<'a> {
    pub method_idx: u32,
    pub class_desc: &'a str,
    pub method_name: &'a str,
    pub is_init: bool,
    pub code_off: u32,
    pub access_flags: u32,
}

impl<'a> DexFile<'a> {
    pub fn parse(data: &'a [u8]) -> Result<Self, &'static str> {
        if data.len() < 0x70 {
            return Err("DEX binary too small (< 0x70 bytes)");
        }

        // Validate magic: "dex\n035\0" .. "dex\n041\0"
        if &data[0..4] != b"dex\n" {
            return Err("Invalid DEX magic header");
        }

        let endian_tag = read_u32_le(data, 0x28)?;
        if endian_tag != 0x12345678 {
            return Err("Unsupported endianness (only little-endian DEX is supported)");
        }

        let header = DexHeader {
            file_size: read_u32_le(data, 0x20)?,
            string_ids_size: read_u32_le(data, 0x38)?,
            string_ids_off: read_u32_le(data, 0x3C)?,
            type_ids_size: read_u32_le(data, 0x40)?,
            type_ids_off: read_u32_le(data, 0x44)?,
            proto_ids_size: read_u32_le(data, 0x48)?,
            proto_ids_off: read_u32_le(data, 0x4C)?,
            field_ids_size: read_u32_le(data, 0x50)?,
            field_ids_off: read_u32_le(data, 0x54)?,
            method_ids_size: read_u32_le(data, 0x58)?,
            method_ids_off: read_u32_le(data, 0x5C)?,
            class_defs_size: read_u32_le(data, 0x60)?,
            class_defs_off: read_u32_le(data, 0x64)?,
        };

        Ok(DexFile { data, header })
    }

    #[inline]
    pub fn string_count(&self) -> u32 {
        self.header.string_ids_size
    }

    #[inline]
    pub fn class_def_count(&self) -> u32 {
        self.header.class_defs_size
    }

    pub fn get_string(&self, string_idx: u32) -> Option<&'a str> {
        if string_idx >= self.header.string_ids_size {
            return None;
        }
        let off_offset = (self.header.string_ids_off + string_idx * 4) as usize;
        let str_data_off = read_u32_le(self.data, off_offset).ok()? as usize;
        if str_data_off >= self.data.len() {
            return None;
        }

        // Read ULEB128 utf16_size prefix
        let (_, mut cursor) = read_uleb128(self.data, str_data_off)?;
        let start = cursor;
        while cursor < self.data.len() && self.data[cursor] != 0 {
            cursor += 1;
        }

        // Fast zero-copy standard UTF-8 parsing
        std::str::from_utf8(&self.data[start..cursor]).ok()
    }

    pub fn get_type_descriptor(&self, type_idx: u32) -> Option<&'a str> {
        if type_idx >= self.header.type_ids_size {
            return None;
        }
        let off_offset = (self.header.type_ids_off + type_idx * 4) as usize;
        let descriptor_str_idx = read_u32_le(self.data, off_offset).ok()?;
        self.get_string(descriptor_str_idx)
    }

    pub fn get_method_name(&self, method_idx: u32) -> Option<&'a str> {
        if method_idx >= self.header.method_ids_size {
            return None;
        }
        let base = (self.header.method_ids_off + method_idx * 8) as usize;
        let name_idx = read_u32_le(self.data, base + 4).ok()?;
        self.get_string(name_idx)
    }

    pub fn get_method_class_desc(&self, method_idx: u32) -> Option<&'a str> {
        if method_idx >= self.header.method_ids_size {
            return None;
        }
        let base = (self.header.method_ids_off + method_idx * 8) as usize;
        let class_idx = read_u16_le(self.data, base).ok()? as u32;
        self.get_type_descriptor(class_idx)
    }

    pub fn get_class_def_desc(&self, class_def_idx: u32) -> Option<&'a str> {
        if class_def_idx >= self.header.class_defs_size {
            return None;
        }
        let base = (self.header.class_defs_off + class_def_idx * 32) as usize;
        let class_idx = read_u32_le(self.data, base).ok()?;
        self.get_type_descriptor(class_idx)
    }

    /// Iterates every method defined inside the DEX file, executing the callback.
    pub fn for_each_method<F>(&self, mut callback: F)
    where
        F: FnMut(ClassMethodSummary<'a>),
    {
        for c_idx in 0..self.header.class_defs_size {
            let base = (self.header.class_defs_off + c_idx * 32) as usize;
            let class_idx = match read_u32_le(self.data, base) {
                Ok(idx) => idx,
                Err(_) => continue,
            };
            let class_desc = match self.get_type_descriptor(class_idx) {
                Some(d) => d,
                None => continue,
            };

            let class_data_off = match read_u32_le(self.data, base + 24) {
                Ok(off) => off as usize,
                Err(_) => continue,
            };
            if class_data_off == 0 || class_data_off >= self.data.len() {
                continue;
            }

            let mut cursor = class_data_off;
            let (static_fields_size, c) = match read_uleb128(self.data, cursor) {
                Some(pair) => pair,
                None => continue,
            };
            cursor = c;
            let (instance_fields_size, c) = match read_uleb128(self.data, cursor) {
                Some(pair) => pair,
                None => continue,
            };
            cursor = c;
            let (direct_methods_size, c) = match read_uleb128(self.data, cursor) {
                Some(pair) => pair,
                None => continue,
            };
            cursor = c;
            let (virtual_methods_size, c) = match read_uleb128(self.data, cursor) {
                Some(pair) => pair,
                None => continue,
            };
            cursor = c;

            // Skip static fields
            for _ in 0..static_fields_size {
                let (_, c) = match read_uleb128(self.data, cursor) { Some(p) => p, None => break };
                let (_, c2) = match read_uleb128(self.data, c) { Some(p) => p, None => break };
                cursor = c2;
            }

            // Skip instance fields
            for _ in 0..instance_fields_size {
                let (_, c) = match read_uleb128(self.data, cursor) { Some(p) => p, None => break };
                let (_, c2) = match read_uleb128(self.data, c) { Some(p) => p, None => break };
                cursor = c2;
            }

            // Read direct methods
            let mut last_idx = 0u32;
            for _ in 0..direct_methods_size {
                let (idx_diff, c1) = match read_uleb128(self.data, cursor) { Some(p) => p, None => break };
                let (flags, c2) = match read_uleb128(self.data, c1) { Some(p) => p, None => break };
                let (code_off, c3) = match read_uleb128(self.data, c2) { Some(p) => p, None => break };
                cursor = c3;
                last_idx += idx_diff;

                if let Some(m_name) = self.get_method_name(last_idx) {
                    let is_init = m_name == "<init>";
                    callback(ClassMethodSummary {
                        method_idx: last_idx,
                        class_desc,
                        method_name: m_name,
                        is_init,
                        code_off,
                        access_flags: flags,
                    });
                }
            }

            // Read virtual methods
            last_idx = 0;
            for _ in 0..virtual_methods_size {
                let (idx_diff, c1) = match read_uleb128(self.data, cursor) { Some(p) => p, None => break };
                let (flags, c2) = match read_uleb128(self.data, c1) { Some(p) => p, None => break };
                let (code_off, c3) = match read_uleb128(self.data, c2) { Some(p) => p, None => break };
                cursor = c3;
                last_idx += idx_diff;

                if let Some(m_name) = self.get_method_name(last_idx) {
                    let is_init = m_name == "<init>";
                    callback(ClassMethodSummary {
                        method_idx: last_idx,
                        class_desc,
                        method_name: m_name,
                        is_init,
                        code_off,
                        access_flags: flags,
                    });
                }
            }
        }
    }
}

#[inline(always)]
fn read_u16_le(data: &[u8], offset: usize) -> Result<u16, &'static str> {
    if offset + 2 > data.len() {
        return Err("Buffer overrun reading u16");
    }
    Ok(u16::from_le_bytes([data[offset], data[offset + 1]]))
}

#[inline(always)]
fn read_u32_le(data: &[u8], offset: usize) -> Result<u32, &'static str> {
    if offset + 4 > data.len() {
        return Err("Buffer overrun reading u32");
    }
    Ok(u32::from_le_bytes([
        data[offset],
        data[offset + 1],
        data[offset + 2],
        data[offset + 3],
    ]))
}

#[inline(always)]
fn read_uleb128(data: &[u8], mut offset: usize) -> Option<(u32, usize)> {
    let mut result = 0u32;
    let mut shift = 0;
    while offset < data.len() {
        let byte = data[offset];
        offset += 1;
        result |= ((byte & 0x7F) as u32) << shift;
        if (byte & 0x80) == 0 {
            return Some((result, offset));
        }
        shift += 7;
        if shift > 35 {
            return None;
        }
    }
    None
}
