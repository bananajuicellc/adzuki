package tech.bananajuice.adzuki.android

import android.app.Application
import androidx.room.Room
import tech.bananajuice.adzuki.android.data.AdzukiDatabase
import tech.bananajuice.adzuki.android.data.RoomFoldStateRepository

class AdzukiApplication : Application() {
    lateinit var database: AdzukiDatabase
    lateinit var foldStateRepository: RoomFoldStateRepository

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            this,
            AdzukiDatabase::class.java, "adzuki-database"
        ).build()

        foldStateRepository = RoomFoldStateRepository(database.foldedHeadingDao())
    }
}
