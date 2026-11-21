//! Android.mk and Application.mk build generators for NDK and OLLVM.

use dex2c_core::TranspilerConfig;
use std::fmt::Write;

pub fn generate_android_mk(
    config: &TranspilerConfig,
    source_files: &[String],
) -> String {
    let mut out = String::with_capacity(1024);

    let _ = writeln!(out, "LOCAL_PATH := $(call my-dir)\n");
    let _ = writeln!(out, "include $(CLEAR_VARS)\n");
    let _ = writeln!(out, "LOCAL_MODULE := {}", config.module_name);
    let _ = writeln!(out, "LOCAL_SRC_FILES := {}", source_files.join(" \\\n    "));

    let mut cpp_flags = vec!["-std=c++17", "-fvisibility=hidden", "-O3"];
    if config.ollvm.enabled {
        if config.ollvm.fla { cpp_flags.push("-mllvm -fla"); }
        if config.ollvm.bcf { cpp_flags.push("-mllvm -bcf"); }
        if config.ollvm.sub { cpp_flags.push("-mllvm -sub"); }
        if config.ollvm.split { cpp_flags.push("-mllvm -split"); }
    }
    let _ = writeln!(out, "LOCAL_CPPFLAGS := {}", cpp_flags.join(" "));

    let mut ld_flags = vec!["-Wl,--gc-sections"];
    if config.ollvm.align_16kb {
        ld_flags.push("-Wl,-z,max-page-size=16384");
    }
    let _ = writeln!(out, "LOCAL_LDFLAGS := {}", ld_flags.join(" "));
    let _ = writeln!(out, "LOCAL_LDLIBS := -llog\n");
    let _ = writeln!(out, "include $(BUILD_SHARED_LIBRARY)");

    out
}

pub fn generate_application_mk(abis: &[&str]) -> String {
    let mut out = String::with_capacity(256);
    let _ = writeln!(out, "APP_ABI := {}", abis.join(" "));
    let _ = writeln!(out, "APP_PLATFORM := android-24");
    let _ = writeln!(out, "APP_STL := c++_static");
    let _ = writeln!(out, "APP_OPTIM := release");
    out
}
