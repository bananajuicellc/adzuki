use adzuki::calculate_trial_balances;

#[test]
fn test_invalid_beancount() {
    let source = "
```beancount
2023-01-01 * \"Payee\"
  Assets:Checking  -10.00 USD
  Expenses:Food  10.00 USD
  Invalid Syntax
```
".to_string();
    calculate_trial_balances(source);
}

#[test]
fn test_missing_amount_unbalanced() {
    let source = "
```beancount
2023-01-01 * \"Payee\"
  Assets:Checking  -10.00 USD
  Expenses:Food  15.00 USD
```
".to_string();
    calculate_trial_balances(source);
}
