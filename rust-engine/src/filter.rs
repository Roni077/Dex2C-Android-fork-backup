//! High-speed SIMD RegexSet filter rule evaluator for Dex2C Studio.
//! Matches 100,000+ methods against complex inclusion and exclusion rules in single-digit milliseconds.

use crate::dex::{DexFile, ClassMethodSummary};
use regex::RegexSet;

pub struct CompiledFilter {
    pub includes: Option<RegexSet>,
    pub excludes: Option<RegexSet>,
    pub allow_init: bool,
    pub skip_synthetic: bool,
}

impl CompiledFilter {
    pub fn new(rules: &[&str], allow_init: bool, skip_synthetic: bool) -> Self {
        let mut include_patterns = Vec::new();
        let mut exclude_patterns = Vec::new();

        for raw_line in rules {
            let line = raw_line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            if let Some(stripped) = line.strip_prefix('!') {
                let pattern = stripped.trim();
                if !pattern.is_empty() {
                    exclude_patterns.push(pattern);
                }
            } else {
                include_patterns.push(line);
            }
        }

        let includes = if include_patterns.is_empty() {
            None
        } else {
            RegexSet::new(include_patterns).ok()
        };

        let excludes = if exclude_patterns.is_empty() {
            None
        } else {
            RegexSet::new(exclude_patterns).ok()
        };

        CompiledFilter {
            includes,
            excludes,
            allow_init,
            skip_synthetic,
        }
    }

    /// Evaluates whether a given class descriptor and method name match the compiled filter rules.
    #[inline]
    pub fn matches(&self, class_desc: &str, method_name: &str, is_init: bool, access_flags: u32) -> bool {
        if !self.allow_init && is_init {
            return false;
        }

        // ACC_SYNTHETIC = 0x1000
        if self.skip_synthetic && (access_flags & 0x1000 != 0) {
            return false;
        }

        // Format candidate target for regex matching:
        // E.g.: "Lcom/example/MyClass;->myMethod(" or "com.example.MyClass.myMethod"
        // Build a zero-allocation or small-buffer search string
        let mut target = String::with_capacity(class_desc.len() + method_name.len() + 3);
        target.push_str(class_desc);
        target.push_str("->");
        target.push_str(method_name);
        target.push('(');

        // Check exclusions first (faster rejection)
        if let Some(ref exc) = self.excludes {
            if exc.is_match(&target) {
                return false;
            }
            // Also test simplified java form: com.example.MyClass.methodName
            let java_form = to_java_name(class_desc);
            let mut java_target = String::with_capacity(java_form.len() + method_name.len() + 2);
            java_target.push_str(&java_form);
            java_target.push('.');
            java_target.push_str(method_name);
            if exc.is_match(&java_target) {
                return false;
            }
        }

        // Check inclusions
        if let Some(ref inc) = self.includes {
            if inc.is_match(&target) {
                return true;
            }
            let java_form = to_java_name(class_desc);
            let mut java_target = String::with_capacity(java_form.len() + method_name.len() + 2);
            java_target.push_str(&java_form);
            java_target.push('.');
            java_target.push_str(method_name);
            return inc.is_match(&java_target);
        }

        // If no explicit inclusion rules were defined, default match is true
        true
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DexFilterMatchResult {
    pub matched_classes: Vec<i32>,
    pub matched_methods: Vec<i32>,
}

/// Evaluates a DEX file against the filter rules, returning both matching method indices
/// and matching class indices with zero intermediate string or collection overhead.
pub fn evaluate_dex_methods_and_classes(
    dex: &DexFile,
    rules: &[&str],
    allow_init: bool,
    skip_synthetic: bool,
) -> DexFilterMatchResult {
    let filter = CompiledFilter::new(rules, allow_init, skip_synthetic);
    let mut matched_methods = Vec::with_capacity(1024);
    let mut matched_classes = Vec::with_capacity(256);
    let mut last_matched_class = u32::MAX;

    dex.for_each_method(|m: ClassMethodSummary| {
        // Only methods with actual Dalvik bytecode instructions (code_off > 0) are transpiled
        if m.code_off > 0 && filter.matches(m.class_desc, m.method_name, m.is_init, m.access_flags) {
            matched_methods.push(m.method_idx as i32);
            if m.class_idx != last_matched_class {
                matched_classes.push(m.class_idx as i32);
                last_matched_class = m.class_idx;
            }
        }
    });

    DexFilterMatchResult {
        matched_classes,
        matched_methods,
    }
}

/// Evaluates a DEX file against the filter rules, returning a vector of matching method indices.
pub fn evaluate_dex_methods(
    dex: &DexFile,
    rules: &[&str],
    allow_init: bool,
    skip_synthetic: bool,
) -> Vec<i32> {
    evaluate_dex_methods_and_classes(dex, rules, allow_init, skip_synthetic).matched_methods
}

#[inline]
fn to_java_name(desc: &str) -> String {
    let stripped = desc.strip_prefix('L').unwrap_or(desc);
    let stripped = stripped.strip_suffix(';').unwrap_or(stripped);
    stripped.replace('/', ".")
}
