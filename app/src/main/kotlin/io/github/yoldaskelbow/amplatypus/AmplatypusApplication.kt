// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.yoldaskelbow.amplatypus

import android.app.Application
import androidx.room.Room
import io.github.yoldaskelbow.amplatypus.data.AppDatabase
import io.github.yoldaskelbow.amplatypus.data.Library
import io.github.yoldaskelbow.amplatypus.data.Settings
import io.github.yoldaskelbow.amplatypus.net.ApiConfig
import io.github.yoldaskelbow.amplatypus.net.Http
import io.github.yoldaskelbow.amplatypus.playback.PlayerConnection
import io.github.yoldaskelbow.amplatypus.source.MusicSource
import io.github.yoldaskelbow.amplatypus.source.amp.AmpSource
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandIndex
import io.github.yoldaskelbow.amplatypus.source.modland.ModlandSource
import io.github.yoldaskelbow.amplatypus.radio.Radio
import kotlinx.coroutines.MainScope
import java.io.File

/** Hand-wired dependencies; the app is small enough not to need a DI framework. */
class AppContainer(app: Application) {
    private val apiConfig = ApiConfig(BuildConfig.VERSION_NAME, BuildConfig.REPO_URL)
    private val http = Http.client(apiConfig.userAgent)
    private val db = Room.databaseBuilder(app, AppDatabase::class.java, "amplatypus.db")
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .fallbackToDestructiveMigration(true)
        .build()

    val modlandIndex = ModlandIndex(app, db, http)
    val modland: MusicSource = ModlandSource(db.indexDao(), http, modlandIndex::folderFiles)
    /** Amiga Music Preservation; `null` in builds where the source is switched off. */
    val amp: AmpSource? = if (BuildConfig.AMP_SOURCE_ENABLED) AmpSource(http) else null
    val sources: Map<String, MusicSource> = listOfNotNull(modland, amp).associateBy { it.id }
    val library = Library(db.libraryDao(), db.playlistDao(), sources, File(app.filesDir, "modules"))
    val player = PlayerConnection(app)
    val settings = Settings(app)
    val radio = Radio(MainScope(), library, modland, amp, player)
}

class AmplatypusApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
