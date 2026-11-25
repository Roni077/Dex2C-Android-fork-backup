use crate::buffer::RopeBuffer;
use serde::{Deserialize, Serialize};

#[repr(u8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TokenKind {
    None = 0,
    Directive = 1,       // .class, .super, .method, etc.
    Opcode = 2,          // invoke-*, return, goto, const, etc.
    Register = 3,        // v0, p1, etc.
    LabelDef = 4,        // :cond_0 at line start
    LabelRef = 5,        // :cond_0 in branch instruction
    MemberRef = 6,       // ->methodName or ->fieldName
    TypeDescriptor = 7,   // Lcom/example/Class;
    StringLiteral = 8,    // "Hello World"
    Comment = 9,         // # ...
    Number = 10,         // 0x1, 100
    FilterExclusion = 11,// !exclusion rule
    FilterExact = 12,    // =exact rule
    FilterPattern = 13,  // pattern or general match rule
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TokenSpan {
    pub start_col: u16,
    pub end_col: u16,
    pub kind: u8,
}

pub struct SmaliTokenizer;

impl SmaliTokenizer {
    pub fn tokenize_lines(buffer: &RopeBuffer, start_line: usize, count: usize) -> Vec<Vec<TokenSpan>> {
        let lines = buffer.get_lines(start_line, count);
        lines.into_iter().map(|line| Self::tokenize_line(&line)).collect()
    }

    pub fn tokenize_line(line: &str) -> Vec<TokenSpan> {
        let mut spans = Vec::new();
        let chars: Vec<char> = line.chars().collect();
        let len = chars.len();
        if len == 0 {
            return spans;
        }

        let mut i = 0;
        while i < len {
            let c = chars[i];

            // Whitespace
            if c.is_whitespace() {
                i += 1;
                continue;
            }

            // Comment
            if c == '#' {
                spans.push(TokenSpan {
                    start_col: i as u16,
                    end_col: len as u16,
                    kind: TokenKind::Comment as u8,
                });
                break;
            }

            // String Literal
            if c == '"' {
                let start = i;
                i += 1;
                let mut escaped = false;
                while i < len {
                    if escaped {
                        escaped = false;
                    } else if chars[i] == '\\' {
                        escaped = true;
                    } else if chars[i] == '"' {
                        i += 1;
                        break;
                    }
                    i += 1;
                }
                spans.push(TokenSpan {
                    start_col: start as u16,
                    end_col: i as u16,
                    kind: TokenKind::StringLiteral as u8,
                });
                continue;
            }

            // Label Definition or Reference (:label)
            if c == ':' {
                let start = i;
                i += 1;
                while i < len && (chars[i].is_alphanumeric() || chars[i] == '_' || chars[i] == '$') {
                    i += 1;
                }
                let is_def = start == 0 || chars[..start].iter().all(|ch| ch.is_whitespace());
                spans.push(TokenSpan {
                    start_col: start as u16,
                    end_col: i as u16,
                    kind: if is_def {
                        TokenKind::LabelDef as u8
                    } else {
                        TokenKind::LabelRef as u8
                    },
                });
                continue;
            }

            // Directive (.class, .method, etc.)
            if c == '.' {
                let start = i;
                i += 1;
                while i < len && (chars[i].is_alphanumeric() || chars[i] == '-' || chars[i] == '_') {
                    i += 1;
                }
                spans.push(TokenSpan {
                    start_col: start as u16,
                    end_col: i as u16,
                    kind: TokenKind::Directive as u8,
                });
                continue;
            }

            // Member Reference (->method or ->field)
            if c == '-' && i + 1 < len && chars[i + 1] == '>' {
                let start = i;
                i += 2;
                while i < len && !chars[i].is_whitespace() && chars[i] != '(' && chars[i] != ':' && chars[i] != ';' {
                    i += 1;
                }
                spans.push(TokenSpan {
                    start_col: start as u16,
                    end_col: i as u16,
                    kind: TokenKind::MemberRef as u8,
                });
                continue;
            }

            // Type Descriptor (Ljava/lang/String;)
            if c == 'L' && i + 1 < len && (chars[i + 1].is_alphanumeric() || chars[i + 1] == '/') {
                let start = i;
                let mut found_semicolon = false;
                while i < len && !chars[i].is_whitespace() {
                    if chars[i] == ';' {
                        i += 1;
                        found_semicolon = true;
                        break;
                    }
                    i += 1;
                }
                if found_semicolon {
                    spans.push(TokenSpan {
                        start_col: start as u16,
                        end_col: i as u16,
                        kind: TokenKind::TypeDescriptor as u8,
                    });
                    continue;
                } else {
                    i = start; // Fallback to word scanning
                }
            }

            // Numbers (hex: 0x..., or decimal)
            if c == '0' && i + 1 < len && (chars[i + 1] == 'x' || chars[i + 1] == 'X') {
                let start = i;
                i += 2;
                while i < len && (chars[i].is_ascii_hexdigit() || chars[i] == 'L' || chars[i] == 's') {
                    i += 1;
                }
                spans.push(TokenSpan {
                    start_col: start as u16,
                    end_col: i as u16,
                    kind: TokenKind::Number as u8,
                });
                continue;
            }

            // General Word Token (Register, Opcode, or Identifier)
            if c.is_alphanumeric() || c == '-' || c == '/' || c == '_' {
                let start = i;
                while i < len && (chars[i].is_alphanumeric() || chars[i] == '-' || chars[i] == '/' || chars[i] == '_') {
                    i += 1;
                }
                let word: String = chars[start..i].iter().collect();

                let kind = if is_register(&word) {
                    TokenKind::Register
                } else if is_opcode(&word) {
                    TokenKind::Opcode
                } else {
                    TokenKind::None
                };

                if kind != TokenKind::None {
                    spans.push(TokenSpan {
                        start_col: start as u16,
                        end_col: i as u16,
                        kind: kind as u8,
                    });
                }
                continue;
            }

            i += 1;
        }

        spans
    }
}

