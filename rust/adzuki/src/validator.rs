use crate::ast::BeancountNode;
use crate::beancount_parser::BeancountParseError;
use crate::core::Transaction;

pub fn validate_beancount(nodes: &[BeancountNode]) -> Vec<BeancountParseError> {
    let mut errors = Vec::new();

    for node in nodes {
        if let BeancountNode::Transaction { date, flag, payee, narration, postings } = node {
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
    }

    errors
}
