package com.zaduzenja.app.data.backup

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "GoogleDriveBackup"
private const val BACKUP_FILE_NAME = "zaduzenja_backup.json"
private const val APP_DATA_FOLDER = "appDataFolder"
private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val PREFS_NAME = "zaduzenja_backup_prefs"
private const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"
private const val KEY_LAST_SYNC_TIME = "last_sync_time"
private const val KEY_CLOUD_ADOPTED = "cloud_adopted"

private data class DriveBackupFile(val id: String, val name: String)

/** Token je istekao ili je opozvan - briše se iz keša i zahtev se ponavlja jednom. */
private class UnauthorizedException : IOException("HTTP 401")

class GoogleDriveBackup(private val context: Context) {

    private val _signInState = MutableStateFlow<SignInState>(SignInState.SignedOut)
    val signInState: StateFlow<SignInState> = _signInState

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime

    private var currentAccount: GoogleSignInAccount? = null
    private val syncMutex = Mutex()

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
        GoogleSignIn.getClient(context, signInOptions)
    }

    init {
        val savedTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        if (savedTime > 0) {
            _lastSyncTime.value = savedTime
        }

        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DRIVE_SCOPE))) {
            currentAccount = account
            _signInState.value = SignInState.SignedIn(account.email ?: "Unknown")
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            currentAccount = account
            _signInState.value = SignInState.SignedIn(account.email ?: "Unknown")
        } else {
            currentAccount = null
            _signInState.value = SignInState.SignedOut
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                googleSignInClient.signOut().await()
                currentAccount = null
                _signInState.value = SignInState.SignedOut
            } catch (e: Exception) {
                Log.e(TAG, "Sign out failed", e)
            }
        }
    }

    /**
     * Da li je ova instalacija "preuzela" cloud backup: uspešan restore, uspešan backup kad u
     * oblaku nije bilo fajla, ili izričita potvrda korisnika da prepiše postojeći backup.
     * Dok nije preuzeto, postojeći cloud backup se ne prepisuje bez pitanja.
     */
    fun isCloudAdopted(): Boolean {
        if (!prefs.contains(KEY_CLOUD_ADOPTED)) {
            // Postojeće instalacije: ako je sa ovog uređaja već rađen backup, cloud je "naš"
            val adopted = prefs.getLong(KEY_LAST_SYNC_TIME, 0L) > 0
            prefs.edit().putBoolean(KEY_CLOUD_ADOPTED, adopted).apply()
        }
        return prefs.getBoolean(KEY_CLOUD_ADOPTED, false)
    }

    /** Poziva se kada su podaci iz oblaka uspešno vraćeni i sačuvani na uređaju. */
    fun markCloudAdopted() {
        prefs.edit().putBoolean(KEY_CLOUD_ADOPTED, true).apply()
    }

    /** Neuspelo listanje (mreža/nalog) je UNKNOWN - nikad se ne tumači kao "oblak je prazan". */
    suspend fun cloudBackupStatus(): CloudBackupStatus = syncMutex.withLock {
        if (currentAccount == null) return@withLock CloudBackupStatus.UNKNOWN
        withContext(Dispatchers.IO) {
            try {
                val fileId = runAuthorized { token -> findBackupFile(token) }
                if (fileId != null) CloudBackupStatus.EXISTS else CloudBackupStatus.MISSING
            } catch (e: Exception) {
                Log.e(TAG, "Cloud check failed", e)
                CloudBackupStatus.UNKNOWN
            }
        }
    }

    /**
     * @param localIsEmpty lokalno nema nijednog zaduženja - prazno ne sme da pregazi popunjeno
     * @param confirmedOverwrite korisnik je izričito potvrdio prepisivanje postojećeg backup-a
     */
    suspend fun backup(
        jsonData: String,
        localIsEmpty: Boolean,
        confirmedOverwrite: Boolean = false
    ): Boolean = syncMutex.withLock {
        if (currentAccount == null) return@withLock false

        withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing
                runAuthorized { token -> backupWithToken(token, jsonData, localIsEmpty, confirmedOverwrite) }
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Backup failed")
                false
            }
        }
    }

    private fun backupWithToken(
        token: String,
        jsonData: String,
        localIsEmpty: Boolean,
        confirmedOverwrite: Boolean
    ): Boolean {
        val existingFileId = findBackupFile(token)

        if (existingFileId == null && localIsEmpty) {
            Log.w(TAG, "Backup preskočen - nema podataka")
            _syncState.value = SyncState.EmptyData
            return false
        }

        if (existingFileId != null && !confirmedOverwrite && (localIsEmpty || !isCloudAdopted())) {
            _syncState.value = SyncState.OverwriteNeedsConfirmation(localIsEmpty)
            return false
        }

        if (existingFileId != null) {
            if (confirmedOverwrite) {
                markCloudAdopted()
            }
            // Ako kopija ne uspe, baca izuzetak i backup se prekida pre prepisivanja
            createPrevCopy(token, existingFileId)
            updateFile(token, existingFileId, jsonData)
        } else {
            createFile(token, BACKUP_FILE_NAME, jsonData)
        }

        markCloudAdopted()
        val now = System.currentTimeMillis()
        _lastSyncTime.value = now
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, now).apply()
        prunePrevCopies(token)
        _syncState.value = SyncState.Success
        return true
    }

    /**
     * Vraća sadržaj backup fajla. null + [SyncState.Idle] znači da backup ne postoji,
     * null + [SyncState.Error] da preuzimanje nije uspelo. Na ovoj putanji se ništa ne briše.
     */
    suspend fun restore(): String? = syncMutex.withLock {
        if (currentAccount == null) return@withLock null

        withContext(Dispatchers.IO) {
            try {
                _syncState.value = SyncState.Syncing

                val data = runAuthorized { token ->
                    findBackupFile(token)?.let { fileId -> downloadFile(token, fileId) }
                }
                if (data == null) {
                    _syncState.value = SyncState.Idle
                    return@withContext null
                }
                if (data.isBlank()) {
                    throw IOException("Backup fajl je prazan")
                }

                _syncState.value = SyncState.Success
                data
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                _syncState.value = SyncState.Error(e.message ?: "Restore failed")
                null
            }
        }
    }

    // --- Token ---

    private fun fetchToken(): String {
        val androidAccount = currentAccount?.account
            ?: throw IOException("Google nalog nije povezan")
        return GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_SCOPE")
    }

    /** Izvršava blok sa tokenom; na 401 briše keširani token i ponavlja jednom sa novim. */
    private fun <T> runAuthorized(block: (String) -> T): T {
        val token = fetchToken()
        return try {
            block(token)
        } catch (e: UnauthorizedException) {
            Log.w(TAG, "Token odbijen (401), tražim novi")
            GoogleAuthUtil.clearToken(context, token)
            block(fetchToken())
        }
    }

    // --- Drive REST ---

    /** Izvršava zahtev; svaki odgovor koji nije 2xx je izuzetak (401 posebno, zbog ponavljanja). */
    private fun execute(request: Request, what: String): String {
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw UnauthorizedException()
            }
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Drive u telu odgovora kaže ŠTA je odbio - to ide u poruku korisniku
                val detail = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() }
                throw IOException("$what: HTTP ${response.code}" + (detail?.let { " - $it" } ?: ""))
            }
            return text
        }
    }

    /**
     * Traži glavni backup fajl po tačnom imenu. Ništa ne briše - ako postoji više fajlova
     * sa istim imenom, koristi se najnoviji, a ostali se ne diraju.
     */
    private fun findBackupFile(token: String): String? {
        return listFiles(token, "name = '$BACKUP_FILE_NAME' and trashed = false", "modifiedTime desc")
            .firstOrNull { it.name == BACKUP_FILE_NAME }
            ?.id
    }

    private fun listFiles(token: String, query: String, orderBy: String? = null): List<DriveBackupFile> {
        val result = mutableListOf<DriveBackupFile>()
        var pageToken: String? = null
        do {
            val url = DRIVE_FILES_URL.toHttpUrl().newBuilder()
                .addQueryParameter("spaces", APP_DATA_FOLDER)
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime)")
                .addQueryParameter("pageSize", "200")
                .apply {
                    orderBy?.let { addQueryParameter("orderBy", it) }
                    pageToken?.let { addQueryParameter("pageToken", it) }
                }
                .build()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val json = JSONObject(execute(request, "Find file failed"))
            val files = json.optJSONArray("files")
            if (files != null) {
                for (i in 0 until files.length()) {
                    val file = files.optJSONObject(i) ?: continue
                    val id = file.optString("id")
                    if (id.isNotBlank()) {
                        result.add(DriveBackupFile(id = id, name = file.optString("name")))
                    }
                }
            }
            pageToken = json.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return result
    }

    /** Pre prepisivanja čuva postojeći fajl kao "<ime>_prev_<vreme>.json". Baca izuzetak ako ne uspe. */
    private fun createPrevCopy(token: String, fileId: String) {
        val copyName = BackupFilePolicy.prevCopyName(BACKUP_FILE_NAME, System.currentTimeMillis())
        try {
            copyFile(token, fileId, copyName)
        } catch (e: UnauthorizedException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "files.copy nije uspeo, pokušavam download + create", e)
            createFile(token, copyName, downloadFile(token, fileId))
        }
    }

    /** Čuva se najnovijih 5 prethodnih kopija. Neuspeh čišćenja ne obara backup. */
    private fun prunePrevCopies(token: String) {
        try {
            val files = listFiles(token, "trashed = false").map { it.id to it.name }
            BackupFilePolicy.prevCopiesToDelete(BACKUP_FILE_NAME, files).forEach { id ->
                try {
                    deleteFile(token, id)
                } catch (e: Exception) {
                    Log.w(TAG, "Neuspešno brisanje stare kopije: $id", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Čišćenje starih kopija nije uspelo", e)
        }
    }

    private fun copyFile(token: String, fileId: String, newName: String) {
        val metadata = JSONObject().apply {
            put("name", newName)
            put("parents", org.json.JSONArray().put(APP_DATA_FOLDER))
        }
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId/copy")
            .addHeader("Authorization", "Bearer $token")
            .post(metadata.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        execute(request, "Copy file failed")
    }

    private fun deleteFile(token: String, fileId: String) {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId")
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()

        execute(request, "Delete file failed")
    }

    private fun createFile(token: String, fileName: String, content: String) {
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", org.json.JSONArray().put(APP_DATA_FOLDER))
        }

        val metadataPart = metadata.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val contentPart = content
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        // Drive multipart upload traži multipart/related: prvi deo metapodaci, drugi sadržaj
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadataPart)
            .addPart(contentPart)
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        execute(request, "Create file failed")
    }

    private fun updateFile(token: String, fileId: String, content: String) {
        val body = content.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL/$fileId?uploadType=media")
            .addHeader("Authorization", "Bearer $token")
            .patch(body)
            .build()

        execute(request, "Update file failed")
    }

    private fun downloadFile(token: String, fileId: String): String {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return execute(request, "Download file failed")
    }

    fun isSignedIn(): Boolean = _signInState.value is SignInState.SignedIn

    fun isFirstLaunch(): Boolean {
        return !prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)
    }

    fun markFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH_DONE, true).apply()
    }
}

sealed class SignInState {
    data object SignedOut : SignInState()
    data class SignedIn(val email: String) : SignInState()
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data object Success : SyncState()
    data object EmptyData : SyncState()
    /**
     * U oblaku postoji backup koji bi bio pregažen: ova instalacija ga nije preuzela ili je
     * lokalno stanje prazno. Backup se ne radi dok korisnik izričito ne potvrdi.
     */
    data class OverwriteNeedsConfirmation(val localIsEmpty: Boolean) : SyncState()
    data class Error(val message: String) : SyncState()
}

enum class CloudBackupStatus { EXISTS, MISSING, UNKNOWN }
