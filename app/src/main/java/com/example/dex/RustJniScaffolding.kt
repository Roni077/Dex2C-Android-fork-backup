package com.example.dex

data class RustScaffoldingFile(
  val fileName: String,
  val relativePath: String,
  val description: String,
  val content: String,
)

object RustJniScaffolding {

  fun generateBridgeFiles(): List<RustScaffoldingFile> =
    listOf(
      RustScaffoldingFile(
        fileName = "Cargo.toml",
        relativePath = "rust_jni/Cargo.toml",
        description = "Cargo manifest pulling androguard/dex-parser (dexparser-rs) & jni-rs",
        content =
          """
          |[package]
          |name = "dexparser_jni"
          |version = "0.1.0"
          |edition = "2021"
          |
          |[lib]
          |name = "dexparser_jni"
          |crate-type = ["cdylib"]
          |
          |[dependencies]
          |dexparser-rs = { git = "https://github.com/androguard/dex-parser.git" }
          |jni = "0.21"
          |serde = { version = "1.0", features = ["derive"] }
          |serde_json = "1.0"
          """.trimMargin() + "\n",
      ),
      RustScaffoldingFile(
        fileName = "lib.rs",
        relativePath = "rust_jni/src/lib.rs",
        description = "Rust JNI entry points wired to NativeDexParserBridge",
        content =
          """
          |use dexparser_rs::{DexFile, DexHelper};
          |use jni::objects::{JByteArray, JClass};
          |use jni::sys::jstring;
          |use jni::JNIEnv;
          |
          |#[no_mangle]
          |pub extern "system" fn Java_com_example_dex_NativeDexParserBridge_nativeInspectHeaderJson(
          |    env: JNIEnv,
          |    _class: JClass,
          |    dex_bytes: JByteArray,
          |) -> jstring {
          |    let raw = match env.convert_byte_array(dex_bytes) {
          |        Ok(b) => b,
          |        Err(e) => return make_error_jstring(&env, &format!("JNI array error: {e}")),
          |    };
          |
          |    let json = match DexFile::from_bytes(&raw) {
          |        Ok(dex) => {
          |            let helper = DexHelper::new(&dex);
          |            format!(
          |                "{{\"engine\":\"dexparser-rs\",\"classes\":{},\"methods\":{},\"fields\":{}}}",
          |                helper.classes().len(),
          |                helper.methods().len(),
          |                helper.fields().len()
          |            )
          |        }
          |        Err(e) => format!("{{\"error\":\"{e:?}\"}}"),
          |    };
          |
          |    env.new_string(json)
          |        .map(|s| s.into_raw())
          |        .unwrap_or(std::ptr::null_mut())
          |}
          |
          |#[no_mangle]
          |pub extern "system" fn Java_com_example_dex_NativeDexParserBridge_nativeParseMapListJson(
          |    env: JNIEnv,
          |    _class: JClass,
          |    dex_bytes: JByteArray,
          |) -> jstring {
          |    let raw = match env.convert_byte_array(dex_bytes) {
          |        Ok(b) => b,
          |        Err(e) => return make_error_jstring(&env, &format!("JNI array error: {e}")),
          |    };
          |
          |    let json = match DexFile::from_bytes(&raw) {
          |        Ok(dex) => format!("{{\"map_items\":{}}}", dex.map_list().len()),
          |        Err(e) => format!("{{\"error\":\"{e:?}\"}}"),
          |    };
          |
          |    env.new_string(json)
          |        .map(|s| s.into_raw())
          |        .unwrap_or(std::ptr::null_mut())
          |}
          |
          |fn make_error_jstring(env: &JNIEnv, msg: &str) -> jstring {
          |    let escaped = msg.replace('"', "\\\"");
          |    env.new_string(format!("{{\"error\":\"{escaped}\"}}"))
          |        .map(|s| s.into_raw())
          |        .unwrap_or(std::ptr::null_mut())
          |}
          """.trimMargin() + "\n",
      ),
      RustScaffoldingFile(
        fileName = "build_jni.sh",
        relativePath = "rust_jni/build_jni.sh",
        description = "cargo-ndk cross-compilation commands for Android ABIs",
        content =
          """
          |#!/usr/bin/env bash
          |set -euo pipefail
          |
          |# Install Android Rust targets once:
          |rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
          |cargo install cargo-ndk
          |
          |# Build libdexparser_jni.so directly into app/src/main/jniLibs:
          |cargo ndk \
          |  -t arm64-v8a \
          |  -t armeabi-v7a \
          |  -t x86_64 \
          |  -o ../app/src/main/jniLibs \
          |  build --release
          """.trimMargin() + "\n",
      ),
    )
}
