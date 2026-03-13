use crate::lexer::{lex_core, CoreToken, SpannedToken};

pub trait Plugin {
    fn process(&self, filepath: &str, stream: Vec<SpannedToken<CoreToken>>, source: &str) -> Vec<SpannedToken<CoreToken>>;
}

pub struct MarkdownPlugin;

impl Plugin for MarkdownPlugin {
    fn process(&self, filepath: &str, stream: Vec<SpannedToken<CoreToken>>, source: &str) -> Vec<SpannedToken<CoreToken>> {
        // If it is not a markdown file, plugins for markdown should transparently return the original input without changes.
        if !filepath.ends_with(".md") {
            return stream;
        }

        let mut out = Vec::new();
        let mut in_beancount_block = false;

        let tokens = &stream;

        // Determine if we are at the start of a line
        let mut is_start_of_line = true;

        let is_codeblock_edge = |idx: usize, tokens: &[SpannedToken<CoreToken>]| -> Option<(bool, usize)> {
            // we look for ```
            if idx + 2 < tokens.len()
                && tokens[idx].0 == CoreToken::Backtick
                && tokens[idx+1].0 == CoreToken::Backtick
                && tokens[idx+2].0 == CoreToken::Backtick
            {
                // Check if it says beancount
                if idx + 3 < tokens.len() && tokens[idx+3].0 == CoreToken::Ident && source[tokens[idx+3].1.clone()] == *"beancount" {
                    return Some((true, 4));
                } else if idx + 3 < tokens.len() && tokens[idx+3].0 == CoreToken::Newline {
                    return Some((false, 3));
                } else if idx + 3 == tokens.len() {
                    return Some((false, 3));
                }
                return Some((false, 3)); // simple CodeBlockEnd equivalent
            }
            None
        };

        let mut i = 0;
        while i < tokens.len() {
            let token = &tokens[i];

            if let Some((is_start, len)) = is_codeblock_edge(i, tokens) {
                if !in_beancount_block && is_start {
                    in_beancount_block = true;
                    for j in 0..len {
                        out.push(tokens[i + j].clone());
                    }
                    i += len;
                    is_start_of_line = false; // it is in the middle of a line now or end depending on trailing newline, but wait, if it's the start it's the rest of the line. Wait, we should just let the main loop handle the trailing newline.
                    continue;
                } else if in_beancount_block && !is_start {
                    in_beancount_block = false;
                    for j in 0..len {
                        out.push(tokens[i + j].clone());
                    }
                    i += len;
                    is_start_of_line = false;
                    continue;
                }
            }

            if is_start_of_line && !in_beancount_block {
                if token.0 == CoreToken::Newline {
                    out.push((CoreToken::PunctOrOther, 0..0)); // ';'
                } else if token.0 == CoreToken::Whitespace {
                    out.push((CoreToken::PunctOrOther, 0..0)); // ';'
                } else {
                    out.push((CoreToken::PunctOrOther, 0..0)); // ';'
                    out.push((CoreToken::Whitespace, 0..0));   // ' '
                }
            }

            out.push(token.clone());
            is_start_of_line = token.0 == CoreToken::Newline;
            i += 1;
        }

        out
    }
}

pub fn process_markdown_stream(filepath: &str, input: &str) -> String {
    let stream = lex_core(input);
    let plugin = MarkdownPlugin;
    let modified = plugin.process(filepath, stream, input);

    let mut out_str = String::new();
    for (tok, span) in modified {
        if span.start == 0 && span.end == 0 {
            if tok == CoreToken::PunctOrOther {
                out_str.push(';');
            } else if tok == CoreToken::Whitespace {
                out_str.push(' ');
            }
        } else {
            out_str.push_str(&input[span]);
        }
    }
    out_str
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_markdown_plugin() {
        let input = "This is a test.
Another line.
```beancount
2023-01-01 * \"Payee\" \"Narration\"
  Assets:Checking -10.00 USD
  Expenses:Food 10.00 USD
```
Some more markdown here.
And another line.";

        let expected = "; This is a test.
; Another line.
```beancount
2023-01-01 * \"Payee\" \"Narration\"
  Assets:Checking -10.00 USD
  Expenses:Food 10.00 USD
```
; Some more markdown here.
; And another line.";

        let output = process_markdown_stream("test.md", input);
        assert_eq!(output, expected);

        // Also test that non-md files are not modified
        let output_unmodified = process_markdown_stream("test.beancount", input);
        assert_eq!(output_unmodified, input);
    }

    #[test]
    fn test_markdown_plugin_starts_with_block() {
        let input = "```beancount
2023-01-01 * \"Payee\" \"Narration\"
  Assets:Checking -10.00 USD
  Expenses:Food 10.00 USD
```
Some more markdown here.
And another line.";

        let expected = "```beancount
2023-01-01 * \"Payee\" \"Narration\"
  Assets:Checking -10.00 USD
  Expenses:Food 10.00 USD
```
; Some more markdown here.
; And another line.";

        let output = process_markdown_stream("test.md", input);
        assert_eq!(output, expected);
    }
}
