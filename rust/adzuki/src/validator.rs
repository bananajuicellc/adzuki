use std::collections::HashSet;

use crate::ast::BeancountNode;
use crate::beancount_parser::BeancountParseError;
use crate::core::Transaction;

pub fn validate_beancount(nodes: &[BeancountNode]) -> Vec<BeancountParseError> {
    let mut errors = Vec::new();
    let mut open_accounts = HashSet::new();
    let mut closed_accounts = HashSet::new();

    let mut sorted_nodes: Vec<&BeancountNode> = nodes.iter().collect();
    sorted_nodes.sort_by(|a, b| {
        let date_a: &str = match a {
            BeancountNode::OptionDirective { .. } => "",
            BeancountNode::OpenDirective { date, .. } => date.as_str(),
            BeancountNode::CloseDirective { date, .. } => date.as_str(),
            BeancountNode::Transaction { date, .. } => date.as_str(),
        };
        let date_b: &str = match b {
            BeancountNode::OptionDirective { .. } => "",
            BeancountNode::OpenDirective { date, .. } => date.as_str(),
            BeancountNode::CloseDirective { date, .. } => date.as_str(),
            BeancountNode::Transaction { date, .. } => date.as_str(),
        };
        date_a.cmp(date_b)
    });

    for node in sorted_nodes {
        match node {
            BeancountNode::OpenDirective { date: _, account, .. } => {
                open_accounts.insert(account.clone());
                closed_accounts.remove(account);
            }
            BeancountNode::CloseDirective { date: _, account } => {
                closed_accounts.insert(account.clone());
                open_accounts.remove(account);
            }
            BeancountNode::Transaction { date, flag, payee, narration, postings } => {
                for posting in postings {
                    if closed_accounts.contains(&posting.account) {
                        errors.push(BeancountParseError {
                            span: 0..0,
                            message: format!(
                                "Validation error for Transaction on {}: Account {} is closed",
                                date, posting.account
                            ),
                        });
                    } else if !open_accounts.contains(&posting.account) {
                        errors.push(BeancountParseError {
                            span: 0..0,
                            message: format!(
                                "Validation error for Transaction on {}: Account {} is not open",
                                date, posting.account
                            ),
                        });
                    }
                }

                if let Err(err) = Transaction::try_from_ast(date, flag, payee, narration, postings) {
                // Since ast::BeancountNode currently does not store its own byte span,
                // we synthesize a 0..0 span or attempt to associate it with the transaction.
                // For bean-check, 0..0 might mean it prints at line 1, col 1, but we include
                // the transaction context in the message.

                let payee_str = payee.as_deref().unwrap_or("");
                let narration_str = narration.as_deref().unwrap_or("");

                let context = format!("Transaction on {} {} {}", date, payee_str, narration_str).trim().to_string();

                errors.push(BeancountParseError {
                    span: 0..0,
                    message: format!("Validation error for {}: {}", context, err.message),
                });
            }
            }
            _ => {}
        }
    }

    errors
}
