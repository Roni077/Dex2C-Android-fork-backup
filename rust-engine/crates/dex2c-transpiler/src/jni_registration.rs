//! JNI native registration and JNI_OnLoad generation.

use dex2c_core::JniBindingStyle;
use std::fmt::Write;

pub struct NativeMethodReg {
    pub class_name: String,
    pub method_name: String,
    pub signature: String,
    pub c_func_ptr: String,
}

pub fn generate_jni_onload(
    module_name: &str,
    style: JniBindingStyle,
    methods: &[NativeMethodReg],
) -> String {
    let mut out = String::with_capacity(2048);

    let _ = writeln!(out, "#include \"d2c_runtime.h\"\n");

    if style == JniBindingStyle::RegisterNatives {
        let _ = writeln!(out, "jint JNI_OnLoad(JavaVM* vm, void* reserved) {{");
        let _ = writeln!(out, "    JNIEnv* env = NULL;");
        let _ = writeln!(out, "    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {{");
        let _ = writeln!(out, "        return JNI_ERR;");
        let _ = writeln!(out, "    }}\n");

        for m in methods {
            let _ = writeln!(out, "    {{");
            let _ = writeln!(out, "        jclass cls = env->FindClass(\"{}\");", m.class_name);
            let _ = writeln!(out, "        if (cls) {{");
            let _ = writeln!(
                out,
                "            JNINativeMethod nm = {{(char*)\"{}\", (char*)\"{}\", (void*){}}};",
                m.method_name, m.signature, m.c_func_ptr
            );
            let _ = writeln!(out, "            env->RegisterNatives(cls, &nm, 1);");
            let _ = writeln!(out, "            env->DeleteLocalRef(cls);");
            let _ = writeln!(out, "        }}");
            let _ = writeln!(out, "    }}");
        }

        let _ = writeln!(out, "\n    return JNI_VERSION_1_6;");
        let _ = writeln!(out, "}}\n");
    } else {
        let _ = writeln!(out, "jint JNI_OnLoad(JavaVM* vm, void* reserved) {{");
        let _ = writeln!(out, "    return JNI_VERSION_1_6;");
        let _ = writeln!(out, "}}\n");
    }

    out
}
