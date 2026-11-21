//! CMakeLists.txt build file generator.

use dex2c_core::TranspilerConfig;
use std::fmt::Write;

pub fn generate_cmakelists(
    config: &TranspilerConfig,
    source_files: &[String],
) -> String {
    let mut out = String::with_capacity(1024);

    let _ = writeln!(out, "cmake_minimum_required(VERSION 3.22.1)\n");
    let _ = writeln!(out, "project(\"{}\" LANGUAGES C CXX)\n", config.module_name);

    let _ = writeln!(out, "set(CMAKE_CXX_STANDARD 17)");
    let _ = writeln!(out, "set(CMAKE_CXX_STANDARD_REQUIRED ON)\n");

    // Android 15 16KB page alignment flag
    if config.ollvm.align_16kb {
        let _ = writeln!(out, "# Android 15 16 KB ELF Page Alignment");
        let _ = writeln!(out, "set(CMAKE_SHARED_LINKER_FLAGS \"${{CMAKE_SHARED_LINKER_FLAGS}} -Wl,-z,max-page-size=16384\")\n");
    }

    let _ = writeln!(out, "add_library({} SHARED", config.module_name);
    for src in source_files {
        let _ = writeln!(out, "    {}", src);
    }
    let _ = writeln!(out, ")\n");

    let _ = writeln!(out, "find_library(log-lib log)");
    let _ = writeln!(out, "target_link_libraries({} ${{log-lib}})", config.module_name);

    out
}
