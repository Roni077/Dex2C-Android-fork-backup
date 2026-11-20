//! Zero-copy Dalvik Executable (DEX) binary parser and high-performance
//! D2CN packed binary serializer for Dex2C Studio.
//! Operates directly on borrowed memory slices (&[u8]) with zero JVM heap churn.

use std::borrow::Cow;
use std::collections::HashMap;

/// Standard Dalvik Executable 112-byte (0x70) Header layout.
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

/// Zero-copy DEX file accessor wrapping a borrowed memory slice.
pub struct DexFile<'a> {
    pub data: &'a [u8],
    pub header: DexHeader,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct MethodRef<'a> {
    pub class_desc: &'a str,
    pub name: &'a str,
    pub return_type: &'a str,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ClassMethodSummary<'a> {
    pub method_idx: u32,
    pub class_idx: u32,
    pub class_desc: &'a str,
    pub method_name: &'a str,
    pub is_init: bool,
    pub code_off: u32,
    pub access_flags: u32,
}

/// Detailed metadata of a Dalvik code item from code_off.
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

/// Metadata for an encoded field extracted from DEX class_data_item and field_id_item.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DexFieldMeta<'a> {
    pub field_idx: u32,
    pub access_flags: u32,
    pub name: &'a str,
    pub type_desc: &'a str,
    pub is_static: bool,
}

/// Direct or virtual method metadata with prototype and Dalvik code item details.
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

/// Complete class definition extracted from DEX class_def_item.
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

