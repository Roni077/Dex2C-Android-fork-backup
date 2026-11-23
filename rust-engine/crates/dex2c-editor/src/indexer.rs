use crate::buffer::RopeBuffer;
use std::collections::HashMap;

#[derive(Default, Debug)]
pub struct LabelIndexer {
    labels: HashMap<String, usize>,
}

impl LabelIndexer {
    pub fn new() -> Self {
        Self {
            labels: HashMap::new(),
        }
    }

    pub fn rebuild(&mut self, buffer: &RopeBuffer) {
        self.labels.clear();
        let line_count = buffer.len_lines();
        for idx in 0..line_count {
            if let Some(line) = buffer.get_line(idx) {
                let trimmed = line.trim();
                if trimmed.starts_with(':') {
                    let label = trimmed
                        .split_whitespace()
                        .next()
                        .unwrap_or(trimmed)
                        .trim_end_matches(':');
                    self.labels.insert(label.to_string(), idx);
                }
            }
        }
    }

    pub fn get_label_line(&self, label: &str) -> Option<usize> {
        self.labels.get(label).copied()
    }

    pub fn resolve_jump_at_line(&self, buffer: &RopeBuffer, line_idx: usize) -> Option<usize> {
        let line = buffer.get_line(line_idx)?;
        let trimmed = line.trim();

        // Direct goto targets: "goto :goto_0", "goto/16 :goto_1"
        if trimmed.starts_with("goto ") || trimmed.starts_with("goto/16 ") || trimmed.starts_with("goto/32 ") {
            let target = trimmed.split_whitespace().nth(1)?;
            return self.labels.get(target).copied();
        }

        // Branch opcodes: "if-nez v0, :cond_0", "if-eqz v1, :cond_1"
        if trimmed.contains(" :") {
            for part in trimmed.split_whitespace() {
                let cleaned = part.trim_matches(|c| c == ',' || c == '{' || c == '}');
                if cleaned.starts_with(':') {
                    if let Some(&target_line) = self.labels.get(cleaned) {
                        return Some(target_line);
                    }
                }
            }
        }

        None
    }
}
