use crate::core::Transaction;
use std::collections::HashMap;
use rust_decimal::Decimal;

#[derive(Debug, Clone, PartialEq)]
pub struct AccountBalance {
    pub account: String,
    pub balances: HashMap<String, Decimal>, // Currency -> Amount
}

pub fn calculate_trial_balances(transactions: &[Transaction]) -> Vec<AccountBalance> {
    let mut balances_by_account: HashMap<String, HashMap<String, Decimal>> = HashMap::new();

    for txn in transactions {
        for posting in &txn.postings {
            if let Some(amount) = &posting.amount {
                let account_balances = balances_by_account.entry(posting.account.clone()).or_default();
                *account_balances.entry(amount.currency.clone()).or_insert(Decimal::ZERO) += amount.number;
            }
        }
    }

    let mut result = Vec::new();
    for (account, balances) in balances_by_account {
        result.push(AccountBalance {
            account,
            balances,
        });
    }

    // Sort by account name for consistent ordering
    result.sort_by(|a, b| a.account.cmp(&b.account));

    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use rust_decimal_macros::dec;
    use crate::core::{Posting, Amount};

    #[test]
    fn test_calculate_trial_balances() {
        let transactions = vec![
            Transaction {
                date: "2023-01-01".to_string(),
                flag: "*".to_string(),
                payee: None,
                narration: None,
                postings: vec![
                    Posting { flag: None, account: "Assets:Checking".to_string(), amount: Some(Amount { number: dec!(100.00), currency: "USD".to_string() }) },
                    Posting { flag: None, account: "Income:Salary".to_string(), amount: Some(Amount { number: dec!(-100.00), currency: "USD".to_string() }) },
                ],
            },
            Transaction {
                date: "2023-01-02".to_string(),
                flag: "*".to_string(),
                payee: None,
                narration: None,
                postings: vec![
                    Posting { flag: None, account: "Assets:Checking".to_string(), amount: Some(Amount { number: dec!(-20.00), currency: "USD".to_string() }) },
                    Posting { flag: None, account: "Expenses:Food".to_string(), amount: Some(Amount { number: dec!(20.00), currency: "USD".to_string() }) },
                ],
            },
        ];

        let balances = calculate_trial_balances(&transactions);
        assert_eq!(balances.len(), 3);

        let checking = balances.iter().find(|b| b.account == "Assets:Checking").unwrap();
        assert_eq!(checking.balances.get("USD").unwrap(), &dec!(80.00));

        let food = balances.iter().find(|b| b.account == "Expenses:Food").unwrap();
        assert_eq!(food.balances.get("USD").unwrap(), &dec!(20.00));

        let income = balances.iter().find(|b| b.account == "Income:Salary").unwrap();
        assert_eq!(income.balances.get("USD").unwrap(), &dec!(-100.00));
    }
}
#[derive(uniffi::Record, Debug, Clone, PartialEq)]
pub struct AccountBalanceUi {
    pub account: String,
    pub balances: std::collections::HashMap<String, String>, // Currency -> Stringified Decimal
}