/// Hierarchical package tree node for zero-heap-churn grouping in Rust.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PackageTreeNode<'a> {
    pub package_name: &'a str,
    pub display_label: &'a str,
    pub depth: u16,
    pub parent_idx: u32,
    pub direct_classes: Vec<u32>,
    pub total_subtree_classes: u32,
    pub total_bytecode_methods: u32,
    pub children: Vec<u32>,
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

    pub fn get_class_def_desc(&self, class_def_idx: u32) -> Option<&'a str> {
        if class_def_idx >= self.header.class_defs_size {
            return None;
        }
        let base = (self.header.class_defs_off + class_def_idx * 32) as usize;
        let class_idx = read_u32_le(self.data, base).ok()?;
        self.get_type_descriptor(class_idx)
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

    /// Extracts complete class definition including superclass, interfaces, fields, methods, and code metadata.
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

            // Static fields
            let mut field_idx = 0u32;
            static_fields.reserve_exact(static_fields_size as usize);
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

            // Instance fields
            field_idx = 0;
            instance_fields.reserve_exact(instance_fields_size as usize);
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

            // Direct methods
            let mut method_idx = 0u32;
            direct_methods.reserve_exact(direct_methods_size as usize);
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

            // Virtual methods
            method_idx = 0;
            virtual_methods.reserve_exact(virtual_methods_size as usize);
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
                        class_idx,
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
                        class_idx,
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

    /// Packs all class definitions, methods, fields, and string pool into the unified D2CN binary buffer.
    /// Format:
    /// - Magic: 0x4E433244 ('D','2','C','N') in LE
    /// - Version: 1 (u16), Flags: 0 (u16), HeaderSize: 32 (u32)
    /// - string_count: u32, class_count: u32, method_count: u32, string_pool_off: u32, classes_off: u32
    /// - String Pool: sequence of [len: u16, data: u8[len]]
    /// - Classes: sequence of packed class records with nested field and method records.
    pub fn pack_dex_classes(&self) -> Vec<u8> {
        let class_count = self.header.class_defs_size;
        let mut classes = Vec::with_capacity(class_count as usize);
        let mut total_methods = 0u32;

        for i in 0..class_count {
            if let Some(cls) = self.get_class_def_full(i) {
                total_methods += (cls.direct_methods.len() + cls.virtual_methods.len()) as u32;
                classes.push(cls);
            }
        }

        let mut pool = StringPool::new();

        // Intern class descriptors, package names, simple names, superclasses, interfaces, etc.
        struct ClassIndices {
            descriptor_idx: u32,
            full_name_idx: i32,
            pkg_name_idx: i32,
            simple_name_idx: i32,
            superclass_idx: i32,
            source_file_idx: i32,
            interface_indices: Vec<u32>,
            static_field_indices: Vec<(u32, u32)>, // (name_idx, type_idx)
            instance_field_indices: Vec<(u32, u32)>,
            direct_method_indices: Vec<(u32, u32, u32, Vec<u32>)>, // (name_idx, shorty_idx, ret_idx, param_indices)
            virtual_method_indices: Vec<(u32, u32, u32, Vec<u32>)>,
        }

        let mut pre_indexed = Vec::with_capacity(classes.len());

        for cls in &classes {
            let desc_idx = pool.intern_borrowed(cls.descriptor);

            let (full_name, pkg_name, simple_name) = parse_class_descriptor(cls.descriptor);
            let full_name_idx = pool.intern_owned(full_name) as i32;
            let pkg_name_idx = pool.intern_owned(pkg_name) as i32;
            let simple_name_idx = pool.intern_owned(simple_name) as i32;

            let superclass_idx = cls.superclass.map(|s| pool.intern_borrowed(s) as i32).unwrap_or(-1);
            let source_file_idx = cls.source_file.map(|s| pool.intern_borrowed(s) as i32).unwrap_or(-1);

            let mut interface_indices = Vec::with_capacity(cls.interfaces.len());
            for &iface in &cls.interfaces {
                interface_indices.push(pool.intern_borrowed(iface));
            }

            let mut static_field_indices = Vec::with_capacity(cls.static_fields.len());
            for f in &cls.static_fields {
                let n_idx = pool.intern_borrowed(f.name);
                let t_idx = pool.intern_borrowed(f.type_desc);
                static_field_indices.push((n_idx, t_idx));
            }

            let mut instance_field_indices = Vec::with_capacity(cls.instance_fields.len());
            for f in &cls.instance_fields {
                let n_idx = pool.intern_borrowed(f.name);
                let t_idx = pool.intern_borrowed(f.type_desc);
                instance_field_indices.push((n_idx, t_idx));
            }

            let mut direct_method_indices = Vec::with_capacity(cls.direct_methods.len());
            for m in &cls.direct_methods {
                let n_idx = pool.intern_borrowed(m.name);
                let s_idx = pool.intern_borrowed(m.shorty);
                let r_idx = pool.intern_borrowed(m.return_type);
                let mut p_indices = Vec::with_capacity(m.param_types.len());
                for &p in &m.param_types {
                    p_indices.push(pool.intern_borrowed(p));
                }
                direct_method_indices.push((n_idx, s_idx, r_idx, p_indices));
            }

            let mut virtual_method_indices = Vec::with_capacity(cls.virtual_methods.len());
            for m in &cls.virtual_methods {
                let n_idx = pool.intern_borrowed(m.name);
                let s_idx = pool.intern_borrowed(m.shorty);
                let r_idx = pool.intern_borrowed(m.return_type);
                let mut p_indices = Vec::with_capacity(m.param_types.len());
                for &p in &m.param_types {
                    p_indices.push(pool.intern_borrowed(p));
                }
                virtual_method_indices.push((n_idx, s_idx, r_idx, p_indices));
            }

            pre_indexed.push(ClassIndices {
                descriptor_idx: desc_idx,
                full_name_idx,
                pkg_name_idx,
                simple_name_idx,
                superclass_idx,
                source_file_idx,
                interface_indices,
                static_field_indices,
                instance_field_indices,
                direct_method_indices,
                virtual_method_indices,
            });
        }

        // Start serializing into buffer
        let mut writer = BufferWriter::new(64 * 1024);

        // Header: 32 bytes (0x20)
        writer.write_u32(0x4E433244); // Magic: 'D','2','C','N' in LE
        writer.write_u16(1);          // Version: 1
        writer.write_u16(0);          // Flags: 0
        writer.write_u32(32);         // Header size: 32
        writer.write_u32(pool.entries.len() as u32);
        writer.write_u32(classes.len() as u32);
        writer.write_u32(total_methods);
        writer.write_u32(32);         // string_pool_off (immediately following header)
        let classes_off_pos = writer.len();
        writer.write_u32(0);          // classes_off placeholder

        // Serialize String Pool: [len: u16, data: u8[len]]
        for s in &pool.entries {
            let bytes = s.as_bytes();
            let len = bytes.len().min(u16::MAX as usize) as u16;
            writer.write_u16(len);
            writer.write_bytes(&bytes[..len as usize]);
        }

        // Patch classes_off in header
        let classes_off = writer.len() as u32;
        writer.patch_u32(classes_off_pos, classes_off);

        // Serialize Class Records
        for (i, cls) in classes.iter().enumerate() {
            let idxs = &pre_indexed[i];

            writer.write_u32(cls.class_def_idx);
            writer.write_u32(cls.access_flags);
            writer.write_u32(idxs.descriptor_idx);
            writer.write_i32(idxs.full_name_idx);
            writer.write_i32(idxs.pkg_name_idx);
            writer.write_i32(idxs.simple_name_idx);
            writer.write_i32(idxs.superclass_idx);
            writer.write_i32(idxs.source_file_idx);
            writer.write_u32(cls.annotations_off);
            writer.write_u32(cls.static_values_off);
            writer.write_u32(cls.class_data_off);

            // Interfaces
            writer.write_u16(idxs.interface_indices.len() as u16);
            for &iface_idx in &idxs.interface_indices {
                writer.write_u32(iface_idx);
            }

            // Static fields
            writer.write_u16(cls.static_fields.len() as u16);
            for (f_idx, f) in cls.static_fields.iter().enumerate() {
                let (n_idx, t_idx) = idxs.static_field_indices[f_idx];
                writer.write_u32(f.field_idx);
                writer.write_u32(f.access_flags);
                writer.write_u32(n_idx);
                writer.write_u32(t_idx);
            }

            // Instance fields
            writer.write_u16(cls.instance_fields.len() as u16);
            for (f_idx, f) in cls.instance_fields.iter().enumerate() {
                let (n_idx, t_idx) = idxs.instance_field_indices[f_idx];
                writer.write_u32(f.field_idx);
                writer.write_u32(f.access_flags);
                writer.write_u32(n_idx);
                writer.write_u32(t_idx);
            }

            // Direct methods
            writer.write_u16(cls.direct_methods.len() as u16);
            for (m_idx, m) in cls.direct_methods.iter().enumerate() {
                let (n_idx, s_idx, r_idx, ref p_idxs) = idxs.direct_method_indices[m_idx];
                writer.write_u32(m.method_idx);
                writer.write_u32(m.access_flags);
                writer.write_u32(n_idx);
                writer.write_u32(s_idx);
                writer.write_u32(r_idx);
                writer.write_u16(p_idxs.len() as u16);
                for &p in p_idxs {
                    writer.write_u32(p);
                }

                if let Some(ref code) = m.code {
                    writer.write_u8(1);
                    writer.write_u16(code.registers_size);
                    writer.write_u16(code.ins_size);
                    writer.write_u16(code.outs_size);
                    writer.write_u16(code.tries_size);
                    writer.write_u32(code.insns_count);
                    writer.write_u32(code.insns_offset_bytes);
                    writer.write_u32(code.code_item_offset);
                    writer.write_u32(code.debug_info_offset);
                } else {
                    writer.write_u8(0);
                }
            }

            // Virtual methods
            writer.write_u16(cls.virtual_methods.len() as u16);
            for (m_idx, m) in cls.virtual_methods.iter().enumerate() {
                let (n_idx, s_idx, r_idx, ref p_idxs) = idxs.virtual_method_indices[m_idx];
                writer.write_u32(m.method_idx);
                writer.write_u32(m.access_flags);
                writer.write_u32(n_idx);
                writer.write_u32(s_idx);
                writer.write_u32(r_idx);
                writer.write_u16(p_idxs.len() as u16);
                for &p in p_idxs {
                    writer.write_u32(p);
                }

                if let Some(ref code) = m.code {
                    writer.write_u8(1);
                    writer.write_u16(code.registers_size);
                    writer.write_u16(code.ins_size);
                    writer.write_u16(code.outs_size);
                    writer.write_u16(code.tries_size);
                    writer.write_u32(code.insns_count);
                    writer.write_u32(code.insns_offset_bytes);
                    writer.write_u32(code.code_item_offset);
                    writer.write_u32(code.debug_info_offset);
                } else {
                    writer.write_u8(0);
                }
            }
        }

        writer.into_vec()
    }
}

