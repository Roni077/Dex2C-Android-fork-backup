//! High-speed SIMD RegexSet filter rule evaluator for Dex2C Studio.

use crate::parser::DexFile;
use regex::RegexSet;
use std::collections::HashSet;

pub struct CompiledFilter {
    pub exact_matches: HashSet<String>,
    pub includes: Option<RegexSet>,
    pub excludes: Option<RegexSet>,
    pub allow_init: bool,
    pub skip_synthetic: bool,
}

pub struct FilterMatchResult {
    pub matched_classes: Vec<i32>,
    pub matched_methods: Vec<i32>,
}

impl CompiledFilter {
    pub fn new(rules: &[&str], allow_init: bool, skip_synthetic: bool) -> Self {
        let mut exact_matches = HashSet::new();
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
            } else if let Some(stripped) = line.strip_prefix('=') {
                let pattern = stripped.trim();
                if !pattern.is_empty() {
                    exact_matches.insert(pattern.to_string());
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

        Self {
            exact_matches,
            includes,
            excludes,
            allow_init,
            skip_synthetic,
        }
    }

    #[inline]
    pub fn matches(&self, class_desc: &str, method_name: &str, is_init: bool, access_flags: u32) -> bool {
        if !self.allow_init && is_init {
            return false;
        }

        // ACC_SYNTHETIC = 0x1000
        if self.skip_synthetic && (access_flags & 0x1000 != 0) {
            return false;
        }

        let mut target = String::with_capacity(class_desc.len() + method_name.len() + 3);
        target.push_str(class_desc);
        target.push_str("->");
        target.push_str(method_name);
        target.push('(');

        let mut dcc_target = String::with_capacity(class_desc.len() + method_name.len() + 1);
        dcc_target.push_str(class_desc);
        dcc_target.push_str(method_name);
        dcc_target.push('(');

        let java_form = to_java_name(class_desc);
        let mut java_target = String::with_capacity(java_form.len() + method_name.len() + 2);
        java_target.push_str(&java_form);
        java_target.push('.');
        java_target.push_str(method_name);

        if let Some(ref exc) = self.excludes {
            if exc.is_match(&target) || exc.is_match(&dcc_target) || exc.is_match(&java_target) {
                return false;
            }
        }

        if !self.exact_matches.is_empty() {
            if self.exact_matches.contains(&target)
                || self.exact_matches.contains(&dcc_target)
                || self.exact_matches.contains(&java_target)
                || self.exact_matches.iter().any(|m| {
                    target.starts_with(m) || dcc_target.starts_with(m) || m.starts_with(&dcc_target) || m.starts_with(&target)
                })
            {
                return true;
            }
        }

        if let Some(ref inc) = self.includes {
            inc.is_match(&target) || inc.is_match(&dcc_target) || inc.is_match(&java_target)
        } else {
            self.exact_matches.is_empty()
        }
    }
}

pub fn to_java_name(desc: &str) -> String {
    let s = desc.strip_prefix('L').unwrap_or(desc);
    let s = s.strip_suffix(';').unwrap_or(s);
    s.replace('/', ".")
}

pub fn evaluate_dex_methods_and_classes(
    dex: &DexFile,
    rules: &[&str],
    allow_init: bool,
    skip_synthetic: bool,
) -> FilterMatchResult {
    let filter = CompiledFilter::new(rules, allow_init, skip_synthetic);
    let mut matched_classes = Vec::new();
    let mut matched_methods = Vec::new();

    for c_idx in 0..dex.class_def_count() {
        if let Some(cls) = dex.get_class_def_full(c_idx) {
            let mut class_matched = false;
            for m in cls.direct_methods.iter().chain(cls.virtual_methods.iter()) {
                let is_init = m.name == "<init>" || m.name == "<clinit>";
                if filter.matches(cls.descriptor, m.name, is_init, m.access_flags) {
                    matched_methods.push(m.method_idx as i32);
                    class_matched = true;
                }
            }
            if class_matched {
                matched_classes.push(cls.class_def_idx as i32);
            }
        }
    }

    FilterMatchResult {
        matched_classes,
        matched_methods,
    }
}
