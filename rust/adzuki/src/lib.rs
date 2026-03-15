pub mod lexer;
pub mod parser;
pub mod beancount_parser;
pub mod plugin;
pub mod ast;
pub mod core;
pub mod validator;

use crate::plugin::{Plugin, MarkdownPlugin};
use crate::lexer::{lex_core, lex_beancount, CoreToken, SpannedToken};

uniffi::setup_scaffolding!();

pub fn parse_markdown<'a>(source: &'a str, tokens: &'a [SpannedToken<CoreToken>]) -> Vec<parser::MdNode> {
    parser::parse_markdown(source, tokens)
}

#[uniffi::export]
pub fn parse_to_tree(source: String) -> ast::ParseTree {
    let tokens = lex_core(&source);

    // We only apply the MarkdownPlugin on the generated code block texts internally
    // The Kotlin UI AST (parse_to_tree) SHOULD NOT run the markdown plugin to comment things out.
    // The markdown plugin is only for creating Beancount files.

    let md_nodes = parse_markdown(&source, &tokens);
    let mut ast_nodes = vec![];

    for node in md_nodes {
        match node {
            parser::MdNode::Heading { level, content, span } => {
                ast_nodes.push(ast::AstNode::Heading {
                    level,
                    content,
                    span: ast::Span { start: span.start as u32, end: span.end as u32 }
                });
            }
            parser::MdNode::Paragraph { content, span } => {
                ast_nodes.push(ast::AstNode::Paragraph {
                    content,
                    span: ast::Span { start: span.start as u32, end: span.end as u32 }
                });
            }
            parser::MdNode::CodeBlock { language, tokens: block_tokens, span } => {
                if let Some(lang) = &language {
                    if lang == "beancount" {
                        // Reconstruct block source to feed to the Beancount lexer
                        let mut block_content = String::new();
                        for (tok, b_span) in block_tokens {
                            if b_span.start == 0 && b_span.end == 0 {
                                if tok == CoreToken::PunctOrOther {
                                    block_content.push(';');
                                } else if tok == CoreToken::Whitespace {
                                    block_content.push(' ');
                                }
                            } else {
                                block_content.push_str(&source[b_span.clone()]);
                            }
                        }

                        let bc_tokens = lex_beancount(&block_content);
                        let (beancount_nodes, _errors) = beancount_parser::parse_beancount(&block_content, &bc_tokens);
                        ast_nodes.push(ast::AstNode::Beancount {
                            nodes: beancount_nodes,
                            span: ast::Span { start: span.start as u32, end: span.end as u32 }
                        });
                        continue;
                    }
                }

                // Reconstruct content of code block
                let mut content = String::new();
                for (tok, b_span) in block_tokens {
                    if b_span.start == 0 && b_span.end == 0 {
                        if tok == CoreToken::PunctOrOther {
                            content.push(';');
                        } else if tok == CoreToken::Whitespace {
                            content.push(' ');
                        }
                    } else {
                        content.push_str(&source[b_span.clone()]);
                    }
                }

                ast_nodes.push(ast::AstNode::CodeBlock {
                    content: content.trim().to_string(),
                    span: ast::Span { start: span.start as u32, end: span.end as u32 }
                });
            }
        }
    }

    ast::ParseTree { nodes: ast_nodes }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_simple_markdown() {
        let source = "# Heading\nParagraph text.\n";
        let tree = parse_to_tree(source.to_string());
        println!("{:?}", tree);
    }
}
