package tech.bananajuice.adzuki.shared.automerge

import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.AmValue.List as AmList
import org.automerge.AmValue.Map as AmMap
import org.automerge.AmValue.Str as AmStr

sealed interface Directive

data class AccountDirective(
    val date: String,
    val name: String,
    val constraintCurrencies: List<String>
) : Directive

data class Posting(
    val account: String,
    val amount: String,
    val currency: String
)

data class TransactionDirective(
    val date: String,
    val payee: String,
    val memo: String,
    val postings: List<Posting>
) : Directive

class AutomergeDocument {
    private var doc: Document

    constructor() {
        doc = Document()
    }

    constructor(bytes: ByteArray) {
        doc = if (bytes.isEmpty()) Document() else Document.load(bytes)
    }

    fun save(): ByteArray {
        return doc.save()
    }

    private fun getLength(tx: org.automerge.Transaction, listId: ObjectId): Long {
        return tx.length(listId)
    }

    fun addAccount(account: AccountDirective) {
        doc.startTransaction().use { tx ->
            val root = ObjectId.ROOT

            val directivesOpt = tx.get(root, "directives")
            val directives = if (directivesOpt.isPresent) {
                (directivesOpt.get() as AmValue.List).id
            } else {
                tx.set(root, "directives", ObjectType.LIST)
            }

            val len = getLength(tx, directives)

            val accountDir = tx.insert(directives, len, ObjectType.MAP)
            tx.set(accountDir, "type", "Account")
            tx.set(accountDir, "name", account.name)
            tx.set(accountDir, "date", account.date)

            val currenciesList = tx.set(accountDir, "constraint_currencies", ObjectType.LIST)
            account.constraintCurrencies.forEachIndexed { i, c ->
                tx.insert(currenciesList, i.toLong(), c)
            }

            tx.commit()
        }
    }

    fun addTransaction(transaction: TransactionDirective) {
        doc.startTransaction().use { tx ->
            val root = ObjectId.ROOT

            val directivesOpt = tx.get(root, "directives")
            val directives = if (directivesOpt.isPresent) {
                (directivesOpt.get() as AmValue.List).id
            } else {
                tx.set(root, "directives", ObjectType.LIST)
            }

            val len = getLength(tx, directives)

            val txnDir = tx.insert(directives, len, ObjectType.MAP)
            tx.set(txnDir, "type", "Transaction")
            tx.set(txnDir, "date", transaction.date)
            tx.set(txnDir, "payee", transaction.payee)
            tx.set(txnDir, "memo", transaction.memo)

            val postingsList = tx.set(txnDir, "postings", ObjectType.LIST)
            transaction.postings.forEachIndexed { i, p ->
                val postingObj = tx.insert(postingsList, i.toLong(), ObjectType.MAP)
                tx.set(postingObj, "account", p.account)
                tx.set(postingObj, "amount", p.amount)
                tx.set(postingObj, "currency", p.currency)
            }

            tx.commit()
        }
    }

    fun getDirectives(): List<Directive> {
        val result = mutableListOf<Directive>()
        doc.startTransaction().use { tx ->
            val root = ObjectId.ROOT
            val directivesOpt = tx.get(root, "directives")
            if (!directivesOpt.isPresent) return emptyList()

            val directives = (directivesOpt.get() as AmValue.List).id
            val len = getLength(tx, directives)
            for (i in 0 until len) {
                val dirOpt = tx.get(directives, i)
                if (dirOpt.isPresent) {
                    val dirObj = (dirOpt.get() as AmValue.Map).id
                    val typeOpt = tx.get(dirObj, "type")
                    if (typeOpt.isPresent) {
                        val typeVal = typeOpt.get()
                        if (typeVal is AmValue.Str) {
                            val type = typeVal.value
                            if (type == "Account") {
                                val name = tx.get(dirObj, "name").map { (it as AmValue.Str).value }.orElse("")
                                val date = tx.get(dirObj, "date").map { (it as AmValue.Str).value }.orElse("")

                                val currencies = mutableListOf<String>()
                                val currListOpt = tx.get(dirObj, "constraint_currencies")
                                if (currListOpt.isPresent) {
                                    val currListObj = (currListOpt.get() as AmValue.List).id
                                    val clen = getLength(tx, currListObj)
                                    for (j in 0 until clen) {
                                        val currOpt = tx.get(currListObj, j)
                                        if (currOpt.isPresent) {
                                            currencies.add((currOpt.get() as AmValue.Str).value)
                                        }
                                    }
                                }
                                result.add(AccountDirective(date, name, currencies))
                            } else if (type == "Transaction") {
                                val date = tx.get(dirObj, "date").map { (it as AmValue.Str).value }.orElse("")
                                val payee = tx.get(dirObj, "payee").map { (it as AmValue.Str).value }.orElse("")
                                val memo = tx.get(dirObj, "memo").map { (it as AmValue.Str).value }.orElse("")

                                val postings = mutableListOf<Posting>()
                                val postingsListOpt = tx.get(dirObj, "postings")
                                if (postingsListOpt.isPresent) {
                                    val postingsListObj = (postingsListOpt.get() as AmValue.List).id
                                    val plen = getLength(tx, postingsListObj)
                                    for (j in 0 until plen) {
                                        val postingOpt = tx.get(postingsListObj, j)
                                        if (postingOpt.isPresent) {
                                            val pObj = (postingOpt.get() as AmValue.Map).id
                                            val account = tx.get(pObj, "account").map { (it as AmValue.Str).value }.orElse("")
                                            val amount = tx.get(pObj, "amount").map { (it as AmValue.Str).value }.orElse("")
                                            val currency = tx.get(pObj, "currency").map { (it as AmValue.Str).value }.orElse("")
                                            postings.add(Posting(account, amount, currency))
                                        }
                                    }
                                }
                                result.add(TransactionDirective(date, payee, memo, postings))
                            }
                        }
                    }
                }
            }
        }
        return result
    }
}
