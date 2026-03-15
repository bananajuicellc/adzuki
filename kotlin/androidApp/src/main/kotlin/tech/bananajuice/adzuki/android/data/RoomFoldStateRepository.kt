package tech.bananajuice.adzuki.android.data

import tech.bananajuice.adzuki.shared.mvi.FoldStateRepository

class RoomFoldStateRepository(private val dao: FoldedHeadingDao) : FoldStateRepository {

    override suspend fun getFoldedHeadings(documentId: String): Set<List<Int>> {
        val entities = dao.getFoldedHeadingsForDocument(documentId)
        return entities.map { entity ->
            entity.headingIndex.split(",").mapNotNull { it.toIntOrNull() }
        }.toSet()
    }

    override suspend fun addFoldedHeading(documentId: String, headingIndex: List<Int>) {
        val indexStr = headingIndex.joinToString(",")
        dao.insertFoldedHeading(FoldedHeadingEntity(documentId, indexStr))
    }

    override suspend fun removeFoldedHeading(documentId: String, headingIndex: List<Int>) {
        val indexStr = headingIndex.joinToString(",")
        dao.deleteFoldedHeading(FoldedHeadingEntity(documentId, indexStr))
    }
}
