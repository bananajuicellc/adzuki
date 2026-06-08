pub mod lexer;
pub mod parser;
pub mod beancount_parser;
pub mod ast;
pub mod core;
pub mod validator;

use crate::lexer::{lex_core, lex_beancount, CoreToken, SpannedToken};

uniffi::setup_scaffolding!();

pub fn parse_markdown<'a>(source: &'a str, tokens: &'a [SpannedToken<CoreToken>]) -> Vec<parser::MdNode> {
    parser::parse_markdown(source, tokens)
}

#[uniffi::export]
pub fn parse_to_tree(source: String) -> ast::ParseTree {
    let tokens = lex_beancount(&source);
    let (wrappers, _errors) = beancount_parser::parse_beancount(&source, &tokens);

    ast::ParseTree { nodes: wrappers }
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
pub mod reports;

#[uniffi::export]
pub fn calculate_trial_balances(source: String) -> Vec<reports::AccountBalanceUi> {
    let tree = parse_to_tree(source);
    let mut core_transactions = Vec::new();

    for wrapper in tree.nodes {
        if let ast::BeancountNode::Transaction { date, flag, payee, narration, postings } = wrapper.directive {
            if let Ok(txn) = core::Transaction::try_from_ast(&date, &flag, &payee, &narration, &postings) {
                core_transactions.push(txn);
            }
        }
    }

    let balances = reports::calculate_trial_balances(&core_transactions);

    balances.into_iter().map(|b| {
        let mut balance_map = std::collections::HashMap::new();
        for (k, v) in b.balances {
            balance_map.insert(k, v.to_string());
        }
        reports::AccountBalanceUi {
            account: b.account,
            balances: balance_map,
        }
    }).collect()
}
