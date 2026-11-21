//! Structured error definitions for the Dex2C native engine.

use thiserror::Error;

pub type Result<T> = std::result::Result<T, Dex2cError>;

#[derive(Error, Debug)]
pub enum Dex2cError {
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    #[error("Serialization error: {0}")]
    Serialization(#[from] serde_json::Error),

    #[error("DEX binary format error: {0}")]
    DexFormat(String),

    #[error("DEX index out of bounds: {target} index {index} >= size {size}")]
    IndexOutOfBounds {
        target: &'static str,
        index: u32,
        size: u32,
    },

    #[error("Dalvik instruction decoding error at offset {offset:#06x}: {message}")]
    InstructionDecode { offset: u32, message: String },

    #[error("Control flow graph construction error: {0}")]
    CfgError(String),

    #[error("C++ transpilation error for {method}: {message}")]
    TranspileError { method: String, message: String },

    #[error("DEX patching error: {0}")]
    PatchError(String),

    #[error("Smali compilation/disassembly error: {0}")]
    SmaliError(String),

    #[error("Toolchain or build error: {0}")]
    ToolchainError(String),

    #[error("Task was cancelled by user")]
    Cancelled,

    #[error("Invalid native handle {0:#x}")]
    InvalidHandle(i64),

    #[error("Native panic caught: {0}")]
    Panic(String),
}

impl Dex2cError {
    pub fn error_code(&self) -> i32 {
        match self {
            Dex2cError::Io(_) => -1,
            Dex2cError::Serialization(_) => -2,
            Dex2cError::DexFormat(_) => -3,
            Dex2cError::IndexOutOfBounds { .. } => -4,
            Dex2cError::InstructionDecode { .. } => -5,
            Dex2cError::CfgError(_) => -6,
            Dex2cError::TranspileError { .. } => -7,
            Dex2cError::PatchError(_) => -8,
            Dex2cError::SmaliError(_) => -9,
            Dex2cError::ToolchainError(_) => -10,
            Dex2cError::Cancelled => -11,
            Dex2cError::InvalidHandle(_) => -12,
            Dex2cError::Panic(_) => -99,
        }
    }
}
