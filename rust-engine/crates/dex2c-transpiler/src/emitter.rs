//! Dalvik-to-C++ instruction emitter.
//! Transpiles Dalvik bytecode instructions into optimized C++17 JNI method implementations.

use dex2c_core::{JniBindingStyle, TranspilerConfig};
use dex2c_dex::{DalvikDisassembler, DalvikInstruction, DexClassDefFull, DexFile, DexMethodMeta, OpcodeGroup};
use std::collections::HashSet;
use std::fmt::Write;

pub struct CppEmitter<'a> {
    pub dex: &'a DexFile<'a>,
    pub config: &'a TranspilerConfig,
}

impl<'a> CppEmitter<'a> {
    pub fn new(dex: &'a DexFile<'a>, config: &'a TranspilerConfig) -> Self {
        Self { dex, config }
    }

    pub fn transpile_method(
        &self,
        class_def: &DexClassDefFull,
        method: &DexMethodMeta,
    ) -> String {
        let mut out = String::with_capacity(2048);

        let c_func_name = mangle_c_function_name(class_def.descriptor, method.name);
        let ret_jni_type = descriptor_to_jni_type(method.return_type);

        // Function signature
        let is_static = (method.access_flags & 0x0008) != 0;
        let self_arg = if is_static { "jclass clazz" } else { "jobject thiz" };

        let _ = writeln!(
            out,
            "extern \"C\" JNIEXPORT {} JNICALL {}(JNIEnv* env, {}",
            ret_jni_type, c_func_name, self_arg
        );

        // Parameter list
        for (idx, &param_type) in method.param_types.iter().enumerate() {
            let jni_t = descriptor_to_jni_type(param_type);
            let _ = write!(out, ", {} p{}", jni_t, idx);
        }
        let _ = writeln!(out, ") {{");

        // Code body
        if let Some(ref code) = method.code {
            let _ = writeln!(out, "    d2c_reg regs[{}];", code.registers_size.max(1));
            let _ = writeln!(out, "    memset(regs, 0, sizeof(regs));");

            // Initialize parameter registers
            let ins_count = code.ins_size as usize;
            let reg_count = code.registers_size as usize;
            let param_start = if reg_count >= ins_count { reg_count - ins_count } else { 0 };

            let mut cur_reg = param_start;
            if !is_static {
                let _ = writeln!(out, "    regs[{}].l = thiz;", cur_reg);
                cur_reg += 1;
            }
            for (p_idx, &param_type) in method.param_types.iter().enumerate() {
                let field = jvalue_field_for_desc(param_type);
                let _ = writeln!(out, "    regs[{}].{} = p{};", cur_reg, field, p_idx);
                cur_reg += 1;
            }

            // Disassemble and emit instructions
            if let Some(units) = self.dex.get_insns_units(code) {
                let disasm = DalvikDisassembler::new(self.dex);
                let insns = disasm.disassemble_code(units);

                // Collect label branch targets
                let mut label_targets = HashSet::new();
                for insn in &insns {
                    if let Some(target) = insn.target_offset {
                        let target_pc = (insn.offset as i32 + target) as u32;
                        label_targets.insert(target_pc);
                    }
                }

                for insn in &insns {
                    if label_targets.contains(&insn.offset) {
                        let _ = writeln!(out, "L_{:04x}:", insn.offset);
                    }
                    self.emit_instruction(&mut out, insn, method.return_type);
                }
            }
        } else {
            // Native or abstract fallback return
            let _ = writeln!(out, "    {}", default_return_stmt(method.return_type));
        }

        let _ = writeln!(out, "}}\n");
        out
    }

