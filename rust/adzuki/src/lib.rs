pub mod lexer;
pub mod parser;
pub mod ast;

uniffi::setup_scaffolding!();

pub fn parse_markdown(source: &str) -> parser::Cst<'_> {
    let mut diags = vec![];
    let parser = parser::Parser::new(source, &mut diags);
    parser.parse(&mut diags)
}

#[uniffi::export]
pub fn parse_to_tree(source: String) -> ast::ParseTree {
    let cst = parse_markdown(&source);
    let mut nodes = vec![];

    let root_ref = parser::NodeRef::ROOT;
    if let parser::Node::Rule(parser::Rule::Markdown, _) = cst.get(root_ref) {
        for block_ref in cst.children(root_ref) {
            if let parser::Node::Rule(parser::Rule::Block, _) = cst.get(block_ref) {
                // A Block can be Heading, Paragraph, CodeBlock, etc.
                let mut block_children = cst.children(block_ref);
                if let Some(child_ref) = block_children.next() {
                    match cst.get(child_ref) {
                        parser::Node::Rule(parser::Rule::Heading, _) => {
                            let mut level = 1;
                            let mut text = String::new();
                            for h_child in cst.children(child_ref) {
                                match cst.get(h_child) {
                                    parser::Node::Token(lexer::Token::Heading1, _) => level = 1,
                                    parser::Node::Token(lexer::Token::Heading2, _) => level = 2,
                                    parser::Node::Token(lexer::Token::Heading3, _) => level = 3,
                                    parser::Node::Token(lexer::Token::Heading4, _) => level = 4,
                                    parser::Node::Token(lexer::Token::Heading5, _) => level = 5,
                                    parser::Node::Token(lexer::Token::Heading6, _) => level = 6,
                                    parser::Node::Rule(parser::Rule::TextContent, _) => {
                                        let span = cst.span(h_child);
                                        text.push_str(&source[span]);
                                    }
                                    _ => {}
                                }
                            }
                            nodes.push(ast::AstNode::Heading { level, content: text });
                        }
                        parser::Node::Rule(parser::Rule::Paragraph, _) => {
                            let mut text = String::new();
                            for p_child in cst.children(child_ref) {
                                if let parser::Node::Rule(parser::Rule::TextContent, _) = cst.get(p_child) {
                                    let span = cst.span(p_child);
                                    text.push_str(&source[span]);
                                }
                            }
                            nodes.push(ast::AstNode::Paragraph { content: text });
                        }
                        parser::Node::Token(lexer::Token::CodeBlock, _) => {
                            let span = cst.span(child_ref);
                            nodes.push(ast::AstNode::CodeBlock { content: source[span].to_string() });
                        }
                        _ => {}
                    }
                }
            }
        }
    }

    ast::ParseTree { nodes }
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
