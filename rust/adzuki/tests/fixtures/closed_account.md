```beancount
2024-01-01 open Assets:Checking USD
2024-01-01 open Expenses:Food USD
2024-01-01 open Expenses:Dining USD

2024-01-02 * "Grocery" "Food"
  Assets:Checking -50.00 USD
  Expenses:Food 50.00 USD

2024-01-03 close Expenses:Food

2024-01-04 * "Dining out" "Food"
  Assets:Checking -20.00 USD
  Expenses:Food 20.00 USD

2024-01-05 * "Gift" "Cash"
  Assets:Cash 100.00 USD
  Income:Gifts -100.00 USD
```
