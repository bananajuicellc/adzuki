package tech.bananajuice.adzuki.android.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoldedHeadingDao {
    @Query("SELECT * FROM folded_headings WHERE documentUri = :documentUri")
    suspend fun getFoldedHeadingsForDocument(documentUri: String): List<FoldedHeadingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoldedHeading(entity: FoldedHeadingEntity)

    @Delete
    suspend fun deleteFoldedHeading(entity: FoldedHeadingEntity)
}