/// Zero-copy package name and simple name extraction from class descriptor.
/// E.g. "Lcom/example/MyClass;" -> package: "com/example", simple: "MyClass"
/// E.g. "LMainActivity;" -> package: "", simple: "MainActivity"
#[inline]
pub fn extract_package_and_simple_name(desc: &str) -> (&str, &str) {
    let s = desc.strip_prefix('L').unwrap_or(desc);
    let s = s.strip_suffix(';').unwrap_or(s);
    if let Some(idx) = s.rfind('/') {
        (&s[..idx], &s[idx + 1..])
    } else {
        ("", s)
    }
}

/// Parses class descriptor into full name, normalized package name, and simple name.
/// Matches Kotlin's parseClassDescriptor logic.
pub fn parse_class_descriptor(desc: &str) -> (String, String, String) {
    let (pkg_slice, simple_slice) = extract_package_and_simple_name(desc);
    if pkg_slice.is_empty() {
        (simple_slice.to_string(), "(default)".to_string(), simple_slice.to_string())
    } else {
        let pkg_normalized = pkg_slice.replace('/', ".");
        let full_name = format!("{}.{}", pkg_normalized, simple_slice);
        (full_name, pkg_normalized, simple_slice.to_string())
    }
}

/// Builds a hierarchical package tree from extracted classes with zero heap churn.
pub fn build_package_tree<'a>(classes: &[DexClassDefFull<'a>]) -> Vec<PackageTreeNode<'a>> {
    if classes.is_empty() {
        return Vec::new();
    }

    let mut nodes: Vec<PackageTreeNode<'a>> = Vec::new();
    let mut pkg_map: HashMap<&'a str, usize> = HashMap::new();

    // Map each class into its package node
    for (cls_idx, cls) in classes.iter().enumerate() {
        let (pkg, _) = extract_package_and_simple_name(cls.descriptor);
        let node_idx = if let Some(&idx) = pkg_map.get(pkg) {
            idx
        } else {
            let idx = nodes.len();
            pkg_map.insert(pkg, idx);
            let display_label = if pkg.is_empty() {
                "(default)"
            } else {
                pkg.rsplit('/').next().unwrap_or(pkg)
            };
            nodes.push(PackageTreeNode {
                package_name: if pkg.is_empty() { "(default)" } else { pkg },
                display_label,
                depth: if pkg.is_empty() { 0 } else { pkg.split('/').count() as u16 },
                parent_idx: u32::MAX,
                direct_classes: Vec::new(),
                total_subtree_classes: 0,
                total_bytecode_methods: 0,
                children: Vec::new(),
            });
            idx
        };

        nodes[node_idx].direct_classes.push(cls_idx as u32);
        let bytecode_methods = cls.direct_methods.iter().filter(|m| m.code.is_some()).count()
            + cls.virtual_methods.iter().filter(|m| m.code.is_some()).count();
        nodes[node_idx].total_bytecode_methods += bytecode_methods as u32;
    }

    // Set subtree counts
    for node in &mut nodes {
        node.total_subtree_classes = node.direct_classes.len() as u32;
    }

    nodes
}

