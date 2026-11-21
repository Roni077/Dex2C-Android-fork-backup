//! Zero-copy Dalvik Executable (DEX) binary parser and metadata extractor.

use std::borrow::Cow;
use std::collections::HashMap;
use dex2c_core::{Dex2cError, Result};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DexCodeMeta {
    pub registers_size: u16,
    pub ins_size: u16,
    pub outs_size: u16,
    pub tries_size: u16,
    pub debug_info_offset: u32,
    pub insns_count: u32,
    pub insns_offset_bytes: u32,
    pub code_item_offset: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DexFieldMeta<'a> {
    pub field_idx: u32,
    pub access_flags: u32,
    pub name: &'a str,
    pub type_desc: &'a str,
    pub is_static: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DexMethodMeta<'a> {
    pub method_idx: u32,
    pub access_flags: u32,
    pub name: &'a str,
    pub shorty: &'a str,
    pub return_type: &'a str,
    pub param_types: Vec<&'a str>,
    pub code_off: u32,
    pub is_direct: bool,
    pub code: Option<DexCodeMeta>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DexClassDefFull<'a> {
    pub class_def_idx: u32,
    pub class_idx: u32,
    pub access_flags: u32,
    pub descriptor: &'a str,
    pub superclass: Option<&'a str>,
    pub source_file: Option<&'a str>,
    pub interfaces: Vec<&'a str>,
    pub static_fields: Vec<DexFieldMeta<'a>>,
    pub instance_fields: Vec<DexFieldMeta<'a>>,
    pub direct_methods: Vec<DexMethodMeta<'a>>,
    pub virtual_methods: Vec<DexMethodMeta<'a>>,
    pub annotations_off: u32,
    pub static_values_off: u32,
    pub class_data_off: u32,
}

