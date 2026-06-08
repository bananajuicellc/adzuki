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
            if let Some(amount) = &p.amount {
                *sums_by_currency.entry(amount.currency.clone()).or_insert(Decimal::ZERO) += amount.number;
            } else if missing_amount_idx.is_none() {
                missing_amount_idx = Some(i);
            } else {
                return Err(BalancingError {
                    message: "Transaction has more than one posting with missing amount".to_string(),
                });
            }
        }

        if let Some(idx) = missing_amount_idx {
            let missing_posting = postings.remove(idx);
            let mut added = false;

            // Sort currencies to ensure deterministic ordering of inserted postings
            let mut currencies: Vec<_> = sums_by_currency.keys().cloned().collect();
            currencies.sort();

            let mut insert_idx = idx;
            for currency in &currencies {
                let sum = sums_by_currency[currency];
                if !sum.is_zero() {
                    let mut new_posting = missing_posting.clone();
                    new_posting.amount = Some(Amount {
                        number: -sum,
                        currency: currency.clone(),
                    });
                    postings.insert(insert_idx, new_posting);
                    insert_idx += 1;
                    added = true;
                }
            }

            if !added {
                if let Some(currency) = currencies.first() {
                    let mut new_posting = missing_posting.clone();
                    new_posting.amount = Some(Amount {
                        number: Decimal::ZERO,
                        currency: currency.clone(),
                    });
                    postings.insert(idx, new_posting);
                } else {
                    return Err(BalancingError {
                        message: "Cannot infer missing amount: no currencies to balance against".to_string(),
                    });
                }
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

    #[test]
    fn test_infer_missing_amount_multiple_currencies() {
        let ast_postings = vec![
            ast::Posting {
                flag: None,
                account: "Assets:Checking".to_string(),
                amount: Some(ast::Amount { number: "-10.00".to_string(), currency: "USD".to_string() }),
            },
            ast::Posting {
                flag: None,
                account: "Assets:Checking".to_string(),
                amount: Some(ast::Amount { number: "-20.00".to_string(), currency: "EUR".to_string() }),
            },
            ast::Posting {
                flag: None,
                account: "Expenses:Food".to_string(),
                amount: None,
            },
        ];

        let txn = Transaction::try_from_ast("2023-01-01", "*", &None, &None, &ast_postings).unwrap();
        assert_eq!(txn.postings.len(), 4);

        let eur_posting = txn.postings.iter().find(|p| p.amount.as_ref().unwrap().currency == "EUR" && p.amount.as_ref().unwrap().number.is_sign_positive()).unwrap();
        let usd_posting = txn.postings.iter().find(|p| p.amount.as_ref().unwrap().currency == "USD" && p.amount.as_ref().unwrap().number.is_sign_positive()).unwrap();

        assert_eq!(eur_posting.amount.as_ref().unwrap().number, rust_decimal_macros::dec!(20.00));
        assert_eq!(usd_posting.amount.as_ref().unwrap().number, rust_decimal_macros::dec!(10.00));
    }
