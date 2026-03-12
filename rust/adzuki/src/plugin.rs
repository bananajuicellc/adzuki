use crate::lexer::{lex_core, CoreToken, SpannedToken};

pub trait Plugin {
    fn process(&self, filepath: &str, stream: Vec<SpannedToken<'_, CoreToken>>, source: &str) -> Vec<SpannedToken<'static, CoreToken>>;
}

pub struct MarkdownPlugin;

impl Plugin for MarkdownPlugin {
    fn process(&self, filepath: &str, stream: Vec<SpannedToken<'_, CoreToken>>, source: &str) -> Vec<SpannedToken<'static, CoreToken>> {
        if !filepath.ends_with(".md") {
            return stream.into_iter().map(|(t, r)| (t, r)).collect();
        }

        let mut out = Vec::new();
        let mut in_beancount_block = false;

        let tokens = &stream;

        // Determine if we are at the start of a line
        let mut is_start_of_line = true;

        for token in tokens.iter() {
            let is_beancount_start = token.0 == CoreToken::CodeBlockStart && source[token.1.clone()].starts_with("```beancount");

            if !in_beancount_block && is_beancount_start {
                in_beancount_block = true;
                out.push(token.clone());
                // CodeBlockStart consumes trailing newlines, next token is on a new line
                is_start_of_line = true;
                continue;
            } else if in_beancount_block && token.0 == CoreToken::CodeBlockEnd {
                in_beancount_block = false;
                out.push(token.clone());
                is_start_of_line = true;
                continue;
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

            is_start_of_line = token.0 == CoreToken::Newline || token.0 == CoreToken::CodeBlockEnd;
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
