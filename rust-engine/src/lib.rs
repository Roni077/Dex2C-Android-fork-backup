//! JNI Bridge Exports for Dex2C Studio's Rust Native Backend Engine.
//! Provides panic-guarded, zero-copy native acceleration to the Kotlin Android layer.

pub mod dex;
pub mod filter;

use dex::DexFile;
use filter::evaluate_dex_methods;
use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jboolean, jint, jintArray, jlong, jobject, jsize, jstring, JNI_TRUE};
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};

struct DexHolder {
    _buffer: Vec<u8>,
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

        // Parse header and validate DEX binary
        let dex = match DexFile::parse(&buffer) {
            Ok(d) => d,
            Err(_) => return 0i64,
        };

        let holder = Box::new(DexHolder {
            header: dex.header,
            _buffer: buffer,
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
                data: &holder._buffer,
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
            data: &holder._buffer,
            header: holder.header,
        };

        // Extract Java string rules into owned Rust String vector
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
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeGetVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let s = env.new_string("rust_dex2c 0.1.0 (SIMD RegexSet + Zero-Copy DEX)").ok();
    s.map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}
