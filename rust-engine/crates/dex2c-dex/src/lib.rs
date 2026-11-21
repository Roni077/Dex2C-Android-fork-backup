//! DEX and Dalvik processing crate root for dex2c-dex.

pub mod cfg;
pub mod disassembler;
pub mod filter;
pub mod instruction;
pub mod parser;
pub mod patcher;

pub use cfg::{BasicBlock, CfgEdge, EdgeKind, MethodCfg};
pub use disassembler::DalvikDisassembler;
pub use filter::{evaluate_dex_methods_and_classes, CompiledFilter, FilterMatchResult};
pub use instruction::{CatchHandler, DalvikInstruction, DalvikPayload, OpcodeGroup, TryCatchBlock};
pub use parser::{DexClassDefFull, DexCodeMeta, DexFieldMeta, DexFile, DexHeader, DexMethodMeta};
pub use patcher::{patch_dex_methods_native, resign_dex_header, ACC_NATIVE};
