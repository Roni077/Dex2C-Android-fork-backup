#[derive(Clone, Debug)]
pub struct UndoDelta {
    pub start_char: usize,
    pub end_char: usize,
    pub text: String,
}

#[derive(Clone, Debug)]
pub struct UndoItem {
    pub start_char: usize,
    pub old_text: String,
    pub new_text: String,
    pub timestamp_ms: u64,
}

#[derive(Default, Debug)]
pub struct HistoryManager {
    undo_stack: Vec<UndoItem>,
    redo_stack: Vec<UndoItem>,
}

impl HistoryManager {
    pub fn new() -> Self {
        Self {
            undo_stack: Vec::new(),
            redo_stack: Vec::new(),
        }
    }

    pub fn record_edit(&mut self, start_char: usize, old_text: String, new_text: String) {
        self.redo_stack.clear();
        let now = current_time_ms();

        // Typing coalescing: if single character typed shortly after previous edit at contiguous location
        if let Some(last) = self.undo_stack.last_mut() {
            if now.saturating_sub(last.timestamp_ms) < 400
                && old_text.is_empty()
                && new_text.len() == 1
                && last.start_char + last.new_text.len() == start_char
            {
                last.new_text.push_str(&new_text);
                last.timestamp_ms = now;
                return;
            }
        }

        self.undo_stack.push(UndoItem {
            start_char,
            old_text,
            new_text,
            timestamp_ms: now,
        });

        if self.undo_stack.len() > 1000 {
            self.undo_stack.remove(0);
        }
    }

    pub fn undo(&mut self) -> Option<UndoDelta> {
        let item = self.undo_stack.pop()?;
        let end_char = item.start_char + item.new_text.chars().count();
        let delta = UndoDelta {
            start_char: item.start_char,
            end_char,
            text: item.old_text.clone(),
        };
        self.redo_stack.push(item);
        Some(delta)
    }

    pub fn redo(&mut self) -> Option<UndoDelta> {
        let item = self.redo_stack.pop()?;
        let end_char = item.start_char + item.old_text.chars().count();
        let delta = UndoDelta {
            start_char: item.start_char,
            end_char,
            text: item.new_text.clone(),
        };
        self.undo_stack.push(item);
        Some(delta)
    }

    pub fn can_undo(&self) -> bool {
        !self.undo_stack.is_empty()
    }

    pub fn can_redo(&self) -> bool {
        !self.redo_stack.is_empty()
    }
}

fn current_time_ms() -> u64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}
