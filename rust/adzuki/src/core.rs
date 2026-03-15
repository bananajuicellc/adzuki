use crate::ast;
use rust_decimal::Decimal;
use std::collections::HashMap;
use std::str::FromStr;

#[derive(Debug, Clone, PartialEq)]
pub struct Amount {
    pub number: Decimal,
    pub currency: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Posting {
    pub flag: Option<String>,
    pub account: String,
    pub amount: Option<Amount>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Transaction {
    pub date: String,
    pub flag: String,
    pub payee: Option<String>,
    pub narration: Option<String>,
    pub postings: Vec<Posting>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct BalancingError {
    pub message: String,
}

impl Transaction {
    pub fn try_from_ast(
        date: &str,
        flag: &str,
        payee: &Option<String>,
        narration: &Option<String>,
        ast_postings: &[ast::Posting],
    ) -> Result<Self, BalancingError> {
        let mut postings = Vec::new();

        // 1. Convert ast::Posting to core::Posting
        for p in ast_postings {
            let amount = if let Some(a) = &p.amount {
                let number = Decimal::from_str(&a.number).map_err(|e| BalancingError {
                    message: format!("Invalid decimal number '{}': {}", a.number, e),
                })?;
                Some(Amount {
                    number,
                    currency: a.currency.clone(),
                })
            } else {
                None
            };

            postings.push(Posting {
                flag: p.flag.clone(),
                account: p.account.clone(),
                amount,
            });
        }

        // 2. Perform balancing logic
        let mut missing_amount_idx = None;
        let mut sums_by_currency: HashMap<String, Decimal> = HashMap::new();

        for (i, p) in postings.iter().enumerate() {
            match &p.amount {
                Some(amount) => {
                    *sums_by_currency.entry(amount.currency.clone()).or_insert(Decimal::ZERO) += amount.number;
                }
                None => {
                    if missing_amount_idx.is_some() {
                        return Err(BalancingError {
                            message: "Transaction has more than one posting with missing amount".to_string(),
                        });
                    }
                    missing_amount_idx = Some(i);
                }
            }
        }

        if let Some(idx) = missing_amount_idx {
            // There's exactly one missing amount.
            // We need to infer it. Beancount allows inference as long as there is only one non-zero sum across all currencies.
            // Check if there is exactly 1 non-zero sum.
            let non_zero_sums: Vec<(String, Decimal)> = sums_by_currency.clone().into_iter().filter(|(_, s)| !s.is_zero()).collect();

            if non_zero_sums.len() > 1 {
                return Err(BalancingError {
                    message: "Cannot infer missing amount: multiple currencies have non-zero sums".to_string(),
                });
            } else if non_zero_sums.is_empty() {
                // All currencies are perfectly balanced, so the missing amount must be 0 in the "dominant" currency
                // We'll just pick the first currency we saw, or if there were none, error out.
                if sums_by_currency.is_empty() {
                    return Err(BalancingError {
                        message: "Cannot infer missing amount: no other amounts in transaction to balance against".to_string(),
                    });
                }
                let currency = sums_by_currency.into_iter().next().unwrap().0;
                postings[idx].amount = Some(Amount {
                    number: Decimal::ZERO,
                    currency,
                });
            } else {
                let (currency, sum) = non_zero_sums.into_iter().next().unwrap();
                // The missing amount must be the negation of the sum to make the total 0.
                postings[idx].amount = Some(Amount {
                    number: -sum,
                    currency,
                });
            }
        } else {
            // All postings have amounts. Verify they sum to 0 for each currency.
            for (currency, sum) in sums_by_currency {
                if !sum.is_zero() {
                    return Err(BalancingError {
                        message: format!("Transaction does not balance: sum of {} is {}", currency, sum),
                    });
                }
            }
        }

        Ok(Transaction {
            date: date.to_string(),
            flag: flag.to_string(),
            payee: payee.clone(),
            narration: narration.clone(),
            postings,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rust_decimal_macros::dec;

    #[test]
    fn test_balanced_transaction() {
        let ast_postings = vec![
            ast::Posting {
                flag: None,
                account: "Assets:Checking".to_string(),
                amount: Some(ast::Amount { number: "-10.00".to_string(), currency: "USD".to_string() }),
            },
            ast::Posting {
                flag: None,
                account: "Expenses:Food".to_string(),
                amount: Some(ast::Amount { number: "10.00".to_string(), currency: "USD".to_string() }),
            },
        ];

        let txn = Transaction::try_from_ast("2023-01-01", "*", &None, &None, &ast_postings).unwrap();
        assert_eq!(txn.postings.len(), 2);
    }

    #[test]
    fn test_unbalanced_transaction_errors() {
        let ast_postings = vec![
            ast::Posting {
                flag: None,
                account: "Assets:Checking".to_string(),
                amount: Some(ast::Amount { number: "-10.00".to_string(), currency: "USD".to_string() }),
            },
            ast::Posting {
                flag: None,
                account: "Expenses:Food".to_string(),
                amount: Some(ast::Amount { number: "15.00".to_string(), currency: "USD".to_string() }),
            },
        ];

        let err = Transaction::try_from_ast("2023-01-01", "*", &None, &None, &ast_postings).unwrap_err();
        assert!(err.message.contains("Transaction does not balance"));
    }

    #[test]
    fn test_infer_missing_amount() {
        let ast_postings = vec![
            ast::Posting {
                flag: None,
                account: "Assets:Checking".to_string(),
                amount: Some(ast::Amount { number: "-10.00".to_string(), currency: "USD".to_string() }),
            },
            ast::Posting {
                flag: None,
                account: "Expenses:Food".to_string(),
                amount: None,
            },
        ];

        let txn = Transaction::try_from_ast("2023-01-01", "*", &None, &None, &ast_postings).unwrap();
        assert_eq!(txn.postings.len(), 2);
        assert_eq!(txn.postings[1].amount.as_ref().unwrap().number, dec!(10.00));
        assert_eq!(txn.postings[1].amount.as_ref().unwrap().currency, "USD");
    }

    #[test]
    fn test_multiple_missing_amounts_errors() {
        let ast_postings = vec![
            ast::Posting {
                flag: None,
                account: "Assets:Checking".to_string(),
                amount: None,
            },
            ast::Posting {
                flag: None,
                account: "Expenses:Food".to_string(),
                amount: None,
            },
        ];

        let err = Transaction::try_from_ast("2023-01-01", "*", &None, &None, &ast_postings).unwrap_err();
        assert!(err.message.contains("more than one posting with missing amount"));
    }
}
