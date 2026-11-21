//! Control Flow Graph (CFG) construction and Basic Block analysis for Dalvik bytecode.

use crate::instruction::{DalvikInstruction, OpcodeGroup};
use std::collections::{BTreeSet, HashMap};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EdgeKind {
    Fallthrough,
    Branch,
    SwitchCase,
    Catch,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CfgEdge {
    pub from_block: usize,
    pub to_block: usize,
    pub kind: EdgeKind,
}

#[derive(Debug, Clone, PartialEq)]
pub struct BasicBlock {
    pub id: usize,
    pub start_offset: u32,
    pub end_offset: u32,
    pub instruction_indices: Vec<usize>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MethodCfg {
    pub blocks: Vec<BasicBlock>,
    pub edges: Vec<CfgEdge>,
}

impl MethodCfg {
    pub fn build(instructions: &[DalvikInstruction]) -> Self {
        if instructions.is_empty() {
            return Self {
                blocks: Vec::new(),
                edges: Vec::new(),
            };
        }

        // 1. Identify leader instructions (entry, targets of jumps, instruction after jumps)
        let mut leaders = BTreeSet::new();
        leaders.insert(0u32); // Entry point is always a leader

        for (i, insn) in instructions.iter().enumerate() {
            match insn.group {
                OpcodeGroup::Goto | OpcodeGroup::IfTest | OpcodeGroup::IfTestZ => {
                    if let Some(target) = insn.target_offset {
                        let target_pc = (insn.offset as i32 + target) as u32;
                        leaders.insert(target_pc);
                    }
                    if i + 1 < instructions.len() {
                        leaders.insert(instructions[i + 1].offset);
                    }
                }
                OpcodeGroup::Return | OpcodeGroup::Throw => {
                    if i + 1 < instructions.len() {
                        leaders.insert(instructions[i + 1].offset);
                    }
                }
                _ => {}
            }
        }

        // 2. Partition into BasicBlocks
        let mut blocks = Vec::new();
        let mut current_block_indices = Vec::new();
        let mut block_start = 0u32;
        let mut block_id = 0usize;
        let mut offset_to_block_id = HashMap::new();

        for (i, insn) in instructions.iter().enumerate() {
            if leaders.contains(&insn.offset) && !current_block_indices.is_empty() {
                let end_offset = instructions[i - 1].offset;
                offset_to_block_id.insert(block_start, block_id);
                blocks.push(BasicBlock {
                    id: block_id,
                    start_offset: block_start,
                    end_offset,
                    instruction_indices: current_block_indices,
                });
                block_id += 1;
                current_block_indices = Vec::new();
                block_start = insn.offset;
            }

            if current_block_indices.is_empty() {
                block_start = insn.offset;
            }

            current_block_indices.push(i);
        }

        if !current_block_indices.is_empty() {
            let end_offset = instructions.last().unwrap().offset;
            offset_to_block_id.insert(block_start, block_id);
            blocks.push(BasicBlock {
                id: block_id,
                start_offset: block_start,
                end_offset,
                instruction_indices: current_block_indices,
            });
        }

        // 3. Connect CFG edges between blocks
        let mut edges = Vec::new();

        for block in &blocks {
            let last_idx = *block.instruction_indices.last().unwrap();
            let last_insn = &instructions[last_idx];

            match last_insn.group {
                OpcodeGroup::Goto => {
                    if let Some(target) = last_insn.target_offset {
                        let target_pc = (last_insn.offset as i32 + target) as u32;
                        if let Some(&to_block) = offset_to_block_id.get(&target_pc) {
                            edges.push(CfgEdge {
                                from_block: block.id,
                                to_block,
                                kind: EdgeKind::Branch,
                            });
                        }
                    }
                }
                OpcodeGroup::IfTest | OpcodeGroup::IfTestZ => {
                    // Branch edge
                    if let Some(target) = last_insn.target_offset {
                        let target_pc = (last_insn.offset as i32 + target) as u32;
                        if let Some(&to_block) = offset_to_block_id.get(&target_pc) {
                            edges.push(CfgEdge {
                                from_block: block.id,
                                to_block,
                                kind: EdgeKind::Branch,
                            });
                        }
                    }
                    // Fallthrough edge
                    if block.id + 1 < blocks.len() {
                        edges.push(CfgEdge {
                            from_block: block.id,
                            to_block: block.id + 1,
                            kind: EdgeKind::Fallthrough,
                        });
                    }
                }
                OpcodeGroup::Return | OpcodeGroup::Throw => {
                    // Terminators have no fallthrough
                }
                _ => {
                    // Normal fallthrough
                    if block.id + 1 < blocks.len() {
                        edges.push(CfgEdge {
                            from_block: block.id,
                            to_block: block.id + 1,
                            kind: EdgeKind::Fallthrough,
                        });
                    }
                }
            }
        }

        Self { blocks, edges }
    }
}
