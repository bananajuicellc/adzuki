package tech.bananajuice.adzuki.android.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FoldedHeadingEntity::class], version = 1, exportSchema = false)
abstract class AdzukiDatabase : RoomDatabase() {
    abstract fun foldedHeadingDao(): FoldedHeadingDao
}