/// Internal deduplicating string pool builder for D2CN packaging.
struct StringPool<'a> {
    entries: Vec<Cow<'a, str>>,
    index_map: HashMap<Cow<'a, str>, u32>,
}

impl<'a> StringPool<'a> {
    fn new() -> Self {
        Self {
            entries: Vec::new(),
            index_map: HashMap::new(),
        }
    }

    fn intern(&mut self, s: Cow<'a, str>) -> u32 {
        if let Some(&idx) = self.index_map.get(&s) {
            idx
        } else {
            let idx = self.entries.len() as u32;
            self.index_map.insert(s.clone(), idx);
            self.entries.push(s);
            idx
        }
    }

    #[inline]
    fn intern_borrowed(&mut self, s: &'a str) -> u32 {
        self.intern(Cow::Borrowed(s))
    }

    #[inline]
    fn intern_owned(&mut self, s: String) -> u32 {
        self.intern(Cow::Owned(s))
    }
}

/// Fast little-endian byte buffer writer.
struct BufferWriter {
    buf: Vec<u8>,
}

impl BufferWriter {
    fn new(capacity: usize) -> Self {
        Self {
            buf: Vec::with_capacity(capacity),
        }
    }

    #[inline]
    fn write_u8(&mut self, val: u8) {
        self.buf.push(val);
    }

