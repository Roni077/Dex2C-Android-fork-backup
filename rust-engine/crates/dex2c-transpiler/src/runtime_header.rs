//! Generates d2c_runtime.h runtime macros, exception checking, and register unions.

pub fn generate_runtime_header(module_name: &str) -> String {
    format!(r#"/**
 * Dex2C Native Runtime Header
 * Module: {module_name}
 * Generated automatically by Dex2C Studio Rust Engine.
 */

#ifndef D2C_RUNTIME_{module_name}_H
#define D2C_RUNTIME_{module_name}_H

#include <jni.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {{
#endif

// Universal 64-bit multi-type register representation
typedef union {{
    jint i;
    jlong j;
    jfloat f;
    jdouble d;
    jobject l;
    void* ptr;
}} d2c_reg;

// Null checks
#define DEX2C_CHECK_NULL(env, ref) do {{ \
    if (!(ref)) {{ return; }} \
}} while (0)

#define DEX2C_CHECK_NULL_RET(env, ref, ret) do {{ \
    if (!(ref)) {{ return (ret); }} \
}} while (0)

// Exception checks
#define DEX2C_CHECK_EXCEPTION(env) do {{ \
    if ((env)->ExceptionCheck()) {{ return; }} \
}} while (0)

#define DEX2C_CHECK_EXCEPTION_RET(env, ret) do {{ \
    if ((env)->ExceptionCheck()) {{ return (ret); }} \
}} while (0)

// Helper for finding classes safely
static inline jclass d2c_find_class(JNIEnv* env, const char* name) {{
    jclass cls = env->FindClass(name);
    if (!cls) {{
        env->ExceptionClear();
    }}
    return cls;
}}

#ifdef __cplusplus
}}
#endif

#endif // D2C_RUNTIME_{module_name}_H
"#, module_name = sanitize_macro_ident(module_name))
}

fn sanitize_macro_ident(s: &str) -> String {
    s.chars().map(|c| if c.is_alphanumeric() { c.to_ascii_uppercase() } else { '_' }).collect()
}
