package tech.bananajuice.adzuki.shared

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        } else if (directive is BeancountNode.IncludeDirective) {
            val fileVar = directive.file.replace(".beancount", ".adzuki")
            doc.addIncludeDirective(
                tech.bananajuice.adzuki.shared.automerge.IncludeDirective(
                    id = nextId++,
                    file = fileVar
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
        } else if (dir is tech.bananajuice.adzuki.shared.automerge.IncludeDirective) {
            val file = dir.file.replace(".adzuki", ".beancount")
            val safeFile = file.replace("\"", "\\\"")
            sb.append("include \"${safeFile}\"\n\n")
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

fun exportFolderToZip(context: Context, rootFolder: DocumentFile, outStream: OutputStream) {
    val zos = ZipOutputStream(outStream)

    fun walk(folder: DocumentFile, currentPath: String) {
        val files = folder.listFiles()
        for (file in files) {
            val name = file.name ?: continue
            if (file.isDirectory) {
                walk(file, "${currentPath}${name}/")
            } else if (name.endsWith(".adzuki")) {
                val entryName = currentPath + name.replace(".adzuki", ".beancount")
                zos.putNextEntry(ZipEntry(entryName))

                try {
                    val bytes = context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: ByteArray(0)
                    val doc = AutomergeDocument(bytes)
                    val exportText = exportToBeancount(doc.getDirectives())
                    zos.write(exportText.toByteArray())
                } catch (e: Exception) {
                    zos.write("; Error reading file: ${e.message}\n".toByteArray())
                }

                zos.closeEntry()
            }
        }
    }

    walk(rootFolder, "")
    zos.finish()
}
