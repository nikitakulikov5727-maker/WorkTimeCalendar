package com.example.workcalendar.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.example.workcalendar.R
import com.example.workcalendar.data.TariffTemplate
import com.example.workcalendar.data.WorkDao
import com.example.workcalendar.data.WorkEntry
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GoogleUserAccount(
    val email: String,
    val displayName: String?,
    val accessToken: String
)

data class CloudBackupSnapshot(
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val entries: List<WorkEntry> = emptyList(),
    val templates: List<TariffTemplate> = emptyList()
)

data class SyncUiState(
    val account: GoogleUserAccount? = null,
    val isBusy: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val lastSyncLabel: String? = null,
    val statusMessage: String? = null
)

class GoogleDriveSyncManager(private val context: Context) {

    companion object {
        private const val BACKUP_FILE_NAME = "work_calendar_backup.json"
        private const val SPACE_APP_DATA = "appDataFolder"
        private const val PREFS_NAME = "drive_sync_prefs"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(context)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(
        SyncUiState(
            autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC, true)
        )
    )
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
        _uiState.value = _uiState.value.copy(autoSyncEnabled = enabled)
    }

    suspend fun signInAndAuthorize(activity: Activity): Result<GoogleUserAccount> {
        _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = null)
        return try {
            val webClientId = context.getString(R.string.google_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activity
            )
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val email = googleIdTokenCredential.id
            val displayName = googleIdTokenCredential.displayName

            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DriveScopes.APPDATA)))
                .build()

            val authResult = Identity.getAuthorizationClient(activity)
                .authorize(authRequest)
                .await()

            if (authResult.hasResolution()) {
                val pendingIntent = authResult.pendingIntent
                if (pendingIntent != null) {
                    activity.startIntentSenderForResult(
                        pendingIntent.intentSender,
                        9001,
                        null,
                        0,
                        0,
                        0
                    )
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        statusMessage = "Подтвердите доступ к Google Диску и нажмите войти ещё раз"
                    )
                    return Result.failure(IllegalStateException("Требуется подтверждение доступа к Google Диску"))
                }
            }

            val token = authResult.accessToken
                ?: return Result.failure(IllegalStateException("Не удалось получить токен доступа к Google Диску"))

            val account = GoogleUserAccount(
                email = email,
                displayName = displayName,
                accessToken = token
            )
            _uiState.value = _uiState.value.copy(
                account = account,
                isBusy = false,
                statusMessage = "Вход выполнен: $email"
            )
            Result.success(account)
        } catch (e: GetCredentialException) {
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                statusMessage = "Вход отменён или не выполнен (${e.message ?: "Credential error"})"
            )
            Result.failure(e)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                statusMessage = "Ошибка авторизации: ${e.message ?: "неизвестная ошибка"}"
            )
            Result.failure(e)
        }
    }

    fun signOut() {
        _uiState.value = _uiState.value.copy(
            account = null,
            statusMessage = "Вы вышли из аккаунта Google"
        )
    }

    private fun createDriveService(accessToken: String): Drive {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
            request.connectTimeout = 20_000
            request.readTimeout = 20_000
        }
        return Drive.Builder(transport, jsonFactory, requestInitializer)
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }

    private fun findBackupFile(drive: Drive): File? {
        val listResponse = drive.files().list()
            .setSpaces(SPACE_APP_DATA)
            .setQ("name = '$BACKUP_FILE_NAME' and trashed = false")
            .setFields("files(id, name, modifiedTime)")
            .execute()
        return listResponse.files?.firstOrNull()
    }

    suspend fun backupToDrive(
        entries: List<WorkEntry>,
        templates: List<TariffTemplate>,
        silent: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = _uiState.value.account?.accessToken
            ?: return@withContext Result.failure(IllegalStateException("Сначала выполните вход в аккаунт Google"))

        if (!silent) {
            _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = null)
        }

        try {
            val drive = createDriveService(token)
            val payload = CloudBackupSnapshot(exportedAtMillis = System.currentTimeMillis(),
                entries = entries,
                templates = templates
            )
            val jsonBytes = gson.toJson(payload).toByteArray(StandardCharsets.UTF_8)
            val mediaContent = ByteArrayContent("application/json", jsonBytes)

            val existingFile = findBackupFile(drive)
            if (existingFile != null) {
                drive.files().update(existingFile.id, File(), mediaContent).execute()
            } else {
                val metadata = File().apply {
                    name = BACKUP_FILE_NAME
                    parents = listOf(SPACE_APP_DATA)
                }
                drive.files().create(metadata, mediaContent)
                    .setFields("id")
                    .execute()
            }

            val timeLabel = formatNow()
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                lastSyncLabel = timeLabel,
                statusMessage = if (silent) null else "Сохранено в Google Диск ($timeLabel)"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                statusMessage = "Ошибка бэкапа: ${e.message ?: "нет сети или истёк токен"}"
            )
            Result.failure(e)
        }
    }

    suspend fun restoreFromDrive(dao: WorkDao, silentIfMissing: Boolean = false): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val token = _uiState.value.account?.accessToken
                ?: return@withContext Result.failure(IllegalStateException("Сначала выполните вход в аккаунт Google"))

            _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = null)

            try {
                val drive = createDriveService(token)
                val backupFile = findBackupFile(drive)
                if (backupFile == null) {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        statusMessage = if (silentIfMissing) null else "Резервная копия на Google Диске не найдена"
                    )
                    return@withContext Result.success(false)
                }

                val outputStream = ByteArrayOutputStream()
                drive.files().get(backupFile.id).executeMediaAndDownloadTo(outputStream)
                val jsonText = outputStream.toString(StandardCharsets.UTF_8.name())
                val snapshot = gson.fromJson(jsonText, CloudBackupSnapshot::class.java)
                    ?: return@withContext Result.failure(IllegalStateException("Повреждён файл резервной копии"))

                dao.clearAllEntries()
                if (snapshot.entries.isNotEmpty()) {
                    dao.insertAll(snapshot.entries)
                }

                dao.clearTariffTemplates()
                if (snapshot.templates.isNotEmpty()) {
                    dao.insertTariffTemplates(snapshot.templates)
                }

                val timeLabel = formatNow()
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    lastSyncLabel = timeLabel,
                    statusMessage = "Восстановлено из облака (${snapshot.entries.size} записей)"
                )
                Result.success(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    statusMessage = "Ошибка восстановления: ${e.message ?: "неизвестная ошибка"}"
                )
                Result.failure(e)
            }
        }

    private fun formatNow(): String {
        return SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
    }
}
