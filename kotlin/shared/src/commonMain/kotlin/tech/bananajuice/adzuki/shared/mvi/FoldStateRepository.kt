package tech.bananajuice.adzuki.shared.mvi

interface FoldStateRepository {
    suspend fun getFoldedHeadings(documentId: String): Set<List<Int>>
    suspend fun addFoldedHeading(documentId: String, headingIndex: List<Int>)
    suspend fun removeFoldedHeading(documentId: String, headingIndex: List<Int>)
}
