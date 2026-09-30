package com.zaduzenja.app.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zaduzenja.app.BackupPayloadCodec
import com.zaduzenja.app.ZaduzenjaApplication

/**
 * Pozadinski posao koji šalje backup na Google Drive. Pokreće ga [BackupScheduler]
 * (posle izmene + dnevno), uz uslov da ima mreže.
 *
 * Pravila „prazno ne gazi popunjeno" i „sveža instalacija ne prepisuje zatečeni
 * backup" su u [GoogleDriveBackup.backup]; ovde se `confirmedOverwrite` nikad ne
 * šalje, pa se takav backup tiho preskoči umesto da pita korisnika.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ZaduzenjaApplication ?: return Result.success()
        val backup = app.googleDriveBackup
        val repository = app.dataRepository

        if (!backup.isSignedIn()) {
            Log.d(TAG, "Preskačem auto-backup: korisnik nije prijavljen")
            return Result.success()
        }

        val payload = try {
            repository.buildBackupPayload()
        } catch (e: Exception) {
            // Neispravni podaci u bazi - backup bi bio krnj; sledeća izmena pokušava ponovo.
            Log.w(TAG, "Preskačem auto-backup: podaci nisu čitljivi (${e.message})")
            return Result.success()
        }

        val success = backup.backup(
            jsonData = BackupPayloadCodec.toJson(payload),
            localIsEmpty = payload.isEmpty,
            confirmedOverwrite = false
        )
        if (success) {
            Log.d(TAG, "Auto-backup uspešan")
            return Result.success()
        }

        return when (val state = backup.syncState.value) {
            // Oba su odluke koje mora da donese korisnik na ekranu - ponavljanje ne pomaže.
            is SyncState.OverwriteNeedsConfirmation -> {
                Log.d(TAG, "Preskačem auto-backup: prepisivanje traži potvrdu korisnika")
                Result.success()
            }
            is SyncState.EmptyData -> {
                Log.d(TAG, "Preskačem auto-backup: nema podataka")
                Result.success()
            }
            else -> {
                Log.w(TAG, "Auto-backup neuspešan: ${(state as? SyncState.Error)?.message}")
                if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val MAX_RETRY_ATTEMPTS = 7
    }
}
