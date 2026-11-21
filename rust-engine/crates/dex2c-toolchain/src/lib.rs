//! Build toolchain integration crate root for dex2c-toolchain.

pub mod cmake;
pub mod ndk_make;

pub use cmake::generate_cmakelists;
pub use ndk_make::{generate_android_mk, generate_application_mk};