fn is_register(word: &str) -> bool {
    if word.starts_with('v') || word.starts_with('p') {
        let suffix = &word[1..];
        !suffix.is_empty() && suffix.chars().all(|c| c.is_ascii_digit())
    } else {
        false
    }
}

fn is_opcode(word: &str) -> bool {
    matches!(
        word,
        "nop"
            | "move"
            | "move/from16"
            | "move/16"
            | "move-wide"
            | "move-wide/from16"
            | "move-wide/16"
            | "move-object"
            | "move-object/from16"
            | "move-object/16"
            | "move-result"
            | "move-result-wide"
            | "move-result-object"
            | "move-exception"
            | "return-void"
            | "return"
            | "return-wide"
            | "return-object"
            | "const/4"
            | "const/16"
            | "const"
            | "const/high16"
            | "const-wide/16"
            | "const-wide/32"
            | "const-wide"
            | "const-wide/high16"
            | "const-string"
            | "const-string/jumbo"
            | "const-class"
            | "monitor-enter"
            | "monitor-exit"
            | "check-cast"
            | "instance-of"
            | "array-length"
            | "new-instance"
            | "new-array"
            | "filled-new-array"
            | "filled-new-array/range"
            | "fill-array-data"
            | "throw"
            | "goto"
            | "goto/16"
            | "goto/32"
            | "packed-switch"
            | "sparse-switch"
            | "cmpl-float"
            | "cmpg-float"
            | "cmpl-double"
            | "cmpg-double"
            | "cmp-long"
            | "if-eq"
            | "if-ne"
            | "if-lt"
            | "if-ge"
            | "if-gt"
            | "if-le"
            | "if-eqz"
            | "if-nez"
            | "if-ltz"
            | "if-gez"
            | "if-gtz"
            | "if-lez"
            | "aget"
            | "aget-wide"
            | "aget-object"
            | "aget-boolean"
            | "aget-byte"
            | "aget-char"
            | "aget-short"
            | "aput"
            | "aput-wide"
            | "aput-object"
            | "aput-boolean"
            | "aput-byte"
            | "aput-char"
            | "aput-short"
            | "iget"
            | "iget-wide"
            | "iget-object"
            | "iget-boolean"
            | "iget-byte"
            | "iget-char"
            | "iget-short"
            | "iput"
            | "iput-wide"
            | "iput-object"
            | "iput-boolean"
            | "iput-byte"
            | "iput-char"
            | "iput-short"
            | "sget"
            | "sget-wide"
            | "sget-object"
            | "sget-boolean"
            | "sget-byte"
            | "sget-char"
            | "sget-short"
            | "sput"
            | "sput-wide"
            | "sput-object"
            | "sput-boolean"
            | "sput-byte"
            | "sput-char"
            | "sput-short"
            | "invoke-virtual"
            | "invoke-super"
            | "invoke-direct"
            | "invoke-static"
            | "invoke-interface"
            | "invoke-virtual/range"
            | "invoke-super/range"
            | "invoke-direct/range"
            | "invoke-static/range"
            | "invoke-interface/range"
    )
}

