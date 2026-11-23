use ropey::Rope;

#[derive(Clone, Debug)]
pub struct RopeBuffer {
    rope: Rope,
}

impl RopeBuffer {
    pub fn new(initial_text: &str) -> Self {
        Self {
            rope: Rope::from_str(initial_text),
        }
    }

    pub fn len_chars(&self) -> usize {
        self.rope.len_chars()
    }

    pub fn len_lines(&self) -> usize {
        self.rope.len_lines()
    }

    pub fn line_to_char(&self, line_idx: usize) -> usize {
        let safe_idx = line_idx.min(self.rope.len_lines().saturating_sub(1));
        self.rope.line_to_char(safe_idx)
    }

    pub fn char_to_line(&self, char_idx: usize) -> usize {
        let safe_idx = char_idx.min(self.rope.len_chars());
        self.rope.char_to_line(safe_idx)
    }

    pub fn insert(&mut self, char_idx: usize, text: &str) {
        let safe_idx = char_idx.min(self.rope.len_chars());
        self.rope.insert(safe_idx, text);
    }

    pub fn delete(&mut self, start_char: usize, end_char: usize) {
        let total = self.rope.len_chars();
        let start = start_char.min(total);
        let end = end_char.min(total);
        if start < end {
            self.rope.remove(start..end);
        }
    }

    pub fn replace(&mut self, start_char: usize, end_char: usize, text: &str) {
        self.delete(start_char, end_char);
        self.insert(start_char, text);
    }

    pub fn get_line(&self, line_idx: usize) -> Option<String> {
        if line_idx >= self.rope.len_lines() {
            return None;
        }
        let line = self.rope.line(line_idx);
        let mut s = line.to_string();
        // Strip trailing \r or \n for cleaner presentation
        if s.ends_with('\n') {
            s.pop();
            if s.ends_with('\r') {
                s.pop();
            }
        }
        Some(s)
    }

    pub fn get_lines(&self, start_line: usize, count: usize) -> Vec<String> {
        let total_lines = self.rope.len_lines();
        if start_line >= total_lines || count == 0 {
            return Vec::new();
        }
        let end_line = (start_line + count).min(total_lines);
        let mut result = Vec::with_capacity(end_line - start_line);
        for idx in start_line..end_line {
            if let Some(line) = self.get_line(idx) {
                result.push(line);
            }
        }
        result
    }

    pub fn slice_to_string(&self, start_char: usize, end_char: usize) -> String {
        let total = self.rope.len_chars();
        let start = start_char.min(total);
        let end = end_char.min(total);
        if start >= end {
            return String::new();
        }
        self.rope.slice(start..end).to_string()
    }

    pub fn to_string(&self) -> String {
        self.rope.to_string()
    }
}
