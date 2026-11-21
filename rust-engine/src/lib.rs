//! JNI Bridge Exports for Dex2C Studio's Rust Native Backend Engine.
//! Provides panic-guarded, zero-copy native acceleration to the Kotlin Android layer.

pub mod dex;
pub mod filter;

use dex::DexFile;
use dex2c_core::TranspilerConfig;
use dex2c_dex::evaluate_dex_methods_and_classes;
use dex2c_smali::SmaliDisassembler;
use dex2c_toolchain::{generate_android_mk, generate_application_mk, generate_cmakelists};
use dex2c_transpiler::{generate_runtime_header, CppEmitter};
use filter::evaluate_dex_methods;
use jni::objects::{JByteArray, JClass, JIntArray, JObjectArray, JString};
use jni::sys::{jboolean, jint, jintArray, jlong, jobject, jsize, jstring, JNI_TRUE};
use jni::JNIEnv;
use std::collections::HashSet;
use std::panic::{catch_unwind, AssertUnwindSafe};

struct DexHolder {
    buffer: Vec<u8>,
    header: dex::DexHeader,
    cached_packed: Option<Vec<u8>>,
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeOpenDex(
    env: JNIEnv,
    _class: JClass,
    bytes_array: JByteArray,
    size: jsize,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if bytes_array.is_null() || size <= 0x70 {
            return 0i64;
        }

        let mut buffer = vec![0u8; size as usize];
        let slice_i8: &mut [i8] = std::slice::from_raw_parts_mut(buffer.as_mut_ptr() as *mut i8, buffer.len());
        if env.get_byte_array_region(&bytes_array, 0, slice_i8).is_err() {
            return 0i64;
        }

        let dex = match DexFile::parse(&buffer) {
            Ok(d) => d,
            Err(_) => return 0i64,
        };

        let holder = Box::new(DexHolder {
            header: dex.header,
            buffer,
            cached_packed: None,
        });

        Box::into_raw(holder) as jlong
    }));

    result.unwrap_or(0i64)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeCloseDex(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let _ = Box::from_raw(handle as *mut DexHolder);
    }));
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeGetClassCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    if handle == 0 {
        return 0;
    }
    let result = catch_unwind(AssertUnwindSafe(|| {
        let holder = &*(handle as *const DexHolder);
        holder.header.class_defs_size as jint
    }));
    result.unwrap_or(0)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeGetStringCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    if handle == 0 {
        return 0;
    }
    let result = catch_unwind(AssertUnwindSafe(|| {
        let holder = &*(handle as *const DexHolder);
        holder.header.string_ids_size as jint
    }));
    result.unwrap_or(0)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeParseDex(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jobject {
    if handle == 0 {
        return std::ptr::null_mut();
    }

    let result = catch_unwind(AssertUnwindSafe(|| {
        let holder = &mut *(handle as *mut DexHolder);
        if holder.cached_packed.is_none() {
            let dex = DexFile {
                data: &holder.buffer,
                header: holder.header,
            };
            holder.cached_packed = Some(dex.pack_dex_classes());
        }

        let packed = holder.cached_packed.as_mut().unwrap();
        let buf = match env.new_direct_byte_buffer(packed.as_mut_ptr(), packed.len()) {
            Ok(b) => b,
            Err(_) => return std::ptr::null_mut(),
        };

        buf.into_raw()
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeFilterMethods(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    rules_array: JObjectArray,
    allow_init: jboolean,
    skip_synthetic: jboolean,
) -> jintArray {
    if handle == 0 || rules_array.is_null() {
        return std::ptr::null_mut();
    }

    let result = catch_unwind(AssertUnwindSafe(|| {
        let holder = &*(handle as *const DexHolder);
        let dex = DexFile {
            data: &holder.buffer,
            header: holder.header,
        };

        let len = match env.get_array_length(&rules_array) {
            Ok(l) => l,
            Err(_) => return std::ptr::null_mut(),
        };

        let mut rule_strings = Vec::with_capacity(len as usize);

        for i in 0..len {
            if let Ok(obj) = env.get_object_array_element(&rules_array, i) {
                let jstr: JString = obj.into();
                if !jstr.is_null() {
                    if let Ok(java_str) = env.get_string(&jstr) {
                        rule_strings.push(java_str.to_string_lossy().into_owned());
                    }
                }
            }
        }

        let rust_rules: Vec<&str> = rule_strings.iter().map(|s| s.as_str()).collect();

        let matched = evaluate_dex_methods(
            &dex,
            &rust_rules,
            allow_init == JNI_TRUE,
            skip_synthetic == JNI_TRUE,
        );

        let out_array = match env.new_int_array(matched.len() as jsize) {
            Ok(arr) => arr,
            Err(_) => return std::ptr::null_mut(),
        };

        if env.set_int_array_region(&out_array, 0, &matched).is_err() {
            return std::ptr::null_mut();
        }

        out_array.into_raw()
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeFilterClassesAndMethods(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    rules_array: JObjectArray,
    allow_init: jboolean,
    skip_synthetic: jboolean,
) -> jintArray {
    if handle == 0 || rules_array.is_null() {
        return std::ptr::null_mut();
    }

    let result = catch_unwind(AssertUnwindSafe(|| {
        let holder = &*(handle as *const DexHolder);
        let dex = match dex2c_dex::DexFile::parse(&holder.buffer) {
            Ok(d) => d,
            Err(_) => return std::ptr::null_mut(),
        };

        let len = match env.get_array_length(&rules_array) {
            Ok(l) => l,
            Err(_) => return std::ptr::null_mut(),
        };

        let mut rule_strings = Vec::with_capacity(len as usize);

        for i in 0..len {
            if let Ok(obj) = env.get_object_array_element(&rules_array, i) {
                let jstr: JString = obj.into();
                if !jstr.is_null() {
                    if let Ok(java_str) = env.get_string(&jstr) {
                        rule_strings.push(java_str.to_string_lossy().into_owned());
                    }
                }
            }
        }

        let rust_rules: Vec<&str> = rule_strings.iter().map(|s| s.as_str()).collect();

        let matched = evaluate_dex_methods_and_classes(
            &dex,
            &rust_rules,
            allow_init == JNI_TRUE,
            skip_synthetic == JNI_TRUE,
        );

        let total_size = 2 + matched.matched_classes.len() + matched.matched_methods.len();
        let mut packed = Vec::with_capacity(total_size);
        packed.push(matched.matched_classes.len() as i32);
        packed.push(matched.matched_methods.len() as i32);
        packed.extend_from_slice(&matched.matched_classes);
        packed.extend_from_slice(&matched.matched_methods);

        let out_array = match env.new_int_array(packed.len() as jsize) {
            Ok(arr) => arr,
            Err(_) => return std::ptr::null_mut(),
        };

        if env.set_int_array_region(&out_array, 0, &packed).is_err() {
            return std::ptr::null_mut();
        }

        out_array.into_raw()
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeDisassembleClassSmali(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    class_def_idx: jint,
) -> jstring {
    if handle == 0 || class_def_idx < 0 {
        return std::ptr::null_mut();
    }

    let result = catch_unwind(AssertUnwindSafe(|| {
        let holder = &*(handle as *const DexHolder);
        let dex = match dex2c_dex::DexFile::parse(&holder.buffer) {
            Ok(d) => d,
            Err(_) => return std::ptr::null_mut(),
        };

        let cls = match dex.get_class_def_full(class_def_idx as u32) {
            Some(c) => c,
            None => return std::ptr::null_mut(),
        };

        let smali_text = SmaliDisassembler::disassemble_class(&dex, &cls);
        match env.new_string(smali_text) {
            Ok(j_str) => j_str.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeTranspileMethod(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    class_def_idx: jint,
    method_idx: jint,
) -> jstring {
    if handle == 0 || class_def_idx < 0 || method_idx < 0 {
        return std::ptr::null_mut();
    }

    let result = catch_unwind(AssertUnwindSafe(|| {
        let holder = &*(handle as *const DexHolder);
        let dex = match dex2c_dex::DexFile::parse(&holder.buffer) {
            Ok(d) => d,
            Err(_) => return std::ptr::null_mut(),
        };

        let cls = match dex.get_class_def_full(class_def_idx as u32) {
            Some(c) => c,
            None => return std::ptr::null_mut(),
        };

        let m = match cls
            .direct_methods
            .iter()
            .chain(cls.virtual_methods.iter())
            .find(|method| method.method_idx == method_idx as u32)
        {
            Some(method) => method,
            None => return std::ptr::null_mut(),
        };

        let config = TranspilerConfig::default();
        let emitter = CppEmitter::new(&dex, &config);
        let cpp_code = emitter.transpile_method(&cls, m);

        match env.new_string(cpp_code) {
            Ok(j_str) => j_str.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativePatchDexMethods(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    method_indices_array: JIntArray,
) -> jint {
    if handle == 0 || method_indices_array.is_null() {
        return -1;
    }

    let result = catch_unwind(AssertUnwindSafe(|| {
        let holder = &mut *(handle as *mut DexHolder);
        let len = match env.get_array_length(&method_indices_array) {
            Ok(l) => l as usize,
            Err(_) => return -1,
        };

        let mut method_ids = vec![0i32; len];
        if env.get_int_array_region(&method_indices_array, 0, &mut method_ids).is_err() {
            return -1;
        }

        let set: HashSet<u32> = method_ids.into_iter().map(|id| id as u32).collect();
        match dex2c_dex::patch_dex_methods_native(&mut holder.buffer, &set) {
            Ok(count) => count as jint,
            Err(_) => -1,
        }
    }));

    result.unwrap_or(-1)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeGenerateBuildFiles(
    mut env: JNIEnv,
    _class: JClass,
    module_name_jstr: JString,
    source_files_array: JObjectArray,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let module_name = if !module_name_jstr.is_null() {
            env.get_string(&module_name_jstr)
                .map(|s| s.to_string_lossy().into_owned())
                .unwrap_or_else(|_| "dex2c_native".into())
        } else {
            "dex2c_native".into()
        };

        let mut config = TranspilerConfig::default();
        config.module_name = module_name.clone();

        let len = match env.get_array_length(&source_files_array) {
            Ok(l) => l,
            Err(_) => 0,
        };

        let mut sources = Vec::new();
        for i in 0..len {
            if let Ok(obj) = env.get_object_array_element(&source_files_array, i) {
                let jstr: JString = obj.into();
                if !jstr.is_null() {
                    if let Ok(s) = env.get_string(&jstr) {
                        sources.push(s.to_string_lossy().into_owned());
                    }
                }
            }
        }

        let cmake = generate_cmakelists(&config, &sources);
        let android_mk = generate_android_mk(&config, &sources);
        let app_mk = generate_application_mk(&["arm64-v8a", "x86_64"]);
        let header = generate_runtime_header(&module_name);

        let json = serde_json::json!({
            "cmake": cmake,
            "android_mk": android_mk,
            "application_mk": app_mk,
            "runtime_header": header
        });

        let json_str = json.to_string();
        match env.new_string(json_str) {
            Ok(j_str) => j_str.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeGetVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let s = env
        .new_string("rust_dex2c 0.2.0 (SIMD RegexSet + Zero-Copy DEX + Pure Rust C++ Transpiler)")
        .ok();
    s.map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}
