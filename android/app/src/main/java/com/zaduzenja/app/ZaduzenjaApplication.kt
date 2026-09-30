package com.zaduzenja.app

import android.app.Application
import com.zaduzenja.app.data.backup.BackupScheduler
import com.zaduzenja.app.data.backup.GoogleDriveBackup
import com.zaduzenja.app.data.db.AppDatabase
import com.zaduzenja.app.data.db.migrateJsonToRoom
import com.programi.crashlog.CrashLogStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZaduzenjaApplication : Application() {
    lateinit var dataRepository: DataRepository
        private set
    lateinit var googleDriveBackup: GoogleDriveBackup
        private set
    lateinit var database: AppDatabase
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashLogStorage.install(this, getString(R.string.app_name))

        // Inicijalizuj Room bazu
        database = AppDatabase.getInstance(this)
        val dao = database.articleDao()

        // Pokreni migraciju iz JSON-a u pozadini
        applicationScope.launch {
            migrateJsonToRoom(this@ZaduzenjaApplication, dao)
        }

        // Kreiraj DataRepository sa Room DAO
        dataRepository = DataRepository(this, dao)

        googleDriveBackup = GoogleDriveBackup(applicationContext)

        // Dnevni backup ako korisnik dugo ne menja ništa (KEEP: ne resetuje se na svakom startu)
        BackupScheduler.schedulePeriodic(this)
    }
}