pub struct FilterTokenizer;

impl FilterTokenizer {
    pub fn tokenize_lines(buffer: &RopeBuffer, start_line: usize, count: usize) -> Vec<Vec<TokenSpan>> {
        let lines = buffer.get_lines(start_line, count);
        lines.into_iter().map(|line| Self::tokenize_line(&line)).collect()
    }

    pub fn tokenize_line(line: &str) -> Vec<TokenSpan> {
        let mut spans = Vec::new();
        let trimmed_start = line.char_indices().find(|(_, c)| !c.is_whitespace());
        let (start_idx, first_char) = match trimmed_start {
            Some((idx, c)) => (idx, c),
            None => return spans,
        };

        // Find if there is a comment #
        let mut comment_idx = None;
        for (idx, c) in line.char_indices() {
            if c == '#' {
                comment_idx = Some(idx);
                break;
            }
        }

        let rule_end = comment_idx.unwrap_or(line.len());
        if rule_end > start_idx {
            let rule_slice = &line[start_idx..rule_end];
            let trimmed_rule = rule_slice.trim_end();
            let rule_len = trimmed_rule.len();

            if rule_len > 0 && first_char != '#' {
                let end_col = (start_idx + rule_len) as u16;
                let kind = match first_char {
                    '!' => TokenKind::FilterExclusion as u8,
                    '=' => TokenKind::FilterExact as u8,
                    _ => TokenKind::FilterPattern as u8,
                };

                spans.push(TokenSpan {
                    start_col: start_idx as u16,
                    end_col,
                    kind,
                });
            }
        }

        if let Some(c_idx) = comment_idx {
            spans.push(TokenSpan {
                start_col: c_idx as u16,
                end_col: line.len() as u16,
                kind: TokenKind::Comment as u8,
            });
        }

        spans
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_filter_tokenizer_comment_only() {
        let spans = FilterTokenizer::tokenize_line("# This is a comment");
        assert_eq!(spans.len(), 1);
        assert_eq!(spans[0].kind, TokenKind::Comment as u8);
        assert_eq!(spans[0].start_col, 0);
        assert_eq!(spans[0].end_col, 19);
    }

    #[test]
    fn test_filter_tokenizer_exclusion_with_inline_comment() {
        let line = "!com/example/MyClass # exclude this";
        let spans = FilterTokenizer::tokenize_line(line);
        assert_eq!(spans.len(), 2);
        assert_eq!(spans[0].kind, TokenKind::FilterExclusion as u8);
        assert_eq!(spans[0].start_col, 0);
        assert_eq!(spans[0].end_col, 20);

        assert_eq!(spans[1].kind, TokenKind::Comment as u8);
        assert_eq!(spans[1].start_col, 21);
        assert_eq!(spans[1].end_col, line.len() as u16);
    }

    #[test]
    fn test_filter_tokenizer_exact_rule() {
        let spans = FilterTokenizer::tokenize_line("=Lcom/example/MainActivity;onCreate(Landroid/os/Bundle;)V");
        assert_eq!(spans.len(), 1);
        assert_eq!(spans[0].kind, TokenKind::FilterExact as u8);
        assert_eq!(spans[0].start_col, 0);
        assert_eq!(spans[0].end_col, 57);
    }

    #[test]
    fn test_filter_tokenizer_pattern_rule() {
        let spans = FilterTokenizer::tokenize_line("^Lcom/dex2c/studio/.*");
        assert_eq!(spans.len(), 1);
        assert_eq!(spans[0].kind, TokenKind::FilterPattern as u8);
        assert_eq!(spans[0].start_col, 0);
        assert_eq!(spans[0].end_col, 21);
    }

    #[test]
    fn test_filter_tokenizer_empty_and_whitespace() {
        assert!(FilterTokenizer::tokenize_line("").is_empty());
        assert!(FilterTokenizer::tokenize_line("   \t  ").is_empty());
    }
}
