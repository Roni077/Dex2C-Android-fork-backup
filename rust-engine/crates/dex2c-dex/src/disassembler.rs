//! Dalvik bytecode instruction decoder.
//! Disassembles 16-bit code units into structured DalvikInstruction instances.

use crate::instruction::{CatchHandler, DalvikInstruction, DalvikPayload, OpcodeGroup, TryCatchBlock};
use crate::parser::DexFile;
use dex2c_core::Result;

pub struct DalvikDisassembler<'a> {
    pub dex: &'a DexFile<'a>,
}

impl<'a> DalvikDisassembler<'a> {
    pub fn new(dex: &'a DexFile<'a>) -> Self {
        Self { dex }
    }

    pub fn disassemble_code(&self, code_units: &[u16]) -> Vec<DalvikInstruction> {
        let mut insns = Vec::new();
        let mut pc = 0usize;
        let len = code_units.len();

        while pc < len {
            let unit = code_units[pc];
            let opcode_byte = (unit & 0xFF) as u8;
            let (insn, advance) = self.decode_one(code_units, pc, opcode_byte);
            insns.push(insn);
            pc += advance.max(1);
        }

        insns
    }

    fn decode_one(&self, units: &[u16], pc: usize, op: u8) -> (DalvikInstruction, usize) {
        let unit = units[pc];
        let offset = pc as u32;

        match op {
            0x00 => {
                // nop
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "nop",
                    group: OpcodeGroup::Nop,
                    length_units: 1,
                    registers: vec![],
                    literal: None,
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 1)
            }
            0x01..=0x09 => {
                // move instructions: move, move/from16, move/16, move-wide, etc.
                let reg_a = ((unit >> 8) & 0x0F) as u16;
                let reg_b = ((unit >> 12) & 0x0F) as u16;
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "move",
                    group: OpcodeGroup::Move,
                    length_units: 1,
                    registers: vec![reg_a, reg_b],
                    literal: None,
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 1)
            }
            0x0E => {
                // return-void
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "return-void",
                    group: OpcodeGroup::Return,
                    length_units: 1,
                    registers: vec![],
                    literal: None,
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 1)
            }
            0x0F..=0x11 => {
                // return, return-wide, return-object
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "return",
                    group: OpcodeGroup::Return,
                    length_units: 1,
                    registers: vec![reg_a],
                    literal: None,
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 1)
            }
            0x12 => {
                // const/4 vA, #+B
                let reg_a = ((unit >> 8) & 0x0F) as u16;
                let mut lit = ((unit >> 12) & 0x0F) as i8;
                if lit > 7 { lit -= 16; } // sign extend
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "const/4",
                    group: OpcodeGroup::Const,
                    length_units: 1,
                    registers: vec![reg_a],
                    literal: Some(lit as i64),
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 1)
            }
            0x13 => {
                // const/16 vA, #+BBBB
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let lit = if pc + 1 < units.len() { units[pc + 1] as i16 } else { 0 };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "const/16",
                    group: OpcodeGroup::Const,
                    length_units: 2,
                    registers: vec![reg_a],
                    literal: Some(lit as i64),
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 2)
            }
            0x14 => {
                // const vAA, #+BBBBBBBB
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let lit = if pc + 2 < units.len() {
                    let lo = units[pc + 1] as u32;
                    let hi = units[pc + 2] as u32;
                    (lo | (hi << 16)) as i32
                } else {
                    0
                };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "const",
                    group: OpcodeGroup::Const,
                    length_units: 3,
                    registers: vec![reg_a],
                    literal: Some(lit as i64),
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 3)
            }
            0x1A => {
                // const-string vAA, string@BBBB
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let str_idx = if pc + 1 < units.len() { units[pc + 1] as u32 } else { 0 };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "const-string",
                    group: OpcodeGroup::Const,
                    length_units: 2,
                    registers: vec![reg_a],
                    literal: None,
                    target_offset: None,
                    index: Some(str_idx),
                    payload: None,
                }, 2)
            }
            0x1B => {
                // const-string/jumbo vAA, string@BBBBBBBB
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let str_idx = if pc + 2 < units.len() {
                    let lo = units[pc + 1] as u32;
                    let hi = units[pc + 2] as u32;
                    lo | (hi << 16)
                } else {
                    0
                };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "const-string/jumbo",
                    group: OpcodeGroup::Const,
                    length_units: 3,
                    registers: vec![reg_a],
                    literal: None,
                    target_offset: None,
                    index: Some(str_idx),
                    payload: None,
                }, 3)
            }
            0x1C => {
                // const-class vAA, type@BBBB
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let type_idx = if pc + 1 < units.len() { units[pc + 1] as u32 } else { 0 };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "const-class",
                    group: OpcodeGroup::Const,
                    length_units: 2,
                    registers: vec![reg_a],
                    literal: None,
                    target_offset: None,
                    index: Some(type_idx),
                    payload: None,
                }, 2)
            }
            0x28 => {
                // goto +AA
                let branch_offset = ((unit >> 8) as i8) as i32;
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "goto",
                    group: OpcodeGroup::Goto,
                    length_units: 1,
                    registers: vec![],
                    literal: None,
                    target_offset: Some(branch_offset),
                    index: None,
                    payload: None,
                }, 1)
            }
            0x29 => {
                // goto/16 +AAAA
                let branch_offset = if pc + 1 < units.len() { units[pc + 1] as i16 as i32 } else { 0 };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "goto/16",
                    group: OpcodeGroup::Goto,
                    length_units: 2,
                    registers: vec![],
                    literal: None,
                    target_offset: Some(branch_offset),
                    index: None,
                    payload: None,
                }, 2)
            }
            0x32..=0x37 => {
                // if-test vA, vB, +CCCC (eq, ne, lt, ge, gt, le)
                let reg_a = ((unit >> 8) & 0x0F) as u16;
                let reg_b = ((unit >> 12) & 0x0F) as u16;
                let branch_offset = if pc + 1 < units.len() { units[pc + 1] as i16 as i32 } else { 0 };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "if-test",
                    group: OpcodeGroup::IfTest,
                    length_units: 2,
                    registers: vec![reg_a, reg_b],
                    literal: None,
                    target_offset: Some(branch_offset),
                    index: None,
                    payload: None,
                }, 2)
            }
            0x38..=0x3D => {
                // if-testz vAA, +BBBB (eqz, nez, ltz, gez, gtz, lez)
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let branch_offset = if pc + 1 < units.len() { units[pc + 1] as i16 as i32 } else { 0 };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "if-testz",
                    group: OpcodeGroup::IfTestZ,
                    length_units: 2,
                    registers: vec![reg_a],
                    literal: None,
                    target_offset: Some(branch_offset),
                    index: None,
                    payload: None,
                }, 2)
            }
            0x44..=0x51 => {
                // aget / aput (aget, aget-wide, aget-object, aget-boolean, etc.)
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let (reg_b, reg_c) = if pc + 1 < units.len() {
                    let next = units[pc + 1];
                    ((next & 0xFF) as u16, ((next >> 8) & 0xFF) as u16)
                } else {
                    (0, 0)
                };
                let is_put = op >= 0x4B;
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: if is_put { "aput" } else { "aget" },
                    group: if is_put { OpcodeGroup::ArrayPut } else { OpcodeGroup::ArrayGet },
                    length_units: 2,
                    registers: vec![reg_a, reg_b, reg_c],
                    literal: None,
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 2)
            }
            0x52..=0x5F => {
                // iget / iput
                let reg_a = ((unit >> 8) & 0x0F) as u16;
                let reg_b = ((unit >> 12) & 0x0F) as u16;
                let field_idx = if pc + 1 < units.len() { units[pc + 1] as u32 } else { 0 };
                let is_put = op >= 0x59;
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: if is_put { "iput" } else { "iget" },
                    group: if is_put { OpcodeGroup::InstanceFieldPut } else { OpcodeGroup::InstanceFieldGet },
                    length_units: 2,
                    registers: vec![reg_a, reg_b],
                    literal: None,
                    target_offset: None,
                    index: Some(field_idx),
                    payload: None,
                }, 2)
            }
            0x60..=0x6D => {
                // sget / sput
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let field_idx = if pc + 1 < units.len() { units[pc + 1] as u32 } else { 0 };
                let is_put = op >= 0x67;
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: if is_put { "sput" } else { "sget" },
                    group: if is_put { OpcodeGroup::StaticFieldPut } else { OpcodeGroup::StaticFieldGet },
                    length_units: 2,
                    registers: vec![reg_a],
                    literal: None,
                    target_offset: None,
                    index: Some(field_idx),
                    payload: None,
                }, 2)
            }
            0x6E..=0x72 => {
                // invoke-kind {vC, vD, vE, vF, vG}, meth@BBBB
                let count = ((unit >> 12) & 0x0F) as usize;
                let meth_idx = if pc + 1 < units.len() { units[pc + 1] as u32 } else { 0 };
                let mut regs = Vec::with_capacity(count);
                if pc + 2 < units.len() {
                    let w2 = units[pc + 2];
                    let reg_c = (w2 & 0x0F) as u16;
                    let reg_d = ((w2 >> 4) & 0x0F) as u16;
                    let reg_e = ((w2 >> 8) & 0x0F) as u16;
                    let reg_f = ((w2 >> 12) & 0x0F) as u16;
                    let reg_g = ((unit >> 8) & 0x0F) as u16;
                    let all = [reg_c, reg_d, reg_e, reg_f, reg_g];
                    for i in 0..count.min(5) {
                        regs.push(all[i]);
                    }
                }
                let group = match op {
                    0x6E => OpcodeGroup::InvokeVirtual,
                    0x6F => OpcodeGroup::InvokeSuper,
                    0x70 => OpcodeGroup::InvokeDirect,
                    0x71 => OpcodeGroup::InvokeStatic,
                    0x72 => OpcodeGroup::InvokeInterface,
                    _ => OpcodeGroup::InvokeVirtual,
                };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "invoke",
                    group,
                    length_units: 3,
                    registers: regs,
                    literal: None,
                    target_offset: None,
                    index: Some(meth_idx),
                    payload: None,
                }, 3)
            }
            0x74..=0x78 => {
                // invoke-kind/range {vCCCC .. vNNNN}, meth@BBBB
                let count = ((unit >> 8) & 0xFF) as usize;
                let meth_idx = if pc + 1 < units.len() { units[pc + 1] as u32 } else { 0 };
                let start_reg = if pc + 2 < units.len() { units[pc + 2] } else { 0 };
                let mut regs = Vec::with_capacity(count);
                for i in 0..count {
                    regs.push(start_reg + i as u16);
                }
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "invoke/range",
                    group: OpcodeGroup::InvokeVirtualRange,
                    length_units: 3,
                    registers: regs,
                    literal: None,
                    target_offset: None,
                    index: Some(meth_idx),
                    payload: None,
                }, 3)
            }
            0x90..=0xAF => {
                // binop vAA, vBB, vCC (add, sub, mul, div, rem, and, or, xor, shl, shr, ushr)
                let reg_a = ((unit >> 8) & 0xFF) as u16;
                let (reg_b, reg_c) = if pc + 1 < units.len() {
                    let next = units[pc + 1];
                    ((next & 0xFF) as u16, ((next >> 8) & 0xFF) as u16)
                } else {
                    (0, 0)
                };
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "binop",
                    group: OpcodeGroup::Binop,
                    length_units: 2,
                    registers: vec![reg_a, reg_b, reg_c],
                    literal: None,
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 2)
            }
            _ => {
                // Fallback 1-unit unknown/unsupported
                (DalvikInstruction {
                    offset,
                    opcode: unit,
                    mnemonic: "unknown",
                    group: OpcodeGroup::Unknown,
                    length_units: 1,
                    registers: vec![],
                    literal: None,
                    target_offset: None,
                    index: None,
                    payload: None,
                }, 1)
            }
        }
    }
}
