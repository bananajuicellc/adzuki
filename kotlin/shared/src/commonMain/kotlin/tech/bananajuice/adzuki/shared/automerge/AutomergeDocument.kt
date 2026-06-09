package tech.bananajuice.adzuki.shared.automerge

import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.Transaction

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

    private fun getLength(tx: Transaction, listId: ObjectId): Long {
        return tx.length(listId)
    }

    private fun ensureVersionAndDirectives(tx: Transaction): ObjectId {
        val root = ObjectId.ROOT

        val versionOpt = tx.get(root, "version")
        if (!versionOpt.isPresent || (versionOpt.get() as? AmValue.Str)?.value != "0") {
            tx.set(root, "version", "0")
        }

        val directivesOpt = tx.get(root, "directives")
        return if (directivesOpt.isPresent) {
            val amValue = directivesOpt.get()
            if (amValue is AmValue.List) {
                amValue.id
            } else {
                tx.set(root, "directives", ObjectType.LIST)
            }
        } else {
            tx.set(root, "directives", ObjectType.LIST)
        }
    }

    fun addAccount(account: AccountDirective) {
        doc.startTransaction().use { tx ->
            val directives = ensureVersionAndDirectives(tx)
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
            val directives = ensureVersionAndDirectives(tx)
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

    private fun parseAccount(tx: Transaction, dirObj: ObjectId): AccountDirective? {
        val name = tx.get(dirObj, "name").map { (it as? AmValue.Str)?.value }.orElse("") ?: ""
        val date = tx.get(dirObj, "date").map { (it as? AmValue.Str)?.value }.orElse("") ?: ""

        val currencies = mutableListOf<String>()
        val currListOpt = tx.get(dirObj, "constraint_currencies")

        if (currListOpt.isPresent) {
            val currListVal = currListOpt.get()
            if (currListVal is AmValue.List) {
                val clen = getLength(tx, currListVal.id)
                for (j in 0 until clen) {
                    val currOpt = tx.get(currListVal.id, j)
                    if (currOpt.isPresent) {
                        val currVal = currOpt.get()
                        if (currVal is AmValue.Str) {
                            currencies.add(currVal.value)
                        }
                    }
                }
            }
        }
        return AccountDirective(date, name, currencies)
    }

    private fun parseTransaction(tx: Transaction, dirObj: ObjectId): TransactionDirective? {
        val date = tx.get(dirObj, "date").map { (it as? AmValue.Str)?.value }.orElse("") ?: ""
        val payee = tx.get(dirObj, "payee").map { (it as? AmValue.Str)?.value }.orElse("") ?: ""
        val memo = tx.get(dirObj, "memo").map { (it as? AmValue.Str)?.value }.orElse("") ?: ""

        val postings = mutableListOf<Posting>()
        val postingsListOpt = tx.get(dirObj, "postings")

        if (postingsListOpt.isPresent) {
            val postingsListVal = postingsListOpt.get()
            if (postingsListVal is AmValue.List) {
                val plen = getLength(tx, postingsListVal.id)
                for (j in 0 until plen) {
                    val postingOpt = tx.get(postingsListVal.id, j.toLong())
                    if (postingOpt.isPresent) {
                        val pVal = postingOpt.get()
                        if (pVal is AmValue.Map) {
                            val account = tx.get(pVal.id, "account").map { (it as? AmValue.Str)?.value }.orElse("") ?: ""
                            val amount = tx.get(pVal.id, "amount").map { (it as? AmValue.Str)?.value }.orElse("") ?: ""
                            val currency = tx.get(pVal.id, "currency").map { (it as? AmValue.Str)?.value }.orElse("") ?: ""
                            postings.add(Posting(account, amount, currency))
                        }
                    }
                }
            }
        }
        return TransactionDirective(date, payee, memo, postings)
    }

    fun getDirectives(): List<Directive> {
        val result = mutableListOf<Directive>()
        doc.startTransaction().use { tx ->
            val root = ObjectId.ROOT
            val directivesOpt = tx.get(root, "directives")
            if (!directivesOpt.isPresent) return emptyList()

            val directivesVal = directivesOpt.get()
            if (directivesVal !is AmValue.List) return emptyList()

            val directivesId = directivesVal.id
            val len = getLength(tx, directivesId)

            for (i in 0 until len) {
                val dirOpt = tx.get(directivesId, i)
                if (dirOpt.isPresent) {
                    val dirVal = dirOpt.get()
                    if (dirVal is AmValue.Map) {
                        val typeOpt = tx.get(dirVal.id, "type")
                        if (typeOpt.isPresent) {
                            val typeVal = typeOpt.get()
                            if (typeVal is AmValue.Str) {
                                when (typeVal.value) {
                                    "Account" -> parseAccount(tx, dirVal.id)?.let { result.add(it) }
                                    "Transaction" -> parseTransaction(tx, dirVal.id)?.let { result.add(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result
    }
}
