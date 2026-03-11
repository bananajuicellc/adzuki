#[derive(Debug, Clone, PartialEq)]
pub enum TokenTree {
    Group(Group),
    Ident(Ident),
    Punct(Punct),
    Literal(Literal),
}

#[derive(Debug, Clone, PartialEq)]
pub struct TokenStream {
    pub trees: Vec<TokenTree>,
}

impl TokenStream {
    pub fn new(trees: Vec<TokenTree>) -> Self {
        Self { trees }
    }
}

impl std::fmt::Display for TokenStream {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        for tree in &self.trees {
            match tree {
                TokenTree::Group(g) => {
                    match g.delimiter {
                        Delimiter::Parenthesis => write!(f, "({})", g.stream)?,
                        Delimiter::Brace => write!(f, "{{{}}}", g.stream)?,
                        Delimiter::Bracket => write!(f, "[{}]", g.stream)?,
                        Delimiter::None => write!(f, "{}", g.stream)?,
                    }
                }
                TokenTree::Ident(id) => write!(f, "{}", id.text)?,
                TokenTree::Punct(p) => write!(f, "{}", p.ch)?,
                TokenTree::Literal(l) => write!(f, "{}", l.text)?,
            }
        }
        Ok(())
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct Group {
    pub delimiter: Delimiter,
    pub stream: TokenStream,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Delimiter {
    Parenthesis,
    Brace,
    Bracket,
    None,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Ident {
    pub text: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Punct {
    pub ch: char,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Literal {
    pub text: String,
}

pub trait Plugin {
    fn process(&self, filepath: &str, stream: TokenStream) -> TokenStream;
}

pub struct MarkdownPlugin;

impl Plugin for MarkdownPlugin {
    fn process(&self, filepath: &str, stream: TokenStream) -> TokenStream {
        if !filepath.ends_with(".md") {
            return stream;
        }

        let mut out = Vec::new();
        let mut in_beancount_block = false;

        let mut i = 0;
        let trees = &stream.trees;

        // Helper to check for ```beancount
        let is_beancount_start = |index: usize| -> bool {
            if index + 3 >= trees.len() {
                return false;
            }
            if let (
                TokenTree::Punct(Punct { ch: '`' }),
                TokenTree::Punct(Punct { ch: '`' }),
                TokenTree::Punct(Punct { ch: '`' }),
                TokenTree::Ident(Ident { text })
            ) = (&trees[index], &trees[index + 1], &trees[index + 2], &trees[index + 3]) {
                if text == "beancount" {
                    return true;
                }
            }
            false
        };

        // Helper to check for ```
        let is_block_end = |index: usize| -> bool {
            if index + 2 >= trees.len() {
                return false;
            }
            if let (
                TokenTree::Punct(Punct { ch: '`' }),
                TokenTree::Punct(Punct { ch: '`' }),
                TokenTree::Punct(Punct { ch: '`' })
            ) = (&trees[index], &trees[index + 1], &trees[index + 2]) {
                return true;
            }
            false
        };

        // If not in a beancount block, the first line should be commented out.
        if !in_beancount_block && !is_beancount_start(0) {
            out.push(TokenTree::Punct(Punct { ch: ';' }));
            out.push(TokenTree::Literal(Literal { text: " ".to_string() }));
        }

        while i < trees.len() {
            if !in_beancount_block && is_beancount_start(i) {
                in_beancount_block = true;
                // Add the start tokens
                out.push(trees[i].clone()); // `
                out.push(trees[i+1].clone()); // `
                out.push(trees[i+2].clone()); // `
                out.push(trees[i+3].clone()); // beancount
                i += 4;
                continue;
            } else if in_beancount_block && is_block_end(i) {
                in_beancount_block = false;
                // Add the end tokens
                out.push(trees[i].clone()); // `
                out.push(trees[i+1].clone()); // `
                out.push(trees[i+2].clone()); // `
                i += 3;
                continue;
            }

            let tree = &trees[i];
            out.push(tree.clone());

            if let TokenTree::Punct(Punct { ch: '\n' }) = tree {
                if !in_beancount_block && i + 1 < trees.len() {
                    if !is_beancount_start(i + 1) {
                        out.push(TokenTree::Punct(Punct { ch: ';' }));
                        out.push(TokenTree::Literal(Literal { text: " ".to_string() }));
                    }
                }
            }

            i += 1;
        }

        TokenStream::new(out)
    }
}

pub fn process_markdown_stream(filepath: &str, input: &str) -> String {
    let stream = lex_token_stream(input);
    let plugin = MarkdownPlugin;
    let modified = plugin.process(filepath, stream);
    modified.to_string()
}

pub fn lex_token_stream(input: &str) -> TokenStream {
    let mut chars = input.chars().peekable();
    let mut trees = Vec::new();

    while let Some(ch) = chars.peek().copied() {
        if ch.is_alphanumeric() || ch == '_' {
            // Ident
            let mut text = String::new();
            while let Some(&c) = chars.peek() {
                if c.is_alphanumeric() || c == '_' {
                    text.push(c);
                    chars.next();
                } else {
                    break;
                }
            }
            trees.push(TokenTree::Ident(Ident { text }));
        } else if ch.is_whitespace() {
            if ch == '\n' {
                trees.push(TokenTree::Punct(Punct { ch: '\n' }));
                chars.next();
            } else {
                // Literal (Whitespace)
                let mut text = String::new();
                while let Some(&c) = chars.peek() {
                    if c.is_whitespace() && c != '\n' {
                        text.push(c);
                        chars.next();
                    } else {
                        break;
                    }
                }
                if !text.is_empty() {
                    trees.push(TokenTree::Literal(Literal { text }));
                }
            }
        } else {
            // Punct
            trees.push(TokenTree::Punct(Punct { ch }));
            chars.next();
        }
    }

    TokenStream::new(trees)
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