impl<'a> DexFile<'a> {
    pub fn parse(data: &'a [u8]) -> Result<Self> {
        if data.len() < 0x70 {
            return Err(Dex2cError::DexFormat("DEX file smaller than 0x70 header".into()));
        }

        if &data[0..4] != b"dex\n" {
            return Err(Dex2cError::DexFormat("Invalid DEX magic header".into()));
        }

        let endian = read_u32_le(data, 0x28)?;
        if endian != 0x12345678 {
            return Err(Dex2cError::DexFormat("Unsupported DEX endianness".into()));
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

        Ok(Self { data, header })
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

        let (_, mut cursor) = read_uleb128(self.data, str_data_off)?;
        let start = cursor;
        while cursor < self.data.len() && self.data[cursor] != 0 {
            cursor += 1;
        }

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

    pub fn get_proto(&self, proto_idx: u32) -> Option<(&'a str, &'a str, Vec<&'a str>)> {
        if proto_idx >= self.header.proto_ids_size {
            return None;
        }
        let base = (self.header.proto_ids_off + proto_idx * 12) as usize;
        let shorty_idx = read_u32_le(self.data, base).ok()?;
        let return_type_idx = read_u32_le(self.data, base + 4).ok()?;
        let parameters_off = read_u32_le(self.data, base + 8).ok()? as usize;

        let shorty = self.get_string(shorty_idx).unwrap_or("V");
        let return_type = self.get_type_descriptor(return_type_idx).unwrap_or("V");

        let mut params = Vec::new();
        if parameters_off != 0 && parameters_off + 4 <= self.data.len() {
            if let Ok(count) = read_u32_le(self.data, parameters_off) {
                let size = count as usize;
                let list_base = parameters_off + 4;
                if list_base + size * 2 <= self.data.len() {
                    params.reserve_exact(size);
                    for p in 0..size {
                        let t_idx = read_u16_le(self.data, list_base + p * 2).unwrap_or(0) as u32;
                        let t_desc = self.get_type_descriptor(t_idx).unwrap_or("Ljava/lang/Object;");
                        params.push(t_desc);
                    }
                }
            }
        }

        Some((shorty, return_type, params))
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

    pub fn get_method_proto(&self, method_idx: u32) -> Option<(&'a str, &'a str, Vec<&'a str>)> {
        if method_idx >= self.header.method_ids_size {
            return None;
        }
        let base = (self.header.method_ids_off + method_idx * 8) as usize;
        let proto_idx = read_u16_le(self.data, base + 2).ok()? as u32;
        self.get_proto(proto_idx)
    }

    pub fn get_field_info(&self, field_idx: u32) -> Option<(&'a str, &'a str)> {
        if field_idx >= self.header.field_ids_size {
            return None;
        }
        let base = (self.header.field_ids_off + field_idx * 8) as usize;
        let type_idx = read_u16_le(self.data, base + 2).ok()? as u32;
        let name_idx = read_u32_le(self.data, base + 4).ok()?;
        let name = self.get_string(name_idx)?;
        let type_desc = self.get_type_descriptor(type_idx)?;
        Some((name, type_desc))
    }

    pub fn get_code_meta(&self, code_off: u32) -> Option<DexCodeMeta> {
        if code_off == 0 {
            return None;
        }
        let off = code_off as usize;
        if off + 16 > self.data.len() {
            return None;
        }
        let registers_size = read_u16_le(self.data, off).ok()?;
        let ins_size = read_u16_le(self.data, off + 2).ok()?;
        let outs_size = read_u16_le(self.data, off + 4).ok()?;
        let tries_size = read_u16_le(self.data, off + 6).ok()?;
        let debug_info_offset = read_u32_le(self.data, off + 8).ok()?;
        let insns_count = read_u32_le(self.data, off + 12).ok()?;

        Some(DexCodeMeta {
            registers_size,
            ins_size,
            outs_size,
            tries_size,
            debug_info_offset,
            insns_count,
            insns_offset_bytes: code_off + 16,
            code_item_offset: code_off,
        })
    }

    pub fn get_insns_units(&self, meta: &DexCodeMeta) -> Option<&'a [u16]> {
        let byte_start = meta.insns_offset_bytes as usize;
        let byte_len = (meta.insns_count as usize) * 2;
        if byte_start + byte_len > self.data.len() {
            return None;
        }
        let slice_u8 = &self.data[byte_start..byte_start + byte_len];
        let ptr = slice_u8.as_ptr() as *const u16;
        Some(unsafe { std::slice::from_raw_parts(ptr, meta.insns_count as usize) })
    }

    pub fn get_class_def_full(&self, class_def_idx: u32) -> Option<DexClassDefFull<'a>> {
        if class_def_idx >= self.header.class_defs_size {
            return None;
        }
        let base = (self.header.class_defs_off + class_def_idx * 32) as usize;
        if base + 32 > self.data.len() {
            return None;
        }

        let class_idx = read_u32_le(self.data, base).ok()?;
        let access_flags = read_u32_le(self.data, base + 4).ok()?;
        let superclass_idx = read_u32_le(self.data, base + 8).ok()?;
        let interfaces_off = read_u32_le(self.data, base + 12).ok()? as usize;
        let source_file_idx = read_u32_le(self.data, base + 16).ok()?;
        let annotations_off = read_u32_le(self.data, base + 20).ok()?;
        let class_data_off = read_u32_le(self.data, base + 24).ok()?;
        let static_values_off = read_u32_le(self.data, base + 28).ok()?;

        let descriptor = self.get_type_descriptor(class_idx)?;
        let superclass = if superclass_idx != 0xFFFFFFFF && superclass_idx < self.header.type_ids_size {
            self.get_type_descriptor(superclass_idx)
        } else {
            None
        };

        let source_file = if source_file_idx != 0xFFFFFFFF && source_file_idx < self.header.string_ids_size {
            self.get_string(source_file_idx)
        } else {
            None
        };

        let mut interfaces = Vec::new();
        if interfaces_off != 0 && interfaces_off + 4 <= self.data.len() {
            if let Ok(count) = read_u32_le(self.data, interfaces_off) {
                let count = count as usize;
                let list_base = interfaces_off + 4;
                if list_base + count * 2 <= self.data.len() {
                    interfaces.reserve_exact(count);
                    for k in 0..count {
                        let t_idx = read_u16_le(self.data, list_base + k * 2).unwrap_or(0) as u32;
                        if let Some(iface_desc) = self.get_type_descriptor(t_idx) {
                            interfaces.push(iface_desc);
                        }
                    }
                }
            }
        }

        let mut static_fields = Vec::new();
        let mut instance_fields = Vec::new();
        let mut direct_methods = Vec::new();
        let mut virtual_methods = Vec::new();

        let c_data_off = class_data_off as usize;
        if c_data_off != 0 && c_data_off < self.data.len() {
            let mut cursor = c_data_off;
            let (static_fields_size, c) = read_uleb128(self.data, cursor)?;
            cursor = c;
            let (instance_fields_size, c) = read_uleb128(self.data, cursor)?;
            cursor = c;
            let (direct_methods_size, c) = read_uleb128(self.data, cursor)?;
            cursor = c;
            let (virtual_methods_size, c) = read_uleb128(self.data, cursor)?;
            cursor = c;

            let mut field_idx = 0u32;
            for _ in 0..static_fields_size {
                let (idx_diff, c1) = read_uleb128(self.data, cursor)?;
                let (flags, c2) = read_uleb128(self.data, c1)?;
                cursor = c2;
                field_idx += idx_diff;
                if let Some((name, type_desc)) = self.get_field_info(field_idx) {
                    static_fields.push(DexFieldMeta {
                        field_idx,
                        access_flags: flags,
                        name,
                        type_desc,
                        is_static: true,
                    });
                }
            }

            field_idx = 0;
            for _ in 0..instance_fields_size {
                let (idx_diff, c1) = read_uleb128(self.data, cursor)?;
                let (flags, c2) = read_uleb128(self.data, c1)?;
                cursor = c2;
                field_idx += idx_diff;
                if let Some((name, type_desc)) = self.get_field_info(field_idx) {
                    instance_fields.push(DexFieldMeta {
                        field_idx,
                        access_flags: flags,
                        name,
                        type_desc,
                        is_static: false,
                    });
                }
            }

            let mut method_idx = 0u32;
            for _ in 0..direct_methods_size {
                let (idx_diff, c1) = read_uleb128(self.data, cursor)?;
                let (flags, c2) = read_uleb128(self.data, c1)?;
                let (code_off, c3) = read_uleb128(self.data, c2)?;
                cursor = c3;
                method_idx += idx_diff;

                let name = self.get_method_name(method_idx).unwrap_or("unknown");
                let (shorty, return_type, param_types) = self.get_method_proto(method_idx).unwrap_or(("V", "V", Vec::new()));
                let code = self.get_code_meta(code_off);

                direct_methods.push(DexMethodMeta {
                    method_idx,
                    access_flags: flags,
                    name,
                    shorty,
                    return_type,
                    param_types,
                    code_off,
                    is_direct: true,
                    code,
                });
            }

            method_idx = 0;
            for _ in 0..virtual_methods_size {
                let (idx_diff, c1) = read_uleb128(self.data, cursor)?;
                let (flags, c2) = read_uleb128(self.data, c1)?;
                let (code_off, c3) = read_uleb128(self.data, c2)?;
                cursor = c3;
                method_idx += idx_diff;

                let name = self.get_method_name(method_idx).unwrap_or("unknown");
                let (shorty, return_type, param_types) = self.get_method_proto(method_idx).unwrap_or(("V", "V", Vec::new()));
                let code = self.get_code_meta(code_off);

                virtual_methods.push(DexMethodMeta {
                    method_idx,
                    access_flags: flags,
                    name,
                    shorty,
                    return_type,
                    param_types,
                    code_off,
                    is_direct: false,
                    code,
                });
            }
        }

        Some(DexClassDefFull {
            class_def_idx,
            class_idx,
            access_flags,
            descriptor,
            superclass,
            source_file,
            interfaces,
            static_fields,
            instance_fields,
            direct_methods,
            virtual_methods,
            annotations_off,
            static_values_off,
            class_data_off,
        })
    }
}

#[inline(always)]
pub fn read_u16_le(data: &[u8], offset: usize) -> Result<u16> {
    if offset + 2 > data.len() {
        return Err(Dex2cError::DexFormat("Buffer overrun reading u16".into()));
    }
    Ok(u16::from_le_bytes([data[offset], data[offset + 1]]))
}

#[inline(always)]
pub fn read_u32_le(data: &[u8], offset: usize) -> Result<u32> {
    if offset + 4 > data.len() {
        return Err(Dex2cError::DexFormat("Buffer overrun reading u32".into()));
    }
    Ok(u32::from_le_bytes([
        data[offset],
        data[offset + 1],
        data[offset + 2],
        data[offset + 3],
    ]))
}

#[inline(always)]
pub fn read_uleb128(data: &[u8], mut offset: usize) -> Option<(u32, usize)> {
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
