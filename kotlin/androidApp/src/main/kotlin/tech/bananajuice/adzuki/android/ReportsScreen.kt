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
import java.math.BigDecimal

data class AccountBalanceUi(
    val account: String,
    val balances: Map<String, BigDecimal>
)

fun calculateTrialBalances(directives: List<Directive>): Result<List<AccountBalanceUi>> {
    val balances = mutableMapOf<String, MutableMap<String, BigDecimal>>()

    for (dir in directives) {
        if (dir is TransactionDirective) {
            for (posting in dir.postings) {
                val account = posting.account
                val currency = posting.currency

                val amountStr = posting.amount.trim()
                if (amountStr.isEmpty()) continue // Skip empty postings logic if any

                val amount = try {
                    BigDecimal(amountStr)
                } catch (e: Exception) {
                    return Result.failure(Exception("Invalid amount '${posting.amount}' in transaction '${dir.payee}' for account '${posting.account}'"))
                }

                if (!balances.containsKey(account)) {
                    balances[account] = mutableMapOf()
                }

                val currentBalance = balances[account]?.get(currency) ?: BigDecimal.ZERO
                balances[account]?.put(currency, currentBalance + amount)
            }
        }
    }

    return Result.success(balances.map { (account, currencies) ->
        AccountBalanceUi(account, currencies)
    }.sortedBy { it.account })
}

@Composable
fun ReportsScreen(directives: List<Directive>) {
    val balancesResult = calculateTrialBalances(directives)

    if (balancesResult.isFailure) {
        val errorMsg = balancesResult.exceptionOrNull()?.message ?: "Unknown error"
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("Report Error:\n$errorMsg", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
    } else if (balancesResult.getOrNull().isNullOrEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("No balances found.")
        }
    } else {
        val currentBalances = balancesResult.getOrNull()!!
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
