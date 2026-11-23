use crate::buffer::RopeBuffer;
use regex::RegexBuilder;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SearchMatch {
    pub line_index: usize,
    pub start_col: usize,
    pub end_col: usize,
    pub preview: String,
}

#[derive(Default, Debug)]
pub struct SearchEngine;

impl SearchEngine {
    pub fn new() -> Self {
        Self
    }

    pub fn search(
        &self,
        buffer: &RopeBuffer,
        query: &str,
        is_regex: bool,
        case_sensitive: bool,
        max_results: usize,
    ) -> Vec<SearchMatch> {
        if query.is_empty() {
            return Vec::new();
        }

        let line_count = buffer.len_lines();
        let mut results = Vec::new();

        if is_regex {
            let re_res = RegexBuilder::new(query)
                .case_insensitive(!case_sensitive)
                .build();

            if let Ok(re) = re_res {
                for line_idx in 0..line_count {
                    if let Some(line) = buffer.get_line(line_idx) {
                        for mat in re.find_iter(&line) {
                            results.push(SearchMatch {
                                line_index: line_idx,
                                start_col: mat.start(),
                                end_col: mat.end(),
                                preview: line.clone(),
                            });
                            if results.len() >= max_results {
                                return results;
                            }
                        }
                    }
                }
            }
        } else {
            let target = if case_sensitive {
                query.to_string()
            } else {
                query.to_lowercase()
            };

            for line_idx in 0..line_count {
                if let Some(line) = buffer.get_line(line_idx) {
                    let hay = if case_sensitive {
                        line.clone()
                    } else {
                        line.to_lowercase()
                    };

                    let mut start = 0;
                    while let Some(pos) = hay[start..].find(&target) {
                        let actual_pos = start + pos;
                        results.push(SearchMatch {
                            line_index: line_idx,
                            start_col: actual_pos,
                            end_col: actual_pos + query.len(),
                            preview: line.clone(),
                        });
                        if results.len() >= max_results {
                            return results;
                        }
                        start = actual_pos + target.len().max(1);
                        if start >= hay.len() {
                            break;
                        }
                    }
                }
            }
        }

        results
    }
}
