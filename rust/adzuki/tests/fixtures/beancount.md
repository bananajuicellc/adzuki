# Beancount Syntax Test

This is a regular markdown paragraph.

```beancount
option "title" "Test Book"
2024-01-01 open Assets:Checking USD
2024-01-02 * "Grocery" "Food"
  Assets:Checking -50.00 USD
  Expenses:Food 50.00 USD
```
