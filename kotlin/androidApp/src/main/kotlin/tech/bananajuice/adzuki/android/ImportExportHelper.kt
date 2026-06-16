package tech.bananajuice.adzuki.android

import tech.bananajuice.adzuki.shared.automerge.*
import uniffi.adzuki.*

fun importFromBeancount(beancountText: String): AutomergeDocument {
    val tree = parseToTree(beancountText)
    val doc = AutomergeDocument()

    var nextId = 0L
    for (nodeWrapper in tree.nodes) {
        val directive = nodeWrapper.directive
        if (directive is BeancountNode.OpenDirective) {
            doc.addAccount(
                AccountDirective(
                    id = nextId++,
                    date = directive.date,
                    name = directive.account,
                    constraintCurrencies = directive.currencies
                )
            )
        } else if (directive is BeancountNode.OptionDirective) {
            doc.addOption(
                OptionDirective(
                    id = nextId++,
                    name = directive.name,
                    value = directive.value
                )
            )
        } else if (directive is BeancountNode.CloseDirective) {
            doc.addCloseDirective(
                CloseDirective(
                    id = nextId++,
                    date = directive.date,
                    account = directive.account
                )
            )
        } else if (directive is BeancountNode.Transaction) {
            val postings = directive.postings.map { p: uniffi.adzuki.Posting ->
                tech.bananajuice.adzuki.shared.automerge.Posting(
                    account = p.account,
                    amount = p.amount?.number ?: "",
                    currency = p.amount?.currency ?: ""
                )
            }
            doc.addTransaction(
                TransactionDirective(
                    id = nextId++,
                    date = directive.date,
                    payee = directive.payee ?: "",
                    memo = directive.narration ?: "",
                    postings = postings
                )
            )
        }
    }
    return doc
}

fun exportToBeancount(directives: List<Directive>): String {
    val sb = StringBuilder()
    directives.forEach { dir ->
        if (dir is AccountDirective) {
            sb.append("${dir.date} open ${dir.name}")
            if (dir.constraintCurrencies.isNotEmpty()) {
                sb.append(" ${dir.constraintCurrencies.joinToString(",")}")
            }
            sb.append("\n\n")
        } else if (dir is OptionDirective) {
            val safeName = dir.name.replace("\"", "\\\"")
            val safeValue = dir.value.replace("\"", "\\\"")
            sb.append("option \"$safeName\" \"$safeValue\"\n\n")
        } else if (dir is CloseDirective) {
            sb.append("${dir.date} close ${dir.account}\n\n")
        } else if (dir is TransactionDirective) {
            val safePayee = dir.payee.replace("\"", "\\\"")
            val safeMemo = dir.memo.replace("\"", "\\\"")
            sb.append("${dir.date} * \"${safePayee}\" \"${safeMemo}\"\n")
            dir.postings.forEach { p ->
                sb.append("  ${p.account}")
                if (p.amount.isNotEmpty()) {
                    sb.append(" ${p.amount}")
                    if (p.currency.isNotEmpty()) {
                        sb.append(" ${p.currency}")
                    }
                }
                sb.append("\n")
            }
            sb.append("\n")
        }
    }
    return sb.toString()
}
