package tech.bananajuice.adzuki.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tech.bananajuice.adzuki.shared.automerge.Directive
import tech.bananajuice.adzuki.shared.automerge.TransactionDirective

data class AccountBalanceUi(
    val account: String,
    val balances: Map<String, Double>
)

fun calculateTrialBalances(directives: List<Directive>): List<AccountBalanceUi> {
    val balances = mutableMapOf<String, MutableMap<String, Double>>()

    for (dir in directives) {
        if (dir is TransactionDirective) {
            for (posting in dir.postings) {
                val account = posting.account
                val currency = posting.currency
                val amount = posting.amount.toDoubleOrNull() ?: 0.0

                if (!balances.containsKey(account)) {
                    balances[account] = mutableMapOf()
                }

                val currentBalance = balances[account]?.get(currency) ?: 0.0
                balances[account]?.put(currency, currentBalance + amount)
            }
        }
    }

    return balances.map { (account, currencies) ->
        AccountBalanceUi(account, currencies)
    }.sortedBy { it.account }
}

@Composable
fun ReportsScreen(directives: List<Directive>) {
    val currentBalances = calculateTrialBalances(directives)

    if (currentBalances.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No balances found.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(currentBalances) { balance ->
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(text = balance.account, fontWeight = FontWeight.Bold)
                    balance.balances.forEach { (currency, amount) ->
                        Text(text = "$amount $currency")
                    }
                }
            }
        }
    }
}
