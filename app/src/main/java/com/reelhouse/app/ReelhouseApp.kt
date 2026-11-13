package com.reelhouse.app

import android.app.Application
import androidx.room.Room
import com.reelhouse.app.data.AppDatabase
import com.reelhouse.app.data.AppPaths
import com.reelhouse.app.data.Prefs
import com.reelhouse.app.data.Repository
import java.io.File

class ReelhouseApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        AppPaths.posterDir = File(filesDir, "posters").also { it.mkdirs() }
        AppPaths.subsDir = File(filesDir, "subs").also { it.mkdirs() }
        prefs = Prefs(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "reelhouse.db")
            .fallbackToDestructiveMigration()
            .build()
        repository = Repository(this, db, prefs)
    }
}
