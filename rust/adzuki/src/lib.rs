pub mod lexer;
pub mod parser;
pub mod beancount_parser;
pub mod plugin;
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
                        parser::Node::Token(lexer::Token::BeancountBlock, _) => {
                            let span = cst.span(child_ref);
                            let text = &source[span];

                            // Remove ```beancount prefix and ``` suffix
                            let mut block_content = text.strip_prefix("```beancount\n").unwrap_or(text);
                            block_content = block_content.strip_suffix("```").unwrap_or(block_content);

                            let mut b_diags = vec![];
                            let b_parser = beancount_parser::Parser::new(block_content, &mut b_diags);
                            let b_cst = b_parser.parse(&mut b_diags);

                            let mut beancount_nodes = vec![];
                            let b_root = beancount_parser::NodeRef::ROOT;

                            if let beancount_parser::Node::Rule(beancount_parser::Rule::Beancount, _) = b_cst.get(b_root) {
                                for b_block_ref in b_cst.children(b_root) {
                                    if let beancount_parser::Node::Rule(beancount_parser::Rule::Block, _) = b_cst.get(b_block_ref) {
                                        if let Some(b_child_ref) = b_cst.children(b_block_ref).next() {
                                            match b_cst.get(b_child_ref) {
                                                beancount_parser::Node::Rule(beancount_parser::Rule::OptionDirective, _) => {
                                                    let mut strings = vec![];
                                                    for o_child in b_cst.children(b_child_ref) {
                                                        if let beancount_parser::Node::Token(lexer::BeancountToken::StringLiteral, _) = b_cst.get(o_child) {
                                                            let span = b_cst.span(o_child);
                                                            strings.push(block_content[span].trim_matches('"').to_string());
                                                        }
                                                    }
                                                    if strings.len() == 2 {
                                                        beancount_nodes.push(ast::BeancountNode::OptionDirective {
                                                            name: strings[0].clone(),
                                                            value: strings[1].clone(),
                                                        });
                                                    }
                                                }
                                                beancount_parser::Node::Rule(beancount_parser::Rule::OpenDirective, _) => {
                                                    let mut date = String::new();
                                                    let mut account = String::new();
                                                    let mut currencies = vec![];
                                                    let mut booking_method = None;

                                                    for o_child in b_cst.children(b_child_ref) {
                                                        match b_cst.get(o_child) {
                                                            beancount_parser::Node::Token(lexer::BeancountToken::Date, _) => {
                                                                date = block_content[b_cst.span(o_child)].to_string();
                                                            }
                                                            beancount_parser::Node::Token(lexer::BeancountToken::Account, _) => {
                                                                account = block_content[b_cst.span(o_child)].to_string();
                                                            }
                                                            beancount_parser::Node::Rule(beancount_parser::Rule::CurrencyList, _) => {
                                                                for c_child in b_cst.children(o_child) {
                                                                    if let beancount_parser::Node::Token(lexer::BeancountToken::Currency, _) = b_cst.get(c_child) {
                                                                        currencies.push(block_content[b_cst.span(c_child)].to_string());
                                                                    }
                                                                }
                                                            }
                                                            beancount_parser::Node::Token(lexer::BeancountToken::StringLiteral, _) => {
                                                                booking_method = Some(block_content[b_cst.span(o_child)].trim_matches('"').to_string());
                                                            }
                                                            _ => {}
                                                        }
                                                    }
                                                    beancount_nodes.push(ast::BeancountNode::OpenDirective { date, account, currencies, booking_method });
                                                }
                                                beancount_parser::Node::Rule(beancount_parser::Rule::Transaction, _) => {
                                                    let mut date = String::new();
                                                    let mut flag = String::new();
                                                    let mut strings = vec![];
                                                    let mut postings = vec![];

                                                    for t_child in b_cst.children(b_child_ref) {
                                                        match b_cst.get(t_child) {
                                                            beancount_parser::Node::Token(lexer::BeancountToken::Date, _) => {
                                                                date = block_content[b_cst.span(t_child)].to_string();
                                                            }
                                                            beancount_parser::Node::Token(lexer::BeancountToken::TxnFlag, _) => {
                                                                flag = block_content[b_cst.span(t_child)].to_string();
                                                            }
                                                            beancount_parser::Node::Token(lexer::BeancountToken::StringLiteral, _) => {
                                                                strings.push(block_content[b_cst.span(t_child)].trim_matches('"').to_string());
                                                            }
                                                            beancount_parser::Node::Rule(beancount_parser::Rule::PostingList, _) => {
                                                                for p_child in b_cst.children(t_child) {
                                                                    if let beancount_parser::Node::Rule(beancount_parser::Rule::Posting, _) = b_cst.get(p_child) {
                                                                        let mut p_flag = None;
                                                                        let mut p_account = String::new();
                                                                        let mut p_amount = None;

                                                                        for pp_child in b_cst.children(p_child) {
                                                                            match b_cst.get(pp_child) {
                                                                                beancount_parser::Node::Token(lexer::BeancountToken::TxnFlag, _) => {
                                                                                    p_flag = Some(block_content[b_cst.span(pp_child)].to_string());
                                                                                }
                                                                                beancount_parser::Node::Token(lexer::BeancountToken::Account, _) => {
                                                                                    p_account = block_content[b_cst.span(pp_child)].to_string();
                                                                                }
                                                                                beancount_parser::Node::Rule(beancount_parser::Rule::Amount, _) => {
                                                                                    let mut number = String::new();
                                                                                    let mut currency = String::new();
                                                                                    for a_child in b_cst.children(pp_child) {
                                                                                        match b_cst.get(a_child) {
                                                                                            beancount_parser::Node::Token(lexer::BeancountToken::Number, _) => number = block_content[b_cst.span(a_child)].to_string(),
                                                                                            beancount_parser::Node::Token(lexer::BeancountToken::Currency, _) => currency = block_content[b_cst.span(a_child)].to_string(),
                                                                                            _ => {}
                                                                                        }
                                                                                    }
                                                                                    p_amount = Some(ast::Amount { number, currency });
                                                                                }
                                                                                _ => {}
                                                                            }
                                                                        }
                                                                        postings.push(ast::Posting { flag: p_flag, account: p_account, amount: p_amount });
                                                                    }
                                                                }
                                                            }
                                                            _ => {}
                                                        }
                                                    }

                                                    let payee = if strings.len() > 1 { Some(strings[0].clone()) } else { None };
                                                    let narration = if strings.len() > 1 { Some(strings[1].clone()) } else if strings.len() == 1 { Some(strings[0].clone()) } else { None };

                                                    beancount_nodes.push(ast::BeancountNode::Transaction { date, flag, payee, narration, postings });
                                                }
                                                _ => {}
                                            }
                                        }
                                    }
                                }
                            }
                            nodes.push(ast::AstNode::Beancount { nodes: beancount_nodes });
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
