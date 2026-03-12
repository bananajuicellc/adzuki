pub mod lexer;
pub mod parser;
pub mod beancount_parser;
pub mod plugin;
pub mod ast;

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
            parser::MdNode::Heading { level, content } => {
                ast_nodes.push(ast::AstNode::Heading { level, content });
            }
            parser::MdNode::Paragraph { content } => {
                ast_nodes.push(ast::AstNode::Paragraph { content });
            }
            parser::MdNode::CodeBlock { language, tokens: block_tokens } => {
                if let Some(lang) = &language {
                    if lang == "beancount" {
                        // Reconstruct block source to feed to the Beancount lexer
                        let mut block_content = String::new();
                        for (tok, span) in block_tokens {
                            if span.start == 0 && span.end == 0 {
                                if tok == CoreToken::PunctOrOther {
                                    block_content.push(';');
                                } else if tok == CoreToken::Whitespace {
                                    block_content.push(' ');
                                }
                            } else {
                                block_content.push_str(&source[span.clone()]);
                            }
                        }

                        let bc_tokens = lex_beancount(&block_content);
                        let beancount_nodes = beancount_parser::parse_beancount(&block_content, &bc_tokens);
                        ast_nodes.push(ast::AstNode::Beancount { nodes: beancount_nodes });
                        continue;
                    }
                }

                // Reconstruct content of code block
                let mut content = String::new();
                for (tok, span) in block_tokens {
                    if span.start == 0 && span.end == 0 {
                        if tok == CoreToken::PunctOrOther {
                            content.push(';');
                        } else if tok == CoreToken::Whitespace {
                            content.push(' ');
                        }
                    } else {
                        content.push_str(&source[span.clone()]);
                    }
                }

                ast_nodes.push(ast::AstNode::CodeBlock { content: content.trim().to_string() });
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
