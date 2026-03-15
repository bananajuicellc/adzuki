# Balancing error tests

```beancount
2023-01-01 * "Test" "Unbalanced transaction"
  Assets:Checking -10.00 USD
  Expenses:Food 15.00 USD
```

```beancount
2023-01-01 * "Test" "Multiple missing amounts"
  Assets:Checking -10.00 USD
  Expenses:Food
  Expenses:Drink
```
