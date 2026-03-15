package tech.bananajuice.adzuki.android.data

import androidx.room.Entity

@Entity(tableName = "folded_headings", primaryKeys = ["documentUri", "headingIndex"])
data class FoldedHeadingEntity(
    val documentUri: String,
    val headingIndex: String
)