    #[inline]
    fn write_u16(&mut self, val: u16) {
        self.buf.extend_from_slice(&val.to_le_bytes());
    }

    #[inline]
    fn write_u32(&mut self, val: u32) {
        self.buf.extend_from_slice(&val.to_le_bytes());
    }

    #[inline]
    fn write_i32(&mut self, val: i32) {
        self.buf.extend_from_slice(&val.to_le_bytes());
    }

    #[inline]
    fn write_bytes(&mut self, bytes: &[u8]) {
        self.buf.extend_from_slice(bytes);
    }

    #[inline]
    fn len(&self) -> usize {
        self.buf.len()
    }

    #[inline]
    fn patch_u32(&mut self, offset: usize, val: u32) {
        let bytes = val.to_le_bytes();
        self.buf[offset..offset + 4].copy_from_slice(&bytes);
    }

    #[inline]
    fn into_vec(self) -> Vec<u8> {
        self.buf
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_package_and_simple_name() {
        assert_eq!(
            extract_package_and_simple_name("Lcom/example/MyClass;"),
            ("com/example", "MyClass")
        );
        assert_eq!(
            extract_package_and_simple_name("LMainActivity;"),
            ("", "MainActivity")
        );
        assert_eq!(
            extract_package_and_simple_name("Lfoo/bar/Outer$Inner;"),
            ("foo/bar", "Outer$Inner")
        );
    }

    #[test]
    fn test_parse_class_descriptor() {
        let (full, pkg, simple) = parse_class_descriptor("Lcom/example/MyClass;");
        assert_eq!(full, "com.example.MyClass");
        assert_eq!(pkg, "com.example");
        assert_eq!(simple, "MyClass");

        let (full_def, pkg_def, simple_def) = parse_class_descriptor("LMainActivity;");
        assert_eq!(full_def, "MainActivity");
        assert_eq!(pkg_def, "(default)");
        assert_eq!(simple_def, "MainActivity");
    }

    #[test]
    fn test_package_tree_grouping() {
        let classes = vec![
            DexClassDefFull {
                class_def_idx: 0,
                class_idx: 0,
                access_flags: 1,
                descriptor: "Lcom/example/A;",
                superclass: None,
                source_file: None,
                interfaces: Vec::new(),
                static_fields: Vec::new(),
                instance_fields: Vec::new(),
                direct_methods: Vec::new(),
                virtual_methods: Vec::new(),
                annotations_off: 0,
                static_values_off: 0,
                class_data_off: 0,
            },
            DexClassDefFull {
                class_def_idx: 1,
                class_idx: 1,
                access_flags: 1,
                descriptor: "Lcom/example/B;",
                superclass: None,
                source_file: None,
                interfaces: Vec::new(),
                static_fields: Vec::new(),
                instance_fields: Vec::new(),
                direct_methods: Vec::new(),
                virtual_methods: Vec::new(),
                annotations_off: 0,
                static_values_off: 0,
                class_data_off: 0,
            },
            DexClassDefFull {
                class_def_idx: 2,
                class_idx: 2,
                access_flags: 1,
                descriptor: "LRootClass;",
                superclass: None,
                source_file: None,
                interfaces: Vec::new(),
                static_fields: Vec::new(),
                instance_fields: Vec::new(),
                direct_methods: Vec::new(),
                virtual_methods: Vec::new(),
                annotations_off: 0,
                static_values_off: 0,
                class_data_off: 0,
            },
        ];

        let tree = build_package_tree(&classes);
        assert_eq!(tree.len(), 2); // "com/example" and "(default)"

        let com_pkg = tree.iter().find(|n| n.package_name == "com/example").unwrap();
        assert_eq!(com_pkg.direct_classes, vec![0, 1]);
        assert_eq!(com_pkg.total_subtree_classes, 2);

        let def_pkg = tree.iter().find(|n| n.package_name == "(default)").unwrap();
        assert_eq!(def_pkg.direct_classes, vec![2]);
        assert_eq!(def_pkg.total_subtree_classes, 1);
    }

    #[test]
    fn test_synthetic_dex_parse_and_pack_roundtrip() {
        // Construct a synthetic 512-byte valid DEX binary
        let mut dex_bytes = vec![0u8; 512];

        // Magic "dex\n035\0"
        dex_bytes[0..8].copy_from_slice(b"dex\n035\0");
        // Endian tag 0x12345678 @ 0x28
        dex_bytes[0x28..0x2C].copy_from_slice(&0x12345678u32.to_le_bytes());
        // file_size = 512 @ 0x20
        dex_bytes[0x20..0x24].copy_from_slice(&512u32.to_le_bytes());

        // String IDs: 7 strings @ 0x70
        let string_count = 7u32;
        dex_bytes[0x38..0x3C].copy_from_slice(&string_count.to_le_bytes());
        dex_bytes[0x3C..0x40].copy_from_slice(&0x70u32.to_le_bytes());

        // String data payload offsets table: 0x70 .. 0x8C (7 * 4 bytes)
        let mut str_data_off = 0x90usize;
        let strings = [
            "Lcom/example/TestClass;",
            "Ljava/lang/Object;",
            "Ljava/lang/Runnable;",
            "TestClass.java",
            "myField",
            "testMethod",
            "V",
        ];

        for (i, s) in strings.iter().enumerate() {
            let offset_pos = 0x70 + i * 4;
            dex_bytes[offset_pos..offset_pos + 4].copy_from_slice(&(str_data_off as u32).to_le_bytes());

            // Write ULEB128 len then UTF-8 bytes + 0
            dex_bytes[str_data_off] = s.len() as u8;
            str_data_off += 1;
            dex_bytes[str_data_off..str_data_off + s.len()].copy_from_slice(s.as_bytes());
            str_data_off += s.len();
            dex_bytes[str_data_off] = 0; // null terminator
            str_data_off += 1;
        }

        // Align str_data_off to 4 bytes
        if str_data_off % 4 != 0 {
            str_data_off += 4 - (str_data_off % 4);
        }

        // Type IDs: 4 types (TestClass=0, Object=1, Runnable=2, V=6) @ str_data_off
        let type_ids_off = str_data_off as u32;
        let type_count = 4u32;
        dex_bytes[0x40..0x44].copy_from_slice(&type_count.to_le_bytes());
        dex_bytes[0x44..0x48].copy_from_slice(&type_ids_off.to_le_bytes());

        let type_str_indices = [0u32, 1, 2, 6];
        for (i, &s_idx) in type_str_indices.iter().enumerate() {
            let pos = type_ids_off as usize + i * 4;
            dex_bytes[pos..pos + 4].copy_from_slice(&s_idx.to_le_bytes());
        }
        str_data_off += type_count as usize * 4;

        // Proto IDs: 1 proto (return type V=3, shorty V=6, params=0) @ str_data_off
        let proto_ids_off = str_data_off as u32;
        let proto_count = 1u32;
        dex_bytes[0x48..0x4C].copy_from_slice(&proto_count.to_le_bytes());
        dex_bytes[0x4C..0x50].copy_from_slice(&proto_ids_off.to_le_bytes());

        let p_pos = proto_ids_off as usize;
        dex_bytes[p_pos..p_pos + 4].copy_from_slice(&6u32.to_le_bytes()); // shorty "V"
        dex_bytes[p_pos + 4..p_pos + 8].copy_from_slice(&3u32.to_le_bytes()); // return_type "V"
        dex_bytes[p_pos + 8..p_pos + 12].copy_from_slice(&0u32.to_le_bytes()); // no params
        str_data_off += 12;

        // Field IDs: 1 field (class=0, type=3, name=4 "myField") @ str_data_off
        let field_ids_off = str_data_off as u32;
        let field_count = 1u32;
        dex_bytes[0x50..0x54].copy_from_slice(&field_count.to_le_bytes());
        dex_bytes[0x54..0x58].copy_from_slice(&field_ids_off.to_le_bytes());

        let f_pos = field_ids_off as usize;
        dex_bytes[f_pos..f_pos + 2].copy_from_slice(&0u16.to_le_bytes()); // class TestClass
        dex_bytes[f_pos + 2..f_pos + 4].copy_from_slice(&3u16.to_le_bytes()); // type V
        dex_bytes[f_pos + 4..f_pos + 8].copy_from_slice(&4u32.to_le_bytes()); // name "myField"
        str_data_off += 8;

        // Method IDs: 1 method (class=0, proto=0, name=5 "testMethod") @ str_data_off
        let method_ids_off = str_data_off as u32;
        let method_count = 1u32;
        dex_bytes[0x58..0x5C].copy_from_slice(&method_count.to_le_bytes());
        dex_bytes[0x5C..0x60].copy_from_slice(&method_ids_off.to_le_bytes());

        let m_pos = method_ids_off as usize;
        dex_bytes[m_pos..m_pos + 2].copy_from_slice(&0u16.to_le_bytes()); // class TestClass
        dex_bytes[m_pos + 2..m_pos + 4].copy_from_slice(&0u16.to_le_bytes()); // proto 0
        dex_bytes[m_pos + 4..m_pos + 8].copy_from_slice(&5u32.to_le_bytes()); // name "testMethod"
        str_data_off += 8;

        // Interfaces type_list: count=1, type_idx=2 (Runnable) @ str_data_off
        let iface_list_off = str_data_off as u32;
        dex_bytes[str_data_off..str_data_off + 4].copy_from_slice(&1u32.to_le_bytes());
        dex_bytes[str_data_off + 4..str_data_off + 6].copy_from_slice(&2u16.to_le_bytes());
        str_data_off += 8; // padded to 4

        // Code Item @ str_data_off
        let code_item_off = str_data_off as u32;
        dex_bytes[str_data_off..str_data_off + 2].copy_from_slice(&4u16.to_le_bytes()); // registers=4
        dex_bytes[str_data_off + 2..str_data_off + 4].copy_from_slice(&1u16.to_le_bytes()); // ins=1
        dex_bytes[str_data_off + 4..str_data_off + 6].copy_from_slice(&2u16.to_le_bytes()); // outs=2
        dex_bytes[str_data_off + 6..str_data_off + 8].copy_from_slice(&0u16.to_le_bytes()); // tries=0
        dex_bytes[str_data_off + 8..str_data_off + 12].copy_from_slice(&0u32.to_le_bytes()); // debug=0
        dex_bytes[str_data_off + 12..str_data_off + 16].copy_from_slice(&3u32.to_le_bytes()); // insns_count=3
        str_data_off += 24;

        // Class Data Item @ str_data_off
        let class_data_off = str_data_off as u32;
        dex_bytes[str_data_off] = 0; // static_fields_size = 0
        dex_bytes[str_data_off + 1] = 1; // instance_fields_size = 1
        dex_bytes[str_data_off + 2] = 1; // direct_methods_size = 1
        dex_bytes[str_data_off + 3] = 0; // virtual_methods_size = 0
        str_data_off += 4;

        // instance field: diff=0, access=1
        dex_bytes[str_data_off] = 0;
        dex_bytes[str_data_off + 1] = 1;
        str_data_off += 2;

        // direct method: diff=0, access=1, code_off=code_item_off
        dex_bytes[str_data_off] = 0;
        dex_bytes[str_data_off + 1] = 1;
        // encode code_item_off in uleb128
        let mut val = code_item_off;
        while val >= 0x80 {
            dex_bytes[str_data_off + 2] = ((val & 0x7F) | 0x80) as u8;
            val >>= 7;
            str_data_off += 1;
        }
        dex_bytes[str_data_off + 2] = (val & 0x7F) as u8;
        str_data_off += 3;

        // Class Defs: 1 class @ str_data_off
        if str_data_off % 4 != 0 {
            str_data_off += 4 - (str_data_off % 4);
        }
        let class_defs_off = str_data_off as u32;
        dex_bytes[0x60..0x64].copy_from_slice(&1u32.to_le_bytes());
        dex_bytes[0x64..0x68].copy_from_slice(&class_defs_off.to_le_bytes());

        let c_pos = class_defs_off as usize;
        dex_bytes[c_pos..c_pos + 4].copy_from_slice(&0u32.to_le_bytes()); // class_idx 0 (TestClass)
        dex_bytes[c_pos + 4..c_pos + 8].copy_from_slice(&1u32.to_le_bytes()); // access_flags 1
        dex_bytes[c_pos + 8..c_pos + 12].copy_from_slice(&1u32.to_le_bytes()); // superclass 1 (Object)
        dex_bytes[c_pos + 12..c_pos + 16].copy_from_slice(&iface_list_off.to_le_bytes()); // interfaces_off
        dex_bytes[c_pos + 16..c_pos + 20].copy_from_slice(&3u32.to_le_bytes()); // source_file 3 (TestClass.java)
        dex_bytes[c_pos + 20..c_pos + 24].copy_from_slice(&0u32.to_le_bytes()); // annotations
        dex_bytes[c_pos + 24..c_pos + 28].copy_from_slice(&class_data_off.to_le_bytes()); // class_data_off
        dex_bytes[c_pos + 28..c_pos + 32].copy_from_slice(&0u32.to_le_bytes()); // static_values

        // 1. Test DexFile parse
        let dex = DexFile::parse(&dex_bytes).expect("Failed to parse synthetic DEX");
        assert_eq!(dex.class_def_count(), 1);
        assert_eq!(dex.string_count(), 7);

        // 2. Test get_class_def_full
        let cls = dex.get_class_def_full(0).expect("Failed to get class def");
        assert_eq!(cls.descriptor, "Lcom/example/TestClass;");
        assert_eq!(cls.superclass, Some("Ljava/lang/Object;"));
        assert_eq!(cls.source_file, Some("TestClass.java"));
        assert_eq!(cls.interfaces, vec!["Ljava/lang/Runnable;"]);
        assert_eq!(cls.instance_fields.len(), 1);
        assert_eq!(cls.instance_fields[0].name, "myField");
        assert_eq!(cls.direct_methods.len(), 1);
        let m = &cls.direct_methods[0];
        assert_eq!(m.name, "testMethod");
        assert!(m.code.is_some());
        let code = m.code.unwrap();
        assert_eq!(code.registers_size, 4);
        assert_eq!(code.ins_size, 1);
        assert_eq!(code.outs_size, 2);
        assert_eq!(code.insns_count, 3);

        // 3. Test pack_dex_classes to D2CN packed buffer
        let packed = dex.pack_dex_classes();
        assert!(packed.len() >= 32);

        // Verify D2CN Header
        let magic = u32::from_le_bytes([packed[0], packed[1], packed[2], packed[3]]);
        assert_eq!(magic, 0x4E433244); // 'D','2','C','N'
        let version = u16::from_le_bytes([packed[4], packed[5]]);
        assert_eq!(version, 1);
        let flags = u16::from_le_bytes([packed[6], packed[7]]);
        assert_eq!(flags, 0);
        let header_size = u32::from_le_bytes([packed[8], packed[9], packed[10], packed[11]]);
        assert_eq!(header_size, 32);
        let string_count = u32::from_le_bytes([packed[12], packed[13], packed[14], packed[15]]);
        assert!(string_count >= 6);
        let class_count = u32::from_le_bytes([packed[16], packed[17], packed[18], packed[19]]);
        assert_eq!(class_count, 1);
        let method_count = u32::from_le_bytes([packed[20], packed[21], packed[22], packed[23]]);
        assert_eq!(method_count, 1);
        let string_pool_off = u32::from_le_bytes([packed[24], packed[25], packed[26], packed[27]]);
        assert_eq!(string_pool_off, 32);
        let classes_off = u32::from_le_bytes([packed[28], packed[29], packed[30], packed[31]]);
        assert!(classes_off > string_pool_off);
    }
}
