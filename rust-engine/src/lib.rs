//! JNI Bridge Exports for Dex2C Studio's Rust Native Backend Engine.
//! Provides panic-guarded, zero-copy native acceleration to the Kotlin Android layer.

pub mod dex;
pub mod filter;

use dex::DexFile;
use filter::evaluate_dex_methods;
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jboolean, jbyteArray, jint, jintArray, jlong, jsize, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};

struct DexHolder {
    _buffer: Vec<u8>,
    header: dex::DexHeader,
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_DexNativeEngine_nativeOpenDex(
    mut env: JNIEnv,
    _class: JClass,
    bytes_array: jbyteArray,
    size: jsize,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if bytes_array.is_null() || size <= 0x70 {
            return 0jlong;
        }

        let mut buffer = vec![0u8; size as usize];
        if env.get_byte_array_region(bytes_array, 0, bytemuck_cast(&mut buffer)).is_err() {
            return 0jlong;
        }

        // Parse header and validate DEX binary
        let dex = match DexFile::parse(&buffer) {
            Ok(d) => d,
            Err(_) => return 0jlong,
        };

        let holder = Box::new(DexHolder {
            header: dex.header,
            _buffer: buffer,
        });

        Box::into_raw(holder) as jlong
    }));

    result.unwrap_or(0jlong)
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

        // Extract Java string rules into Rust &str vectors
        let len = match env.get_array_length(&rules_array) {
            Ok(l) => l,
            Err(_) => return std::ptr::null_mut(),
        };

        let mut rule_strings = Vec::with_capacity(len as usize);
        let mut rust_rules = Vec::with_capacity(len as usize);

        for i in 0..len {
            let jstr: JString = match env.get_object_array_element(&rules_array, i) {
                Ok(obj) => obj.into(),
                Err(_) => continue,
            };
            if jstr.is_null() {
                continue;
            }
            if let Ok(rust_str) = env.get_string(&jstr) {
                let s: String = rust_str.into();
                rule_strings.push(s);
            }
        }

        for s in &rule_strings {
            rust_rules.push(s.as_str());
        }

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

#[inline(always)]
fn bytemuck_cast(buf: &mut [u8]) -> &mut [i8] {
    unsafe { std::slice::from_raw_parts_mut(buf.as_mut_ptr() as *mut i8, buf.len()) }
}
