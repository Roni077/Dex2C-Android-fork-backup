//! Shared domain models, configurations, and method references.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum JniBindingStyle {
    #[serde(rename = "REGISTER_NATIVES")]
    RegisterNatives,
    #[serde(rename = "STATIC_SYMBOLS")]
    StaticSymbols,
}

impl Default for JniBindingStyle {
    fn default() -> Self {
        Self::RegisterNatives
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OllvmConfig {
    pub enabled: bool,
    pub bcf: bool,
    pub fla: bool,
    pub sub: bool,
    pub split: bool,
    pub align_16kb: bool,
}

impl Default for OllvmConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            bcf: false,
            fla: false,
            sub: false,
            split: false,
            align_16kb: true,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TranspilerConfig {
    pub module_name: String,
    pub jni_style: JniBindingStyle,
    pub allow_init: bool,
    pub skip_synthetic: bool,
    pub methods_per_chunk: usize,
    pub custom_loader: String,
    pub ollvm: OllvmConfig,
}

impl Default for TranspilerConfig {
    fn default() -> Self {
        Self {
            module_name: "dex2c_native".to_string(),
            jni_style: JniBindingStyle::RegisterNatives,
            allow_init: false,
            skip_synthetic: true,
            methods_per_chunk: 250,
            custom_loader: "amimo.dcc.DccApplication".to_string(),
            ollvm: OllvmConfig::default(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct MethodRefKey {
    pub class_descriptor: String,
    pub method_name: String,
    pub signature: String,
}
