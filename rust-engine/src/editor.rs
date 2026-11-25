//! JNI bindings for Dex2C Studio's Editor Native Engine.
//! Backed by `dex2c-editor` providing high-performance rope-based editing,
//! Smali syntax tokenization, and label jumping.

use dex2c_editor::EditorSession;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jintArray, jlong, jobjectArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeCreateSession(
    mut env: JNIEnv,
    _class: JClass,
    initial_text: JString,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let text_str = if initial_text.is_null() {
            String::new()
        } else {
            env.get_string(&initial_text)
                .map(|s| s.to_string_lossy().into_owned())
                .unwrap_or_default()
        };

        let session = Box::new(EditorSession::new(&text_str));
        Box::into_raw(session) as jlong
    }));

    result.unwrap_or(0i64)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeDestroySession(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if handle != 0 {
            let _ = Box::from_raw(handle as *mut EditorSession);
        }
    }));
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeGetLineCount(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 {
            return 0;
        }
        let session = &*(handle as *const EditorSession);
        session.line_count() as jint
    }));

    result.unwrap_or(0)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeGetLines(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    start_line: jint,
    count: jint,
) -> jobjectArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 || start_line < 0 || count <= 0 {
            return std::ptr::null_mut();
        }
        let session = &*(handle as *const EditorSession);
        let lines = session.get_lines(start_line as usize, count as usize);

        let string_class = match env.find_class("java/lang/String") {
            Ok(c) => c,
            Err(_) => return std::ptr::null_mut(),
        };

        let empty_str = match env.new_string("") {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };

        let array = match env.new_object_array(lines.len() as jint, &string_class, &empty_str) {
            Ok(a) => a,
            Err(_) => return std::ptr::null_mut(),
        };

        for (i, line) in lines.iter().enumerate() {
            if let Ok(jstr) = env.new_string(line) {
                let _ = env.set_object_array_element(&array, i as jint, &jstr);
            }
        }

        array.into_raw()
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeGetLineTokens(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    start_line: jint,
    count: jint,
) -> jintArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 || start_line < 0 || count <= 0 {
            return std::ptr::null_mut();
        }
        let session = &*(handle as *const EditorSession);
        let token_lines = session.get_line_tokens(start_line as usize, count as usize);

        // Pack format: 4 ints per span [line_offset, start_col, end_col, kind]
        let mut flat_spans: Vec<jint> = Vec::new();
        for (offset, line_spans) in token_lines.iter().enumerate() {
            for span in line_spans {
                flat_spans.push(offset as jint);
                flat_spans.push(span.start_col as jint);
                flat_spans.push(span.end_col as jint);
                flat_spans.push(span.kind as jint);
            }
        }

        let j_array = match env.new_int_array(flat_spans.len() as jint) {
            Ok(a) => a,
            Err(_) => return std::ptr::null_mut(),
        };

        if !flat_spans.is_empty() {
            if env.set_int_array_region(&j_array, 0, &flat_spans).is_err() {
                return std::ptr::null_mut();
            }
        }

        j_array.into_raw()
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeApplyEdit(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    start_char: jint,
    end_char: jint,
    replacement: JString,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 || start_char < 0 || end_char < start_char {
            return;
        }
        let session = &mut *(handle as *mut EditorSession);
        let rep_str = if replacement.is_null() {
            String::new()
        } else {
            env.get_string(&replacement)
                .map(|s| s.to_string_lossy().into_owned())
                .unwrap_or_default()
        };

        session.apply_edit(start_char as usize, end_char as usize, &rep_str);
    }));
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeUndo(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 {
            return -1;
        }
        let session = &mut *(handle as *mut EditorSession);
        session.undo().map(|pos| pos as jint).unwrap_or(-1)
    }));

    result.unwrap_or(-1)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeRedo(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 {
            return -1;
        }
        let session = &mut *(handle as *mut EditorSession);
        session.redo().map(|pos| pos as jint).unwrap_or(-1)
    }));

    result.unwrap_or(-1)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeCanUndo(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 {
            return JNI_FALSE;
        }
        let session = &*(handle as *const EditorSession);
        if session.can_undo() {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    }));

    result.unwrap_or(JNI_FALSE)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeCanRedo(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 {
            return JNI_FALSE;
        }
        let session = &*(handle as *const EditorSession);
        if session.can_redo() {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    }));

    result.unwrap_or(JNI_FALSE)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeResolveLabelJump(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    line_index: jint,
) -> jint {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 || line_index < 0 {
            return -1;
        }
        let session = &*(handle as *const EditorSession);
        session
            .resolve_label_jump(line_index as usize)
            .map(|t| t as jint)
            .unwrap_or(-1)
    }));

    result.unwrap_or(-1)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeGetFullText(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 {
            return std::ptr::null_mut();
        }
        let session = &*(handle as *const EditorSession);
        let text = session.get_full_text();
        env.new_string(text).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeSearch(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    query: JString,
    is_regex: jboolean,
    case_sensitive: jboolean,
    max_results: jint,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if handle == 0 || query.is_null() {
            return std::ptr::null_mut();
        }
        let session = &*(handle as *const EditorSession);
        let q_str = env
            .get_string(&query)
            .map(|s| s.to_string_lossy().into_owned())
            .unwrap_or_default();

        let matches = session.search(
            &q_str,
            is_regex == JNI_TRUE,
            case_sensitive == JNI_TRUE,
            max_results.max(1) as usize,
        );

        let json = serde_json::to_string(&matches).unwrap_or_else(|_| "[]".to_string());
        env.new_string(json).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
    }));

    result.unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_dex2c_studio_nativeengine_EditorNativeEngine_nativeSetGrammar(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    grammar: jint,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if handle != 0 {
            let session = &mut *(handle as *mut EditorSession);
            let g = match grammar {
                1 => dex2c_editor::EditorGrammar::FilterTxt,
                _ => dex2c_editor::EditorGrammar::Smali,
            };
            session.set_grammar(g);
        }
    }));
}
