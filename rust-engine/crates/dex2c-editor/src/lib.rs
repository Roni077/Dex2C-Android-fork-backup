//! dex2c-editor: High-performance Smali text editor engine for Dex2C Studio.

pub mod buffer;
pub mod history;
pub mod indexer;
pub mod search;
pub mod tokenizer;

use buffer::RopeBuffer;
use history::HistoryManager;
use indexer::LabelIndexer;
use search::{SearchEngine, SearchMatch};
use tokenizer::{FilterTokenizer, SmaliTokenizer, TokenSpan};

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EditorGrammar {
    Smali = 0,
    FilterTxt = 1,
}

pub struct EditorSession {
    buffer: RopeBuffer,
    history: HistoryManager,
    indexer: LabelIndexer,
    searcher: SearchEngine,
    grammar: EditorGrammar,
}

impl EditorSession {
    pub fn new(initial_text: &str) -> Self {
        Self::with_grammar(initial_text, EditorGrammar::Smali)
    }

    pub fn with_grammar(initial_text: &str, grammar: EditorGrammar) -> Self {
        let buffer = RopeBuffer::new(initial_text);
        let mut indexer = LabelIndexer::new();
        if grammar == EditorGrammar::Smali {
            indexer.rebuild(&buffer);
        }
        Self {
            buffer,
            history: HistoryManager::new(),
            indexer,
            searcher: SearchEngine::new(),
            grammar,
        }
    }

    pub fn set_grammar(&mut self, grammar: EditorGrammar) {
        self.grammar = grammar;
        if grammar == EditorGrammar::Smali {
            self.indexer.rebuild(&self.buffer);
        }
    }

    pub fn get_grammar(&self) -> EditorGrammar {
        self.grammar
    }

    pub fn line_count(&self) -> usize {
        self.buffer.len_lines()
    }

    pub fn len_chars(&self) -> usize {
        self.buffer.len_chars()
    }

    pub fn get_lines(&self, start_line: usize, count: usize) -> Vec<String> {
        self.buffer.get_lines(start_line, count)
    }

    pub fn get_line(&self, line_idx: usize) -> Option<String> {
        self.buffer.get_line(line_idx)
    }

    pub fn get_line_tokens(&self, start_line: usize, count: usize) -> Vec<Vec<TokenSpan>> {
        match self.grammar {
            EditorGrammar::Smali => SmaliTokenizer::tokenize_lines(&self.buffer, start_line, count),
            EditorGrammar::FilterTxt => FilterTokenizer::tokenize_lines(&self.buffer, start_line, count),
        }
    }

    pub fn apply_edit(&mut self, start_char: usize, end_char: usize, text: &str) {
        let old_text = self.buffer.slice_to_string(start_char, end_char);
        self.history.record_edit(start_char, old_text, text.to_string());
        self.buffer.replace(start_char, end_char, text);
        if self.grammar == EditorGrammar::Smali {
            self.indexer.rebuild(&self.buffer);
        }
    }

    pub fn undo(&mut self) -> Option<usize> {
        if let Some(inv) = self.history.undo() {
            self.buffer.replace(inv.start_char, inv.end_char, &inv.text);
            if self.grammar == EditorGrammar::Smali {
                self.indexer.rebuild(&self.buffer);
            }
            Some(inv.start_char)
        } else {
            None
        }
    }

    pub fn redo(&mut self) -> Option<usize> {
        if let Some(fwd) = self.history.redo() {
            self.buffer.replace(fwd.start_char, fwd.end_char, &fwd.text);
            if self.grammar == EditorGrammar::Smali {
                self.indexer.rebuild(&self.buffer);
            }
            Some(fwd.start_char)
        } else {
            None
        }
    }

    pub fn can_undo(&self) -> bool {
        self.history.can_undo()
    }

    pub fn can_redo(&self) -> bool {
        self.history.can_redo()
    }

    pub fn resolve_label_jump(&self, line_index: usize) -> Option<usize> {
        self.indexer.resolve_jump_at_line(&self.buffer, line_index)
    }

    pub fn get_full_text(&self) -> String {
        self.buffer.to_string()
    }