    fn emit_instruction(&self, out: &mut String, insn: &DalvikInstruction, return_type: &str) {
        match insn.group {
            OpcodeGroup::Nop => {
                let _ = writeln!(out, "    /* nop */");
            }
            OpcodeGroup::Move => {
                if insn.registers.len() >= 2 {
                    let _ = writeln!(out, "    regs[{}] = regs[{}];", insn.registers[0], insn.registers[1]);
                }
            }
            OpcodeGroup::Const => {
                if let Some(lit) = insn.literal {
                    if !insn.registers.is_empty() {
                        let _ = writeln!(out, "    regs[{}].i = (jint){}L;", insn.registers[0], lit);
                    }
                } else if let Some(idx) = insn.index {
                    if !insn.registers.is_empty() {
                        if insn.mnemonic == "const-string" || insn.mnemonic == "const-string/jumbo" {
                            let s = self.dex.get_string(idx).unwrap_or("");
                            let _ = writeln!(
                                out,
                                "    regs[{}].l = env->NewStringUTF(\"{}\");",
                                insn.registers[0], escape_c_string(s)
                            );
                        } else if insn.mnemonic == "const-class" {
                            let type_desc = self.dex.get_type_descriptor(idx).unwrap_or("Ljava/lang/Object;");
                            let class_name = descriptor_to_class_name(type_desc);
                            let _ = writeln!(
                                out,
                                "    regs[{}].l = env->FindClass(\"{}\");",
                                insn.registers[0], class_name
                            );
                        }
                    }
                }
            }
            OpcodeGroup::Return => {
                if insn.mnemonic == "return-void" {
                    let _ = writeln!(out, "    return;");
                } else if !insn.registers.is_empty() {
                    let field = jvalue_field_for_desc(return_type);
                    let _ = writeln!(out, "    return regs[{}].{};", insn.registers[0], field);
                } else {
                    let _ = writeln!(out, "    return;");
                }
            }
            OpcodeGroup::Goto => {
                if let Some(target) = insn.target_offset {
                    let target_pc = (insn.offset as i32 + target) as u32;
                    let _ = writeln!(out, "    goto L_{:04x};", target_pc);
                }
            }
            OpcodeGroup::IfTest => {
                if insn.registers.len() >= 2 {
                    if let Some(target) = insn.target_offset {
                        let target_pc = (insn.offset as i32 + target) as u32;
                        let op_sym = match insn.opcode & 0xFF {
                            0x32 => "==",
                            0x33 => "!=",
                            0x34 => "<",
                            0x35 => ">=",
                            0x36 => ">",
                            0x37 => "<=",
                            _ => "==",
                        };
                        let _ = writeln!(
                            out,
                            "    if (regs[{}].i {} regs[{}].i) goto L_{:04x};",
                            insn.registers[0], op_sym, insn.registers[1], target_pc
                        );
                    }
                }
            }
            OpcodeGroup::IfTestZ => {
                if !insn.registers.is_empty() {
                    if let Some(target) = insn.target_offset {
                        let target_pc = (insn.offset as i32 + target) as u32;
                        let op_sym = match insn.opcode & 0xFF {
                            0x38 => "==",
                            0x39 => "!=",
                            0x3A => "<",
                            0x3B => ">=",
                            0x3C => ">",
                            0x3D => "<=",
                            _ => "==",
                        };
                        let _ = writeln!(
                            out,
                            "    if (regs[{}].i {} 0) goto L_{:04x};",
                            insn.registers[0], op_sym, target_pc
                        );
                    }
                }
            }
            OpcodeGroup::Binop => {
                if insn.registers.len() >= 3 {
                    let op_sym = match insn.opcode & 0xFF {
                        0x90 => "+",
                        0x91 => "-",
                        0x92 => "*",
                        0x93 => "/",
                        0x94 => "%",
                        0x95 => "&",
                        0x96 => "|",
                        0x97 => "^",
                        0x98 => "<<",
                        0x99 => ">>",
                        _ => "+",
                    };
                    let _ = writeln!(
                        out,
                        "    regs[{}].i = regs[{}].i {} regs[{}].i;",
                        insn.registers[0], insn.registers[1], op_sym, insn.registers[2]
                    );
                }
            }
            OpcodeGroup::InvokeVirtual | OpcodeGroup::InvokeStatic | OpcodeGroup::InvokeDirect => {
                let _ = writeln!(out, "    /* invoke #{:?} */", insn.index);
            }
            _ => {
                let _ = writeln!(out, "    /* opcode: {} */", insn.mnemonic);
            }
        }
    }
}

pub fn mangle_c_function_name(class_desc: &str, method_name: &str) -> String {
    let mut out = String::from("Java_");
    let clean = class_desc.strip_prefix('L').unwrap_or(class_desc);
    let clean = clean.strip_suffix(';').unwrap_or(clean);

    for c in clean.chars() {
        match c {
            '/' => out.push('_'),
            '_' => out.push_str("_1"),
            '$' => out.push_str("_00024"),
            other => out.push(other),
        }
    }
    out.push('_');
    for c in method_name.chars() {
        match c {
            '_' => out.push_str("_1"),
            other => out.push(other),
        }
    }
    out
}

pub fn descriptor_to_jni_type(desc: &str) -> &'static str {
    match desc {
        "V" => "void",
        "Z" => "jboolean",
        "B" => "jbyte",
        "C" => "jchar",
        "S" => "jshort",
        "I" => "jint",
        "J" => "jlong",
        "F" => "jfloat",
        "D" => "jdouble",
        s if s.starts_with('[') || s.starts_with('L') => "jobject",
        _ => "jobject",
    }
}

pub fn jvalue_field_for_desc(desc: &str) -> &'static str {
    match desc {
        "Z" => "i",
        "B" => "i",
        "C" => "i",
        "S" => "i",
        "I" => "i",
        "J" => "j",
        "F" => "f",
        "D" => "d",
        _ => "l",
    }
}

pub fn default_return_stmt(desc: &str) -> &'static str {
    match desc {
        "V" => "return;",
        "Z" | "B" | "C" | "S" | "I" => "return 0;",
        "J" => "return 0LL;",
        "F" => "return 0.0f;",
        "D" => "return 0.0;",
        _ => "return NULL;",
    }
}

fn descriptor_to_class_name(desc: &str) -> &str {
    let s = desc.strip_prefix('L').unwrap_or(desc);
    s.strip_suffix(';').unwrap_or(s)
}

fn escape_c_string(s: &str) -> String {
    s.replace('\\', "\\\\")
        .replace('"', "\\\"")
        .replace('\n', "\\n")
        .replace('\r', "\\r")
        .replace('\t', "\\t")
}
