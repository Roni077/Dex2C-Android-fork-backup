//! C++ transpiler engine crate root for dex2c-transpiler.

pub mod emitter;
pub mod jni_registration;
pub mod runtime_header;

pub use emitter::{mangle_c_function_name, CppEmitter};
pub use jni_registration::{generate_jni_onload, NativeMethodReg};
pub use runtime_header::generate_runtime_header;