    pub fn search(&self, query: &str, is_regex: bool, case_sensitive: bool, max_results: usize) -> Vec<SearchMatch> {
        self.searcher.search(&self.buffer, query, is_regex, case_sensitive, max_results)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_SMALI: &str = r#".class public Lcom/example/MyClass;
.super Ljava/lang/Object;
.source "MyClass.java"

.method public static verify(I)Z
    .registers 2
    .param p0, "value"    # I

    const/4 v0, 0x1
    if-lez p0, :cond_pos
    const/4 v0, 0x0
    goto :return_val

:cond_pos
    const/4 v0, 0x1

:return_val
    return v0
.end method
"#;

    #[test]
    fn test_editor_session_initialization_and_lines() {
        let session = EditorSession::new(SAMPLE_SMALI);
        assert!(session.line_count() >= 16);
        let first_3 = session.get_lines(0, 3);
        assert_eq!(first_3.len(), 3);
        assert_eq!(first_3[0], ".class public Lcom/example/MyClass;");
        assert_eq!(first_3[1], ".super Ljava/lang/Object;");
    }

    #[test]
    fn test_tokenization_spans() {
        let session = EditorSession::new(SAMPLE_SMALI);
        let tokens = session.get_line_tokens(0, 1);
        assert_eq!(tokens.len(), 1);
        let line0_tokens = &tokens[0];
        assert!(line0_tokens.iter().any(|t| t.kind == tokenizer::TokenKind::Directive as u8));
        assert!(line0_tokens.iter().any(|t| t.kind == tokenizer::TokenKind::TypeDescriptor as u8));
    }

    #[test]
    fn test_label_jumping() {
        let session = EditorSession::new(SAMPLE_SMALI);
        // Find line with "if-lez p0, :cond_pos"
        let lines = session.get_lines(0, session.line_count());
        let if_line = lines.iter().position(|l| l.contains("if-lez")).expect("if line found");
        let jump = session.resolve_label_jump(if_line);
        assert!(jump.is_some());
        let target_line = jump.unwrap();
        assert_eq!(lines[target_line].trim(), ":cond_pos");

        // Find line with "goto :return_val"
        let goto_line = lines.iter().position(|l| l.contains("goto :return_val")).expect("goto line found");
        let goto_jump = session.resolve_label_jump(goto_line);
        assert!(goto_jump.is_some());
        assert_eq!(lines[goto_jump.unwrap()].trim(), ":return_val");
    }

    #[test]
    fn test_edit_and_undo_redo() {
        let mut session = EditorSession::new("line 1\nline 2\nline 3");
        assert_eq!(session.line_count(), 3);

        // Edit "line 2" to "line EDIT"
        session.apply_edit(7, 13, "line EDIT");
        assert_eq!(session.get_lines(0, 3)[1], "line EDIT");
        assert!(session.can_undo());

        // Undo
        let undo_pos = session.undo();
        assert!(undo_pos.is_some());
        assert_eq!(session.get_lines(0, 3)[1], "line 2");
        assert!(session.can_redo());

        // Redo
        let redo_pos = session.redo();
        assert!(redo_pos.is_some());
        assert_eq!(session.get_lines(0, 3)[1], "line EDIT");
    }

    #[test]
    fn test_search() {
        let session = EditorSession::new(SAMPLE_SMALI);
        let matches = session.search("const/4", false, true, 10);
        assert_eq!(matches.len(), 3);
        assert_eq!(matches[0].line_index, 8);
    }

    #[test]
    fn test_filter_txt_grammar() {
        let sample_filter = "# Dex2C Filter Rules\n!com/example/exclude.*\n=Lcom/example/Main;onCreate()V\n^Lcom/dex2c/studio/.*";
        let mut session = EditorSession::with_grammar(sample_filter, EditorGrammar::FilterTxt);
        assert_eq!(session.line_count(), 4);

        let tokens = session.get_line_tokens(0, 4);
        assert_eq!(tokens.len(), 4);
        // Line 0: Comment
        assert_eq!(tokens[0][0].kind, tokenizer::TokenKind::Comment as u8);
        // Line 1: FilterExclusion
        assert_eq!(tokens[1][0].kind, tokenizer::TokenKind::FilterExclusion as u8);
        // Line 2: FilterExact
        assert_eq!(tokens[2][0].kind, tokenizer::TokenKind::FilterExact as u8);
        // Line 3: FilterPattern
        assert_eq!(tokens[3][0].kind, tokenizer::TokenKind::FilterPattern as u8);

        // Test set_grammar switch
        session.set_grammar(EditorGrammar::Smali);
        assert_eq!(session.get_grammar(), EditorGrammar::Smali);
    }
}
