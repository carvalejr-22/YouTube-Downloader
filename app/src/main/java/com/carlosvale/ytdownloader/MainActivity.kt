package com.carlosvale.ytdownloader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedUrl = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        setContent {
            GetMuviTheme {
                DownloaderScreen(initialUrl = sharedUrl.orEmpty())
            }
        }
    }
}

enum class MediaKind(val title: String) {
    VIDEO_AUDIO("Completo"),
    VIDEO_ONLY("Vídeo"),
    AUDIO("Áudio")
}

enum class DownloadType {
    BEST_AV,
    VIDEO_AUDIO,
    VIDEO_ONLY,
    AUDIO_ORIGINAL,
    AUDIO_CONVERT
}

data class DownloadOption(
    val id: String,
    val kind: MediaKind,
    val type: DownloadType,
    val title: String,
    val subtitle: String,
    val height: Int? = null,
    val container: String? = null,
    val audioFormat: String? = null,
    val sourceExt: String? = null,
    val estimatedBytes: Long? = null
)

data class PlaylistEntry(
    val title: String,
    val url: String
)

data class MediaSummary(
    val title: String,
    val uploader: String = "",
    val durationSeconds: Long? = null,
    val playlistCount: Int = 0,
    val playlistEntries: List<PlaylistEntry> = emptyList(),
    val options: List<DownloadOption> = emptyList()
)

data class UiState(
    val analyzing: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val etaSeconds: Long = 0,
    val status: String = "",
    val media: MediaSummary? = null,
    val error: String? = null,
    val successMessage: String? = null,
    val customFolder: Uri? = null,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val currentItem: Int = 0,
    val resetToken: Int = 0
)

private data class DownloadAttempt(
    val label: String,
    val playerClients: String? = null,
    val compatibilityMode: Boolean = false,
    val restrictFilenames: Boolean = false
)

private data class FormatDescriptor(
    val ext: String,
    val bytes: Long?
)

private const val PROGRESS_UI_INTERVAL_MS = 250L
private const val PROGRESS_UI_DELTA = 0.01f
private val STATUS_WHITESPACE = Regex("\\s+")

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var processId: String? = null

    @Volatile
    private var cancelRequested = false

    fun setFolder(uri: Uri?) {
        _state.value = _state.value.copy(customFolder = uri, successMessage = null)
    }

    fun clearSuccess() {
        if (_state.value.successMessage != null) {
            _state.value = _state.value.copy(successMessage = null)
        }
    }

    fun analyze(url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _state.value = _state.value.copy(error = "Cole um link válido.", successMessage = null)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(
                analyzing = true,
                error = null,
                successMessage = null,
                media = null,
                status = "Atualizando mecanismo…"
            )

            runCatching {
                val app = getApplication<YTDownloaderApp>()
                app.ensureDownloaderReady()
                _state.value = _state.value.copy(status = "Analisando mídia e formatos…")

                val request = YoutubeDLRequest(cleanUrl)
                    .addOption("--dump-single-json")
                    .addOption("--flat-playlist")
                    .addOption("--skip-download")
                    .addOption("--no-warnings")
                    .addOption("--yes-playlist")

                if (isYouTubeUrl(cleanUrl)) {
                    request.addOption(
                        "--extractor-args",
                        "youtube:player_client=default,web_embedded,tv_downgraded"
                    )
                }

                val output = YoutubeDL.getInstance().execute(request).out.trim()
                val json = JSONObject(output)
                val entriesArray = json.optJSONArray("entries")
                val playlistEntries = mutableListOf<PlaylistEntry>()

                if (entriesArray != null) {
                    for (i in 0 until entriesArray.length()) {
                        val item = entriesArray.optJSONObject(i) ?: continue
                        val title = item.optString("title").ifBlank { "Item ${i + 1}" }
                        val entryUrl = normalizePlaylistEntryUrl(item)
                        if (entryUrl.isNotBlank()) {
                            playlistEntries += PlaylistEntry(title = title, url = entryUrl)
                        }
                    }
                }

                val options = buildDownloadOptions(json.optJSONArray("formats"), playlistEntries.isNotEmpty())

                MediaSummary(
                    title = json.optString("title", "Mídia encontrada"),
                    uploader = json.optString("uploader", json.optString("channel", "")),
                    durationSeconds = if (json.has("duration") && !json.isNull("duration")) json.optLong("duration") else null,
                    playlistCount = playlistEntries.size,
                    playlistEntries = playlistEntries,
                    options = options
                )
            }.onSuccess { media ->
                _state.value = _state.value.copy(
                    analyzing = false,
                    media = media,
                    status = "Pronto para baixar"
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    analyzing = false,
                    error = friendlyError(error),
                    status = ""
                )
            }
        }
    }

    private fun buildDownloadOptions(formatsArray: JSONArray?, isPlaylist: Boolean): List<DownloadOption> {
        if (isPlaylist || formatsArray == null || formatsArray.length() == 0) {
            return playlistFallbackOptions()
        }

        val videoByHeight = mutableMapOf<Int, MutableList<FormatDescriptor>>()
        val audioExts = mutableSetOf<String>()
        var hasAnyAudio = false

        for (i in 0 until formatsArray.length()) {
            val format = formatsArray.optJSONObject(i) ?: continue
            val vcodec = format.optString("vcodec", "none")
            val acodec = format.optString("acodec", "none")
            val ext = format.optString("ext", "").lowercase(Locale.ROOT)
            val hasVideo = vcodec.isNotBlank() && vcodec != "none"
            val hasAudio = acodec.isNotBlank() && acodec != "none"
            val height = if (format.has("height") && !format.isNull("height")) {
                format.optInt("height").takeIf { it > 0 }
            } else {
                null
            }
            val size = when {
                format.has("filesize") && !format.isNull("filesize") -> format.optLong("filesize")
                format.has("filesize_approx") && !format.isNull("filesize_approx") -> format.optLong("filesize_approx")
                else -> null
            }?.takeIf { it > 0 }

            if (hasAudio) hasAnyAudio = true

            if (hasVideo && height != null) {
                videoByHeight.getOrPut(height) { mutableListOf() }
                    .add(FormatDescriptor(ext = ext, bytes = size))
            }

            if (hasAudio && !hasVideo && ext.isNotBlank()) {
                audioExts += ext
            }
        }

        val result = mutableListOf<DownloadOption>()
        result += DownloadOption(
            id = "best-av",
            kind = MediaKind.VIDEO_AUDIO,
            type = DownloadType.BEST_AV,
            title = "Melhor qualidade",
            subtitle = "Escolhe automaticamente a melhor combinação de vídeo + áudio"
        )

        val heights = videoByHeight.keys.sortedDescending()
        heights.forEach { height ->
            val atHeight = videoByHeight[height].orEmpty()
            val extOrder = (listOf("mp4", "webm") + atHeight.map { it.ext }
                .filter { it.isNotBlank() && it !in setOf("mp4", "webm") }).distinct()

            extOrder.forEach { ext ->
                val matching = atHeight.filter { it.ext == ext }
                if (matching.isNotEmpty()) {
                    val bytes = matching.mapNotNull { it.bytes }.maxOrNull()
                    result += DownloadOption(
                        id = "av-$height-$ext",
                        kind = MediaKind.VIDEO_AUDIO,
                        type = DownloadType.VIDEO_AUDIO,
                        title = "${height}p · ${ext.uppercase(Locale.ROOT)}",
                        subtitle = buildOptionSubtitle("Vídeo + áudio", bytes),
                        height = height,
                        container = ext,
                        estimatedBytes = bytes
                    )
                }
            }
        }

        heights.forEach { height ->
            val atHeight = videoByHeight[height].orEmpty()
            val extOrder = (listOf("mp4", "webm") + atHeight.map { it.ext }
                .filter { it.isNotBlank() && it !in setOf("mp4", "webm") }).distinct()

            extOrder.forEach { ext ->
                val matching = atHeight.filter { it.ext == ext }
                if (matching.isNotEmpty()) {
                    val bytes = matching.mapNotNull { it.bytes }.maxOrNull()
                    result += DownloadOption(
                        id = "video-$height-$ext",
                        kind = MediaKind.VIDEO_ONLY,
                        type = DownloadType.VIDEO_ONLY,
                        title = "${height}p · ${ext.uppercase(Locale.ROOT)}",
                        subtitle = buildOptionSubtitle("Somente vídeo, sem faixa de áudio", bytes),
                        height = height,
                        container = ext,
                        estimatedBytes = bytes
                    )
                }
            }
        }

        if (hasAnyAudio) {
            result += DownloadOption(
                id = "audio-mp3",
                kind = MediaKind.AUDIO,
                type = DownloadType.AUDIO_CONVERT,
                title = "MP3",
                subtitle = "Alta compatibilidade · melhor áudio disponível",
                audioFormat = "mp3"
            )
            result += DownloadOption(
                id = "audio-m4a",
                kind = MediaKind.AUDIO,
                type = DownloadType.AUDIO_CONVERT,
                title = "M4A",
                subtitle = "Boa qualidade com arquivo compacto",
                audioFormat = "m4a"
            )
            result += DownloadOption(
                id = "audio-opus",
                kind = MediaKind.AUDIO,
                type = DownloadType.AUDIO_CONVERT,
                title = "Opus",
                subtitle = "Formato moderno e eficiente",
                audioFormat = "opus"
            )
            result += DownloadOption(
                id = "audio-original",
                kind = MediaKind.AUDIO,
                type = DownloadType.AUDIO_ORIGINAL,
                title = "Original",
                subtitle = "Mantém o melhor formato de áudio fornecido pela plataforma"
            )

            if ("webm" in audioExts) {
                result += DownloadOption(
                    id = "audio-webm",
                    kind = MediaKind.AUDIO,
                    type = DownloadType.AUDIO_ORIGINAL,
                    title = "WebM",
                    subtitle = "Faixa WebM original disponível nesta mídia",
                    sourceExt = "webm"
                )
            }
        }

        return result.distinctBy { it.id }
    }

    private fun playlistFallbackOptions(): List<DownloadOption> = listOf(
        DownloadOption(
            id = "best-av",
            kind = MediaKind.VIDEO_AUDIO,
            type = DownloadType.BEST_AV,
            title = "Melhor qualidade",
            subtitle = "Cada item usa a melhor qualidade disponível"
        ),
        DownloadOption(
            id = "playlist-mp4",
            kind = MediaKind.VIDEO_AUDIO,
            type = DownloadType.VIDEO_AUDIO,
            title = "MP4 automático",
            subtitle = "Prioriza MP4 em cada item da lista",
            container = "mp4"
        ),
        DownloadOption(
            id = "playlist-video",
            kind = MediaKind.VIDEO_ONLY,
            type = DownloadType.VIDEO_ONLY,
            title = "Melhor vídeo",
            subtitle = "Somente a faixa de vídeo de cada item"
        ),
        DownloadOption(
            id = "audio-mp3",
            kind = MediaKind.AUDIO,
            type = DownloadType.AUDIO_CONVERT,
            title = "MP3",
            subtitle = "Converte o melhor áudio de cada item para MP3",
            audioFormat = "mp3"
        ),
        DownloadOption(
            id = "audio-m4a",
            kind = MediaKind.AUDIO,
            type = DownloadType.AUDIO_CONVERT,
            title = "M4A",
            subtitle = "Converte o melhor áudio de cada item para M4A",
            audioFormat = "m4a"
        ),
        DownloadOption(
            id = "audio-opus",
            kind = MediaKind.AUDIO,
            type = DownloadType.AUDIO_CONVERT,
            title = "Opus",
            subtitle = "Converte o melhor áudio de cada item para Opus",
            audioFormat = "opus"
        ),
        DownloadOption(
            id = "audio-original",
            kind = MediaKind.AUDIO,
            type = DownloadType.AUDIO_ORIGINAL,
            title = "Original",
            subtitle = "Mantém o melhor formato de áudio disponível em cada item"
        )
    )

    fun download(url: String, option: DownloadOption) {
        if (_state.value.downloading) return
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return

        val app = getApplication<YTDownloaderApp>()
        val media = _state.value.media
        val playlistEntries = media?.playlistEntries.orEmpty()
        val isPlaylist = playlistEntries.isNotEmpty()
        val totalItems = if (isPlaylist) playlistEntries.size else 1

        viewModelScope.launch(Dispatchers.IO) {
            cancelRequested = false
            val customTree = _state.value.customFolder
            val jobDir = if (customTree == null) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GetMuvi")
            } else {
                File(app.cacheDir, "export-${System.currentTimeMillis()}")
            }

            jobDir.mkdirs()
            _state.value = _state.value.copy(
                downloading = true,
                progress = 0f,
                error = null,
                successMessage = null,
                status = "Atualizando mecanismo…",
                completedItems = 0,
                totalItems = totalItems,
                currentItem = if (totalItems > 0) 1 else 0
            )

            try {
                app.ensureDownloaderReady()
                if (
                    option.type == DownloadType.BEST_AV ||
                    option.type == DownloadType.VIDEO_AUDIO ||
                    option.type == DownloadType.AUDIO_CONVERT
                ) {
                    app.ensureFfmpegReady()
                }

                var completed = 0
                var failed = 0
                var lastError: Throwable? = null
                val itemNumberWidth = totalItems.toString().length.coerceAtLeast(2)

                if (isPlaylist) {
                    for ((index, entry) in playlistEntries.withIndex()) {
                        if (cancelRequested || !_state.value.downloading) return@launch

                        val itemNumber = index + 1
                        _state.value = _state.value.copy(
                            currentItem = itemNumber,
                            completedItems = completed,
                            progress = completed.toFloat() / totalItems.toFloat(),
                            etaSeconds = 0,
                            status = "Item $itemNumber de $totalItems • ${entry.title}"
                        )

                        val outputTemplate = "${itemNumber.toString().padStart(itemNumberWidth, '0')} - %(title).42s.%(ext)s"
                        val result = executeWithFallback(
                            url = entry.url,
                            option = option,
                            outputDir = jobDir,
                            outputTemplate = outputTemplate,
                            completedBeforeThisItem = completed,
                            totalItems = totalItems,
                            itemNumber = itemNumber,
                            itemTitle = entry.title
                        )

                        if (cancelRequested || !_state.value.downloading) return@launch

                        if (result.isSuccess) {
                            completed++
                            _state.value = _state.value.copy(
                                completedItems = completed,
                                progress = completed.toFloat() / totalItems.toFloat(),
                                status = "$completed de $totalItems itens baixados"
                            )
                        } else {
                            failed++
                            lastError = result.exceptionOrNull()
                            _state.value = _state.value.copy(
                                status = "Não foi possível baixar o item $itemNumber. Continuando…"
                            )
                        }
                    }
                } else {
                    val result = executeWithFallback(
                        url = cleanUrl,
                        option = option,
                        outputDir = jobDir,
                        outputTemplate = "%(title).46s.%(ext)s",
                        completedBeforeThisItem = 0,
                        totalItems = 1,
                        itemNumber = 1,
                        itemTitle = media?.title.orEmpty()
                    )

                    if (cancelRequested || !_state.value.downloading) return@launch

                    if (result.isFailure) throw result.exceptionOrNull() ?: RuntimeException("Falha no download")
                    completed = 1
                    _state.value = _state.value.copy(completedItems = 1, progress = 1f)
                }

                if (completed == 0 && failed > 0) {
                    throw lastError ?: RuntimeException("Nenhum item da lista pôde ser baixado")
                }

                if (customTree != null) {
                    _state.value = _state.value.copy(status = "Salvando na pasta escolhida…")
                    exportToTree(app, jobDir, customTree)
                    jobDir.deleteRecursively()
                }

                val message = when {
                    isPlaylist && failed == 0 -> "Lista concluída: $completed de $totalItems itens baixados com sucesso."
                    isPlaylist -> "Lista finalizada: $completed de $totalItems itens baixados. $failed item(ns) não puderam ser baixados."
                    else -> "Download concluído com sucesso."
                }

                _state.value = UiState(
                    successMessage = message,
                    customFolder = customTree,
                    resetToken = _state.value.resetToken + 1
                )
            } catch (error: Throwable) {
                if (!cancelRequested) {
                    _state.value = _state.value.copy(
                        downloading = false,
                        error = friendlyError(error),
                        status = ""
                    )
                }
            } finally {
                processId = null
                cancelRequested = false
                if (customTree != null) {
                    runCatching { jobDir.deleteRecursively() }
                } else {
                    jobDir.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".part") || file.name.endsWith(".ytdl")) {
                            file.delete()
                        }
                    }
                }
            }
        }
    }

    private fun executeWithFallback(
        url: String,
        option: DownloadOption,
        outputDir: File,
        outputTemplate: String,
        completedBeforeThisItem: Int,
        totalItems: Int,
        itemNumber: Int,
        itemTitle: String
    ): Result<Unit> {
        val attempts = if (isYouTubeUrl(url)) {
            listOf(
                DownloadAttempt("rota padrão"),
                DownloadAttempt(
                    label = "rota alternativa",
                    playerClients = "web_embedded,tv_downgraded"
                ),
                DownloadAttempt(
                    label = "modo compatibilidade",
                    playerClients = "web_embedded,tv_downgraded",
                    compatibilityMode = true
                ),
                DownloadAttempt(
                    label = "nome seguro",
                    playerClients = "web_embedded,tv_downgraded",
                    compatibilityMode = true,
                    restrictFilenames = true
                )
            )
        } else {
            listOf(
                DownloadAttempt("rota padrão"),
                DownloadAttempt(
                    label = "modo compatibilidade",
                    compatibilityMode = true
                ),
                DownloadAttempt(
                    label = "nome seguro",
                    compatibilityMode = true,
                    restrictFilenames = true
                )
            )
        }

        var finalError: Throwable? = null

        for ((attemptIndex, attempt) in attempts.withIndex()) {
            if (cancelRequested || !_state.value.downloading) {
                return Result.failure(RuntimeException("Download cancelado"))
            }

            if (attemptIndex > 0) {
                outputDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".part") || file.name.endsWith(".ytdl")) file.delete()
                }
            }

            _state.value = _state.value.copy(
                status = if (attemptIndex == 0) {
                    if (totalItems > 1) "Item $itemNumber de $totalItems • $itemTitle" else "Preparando download…"
                } else {
                    if (totalItems > 1) {
                        "Item $itemNumber de $totalItems • Tentando ${attempt.label}…"
                    } else {
                        "Tentando ${attempt.label}…"
                    }
                }
            )

            val currentProcessId = UUID.randomUUID().toString()
            processId = currentProcessId

            val result = runCatching {
                val request = buildDownloadRequest(
                    url = url,
                    option = option,
                    outputDir = outputDir,
                    outputTemplate = outputTemplate,
                    playerClients = attempt.playerClients,
                    compatibilityMode = attempt.compatibilityMode,
                    restrictFilenames = attempt.restrictFilenames
                )

                var lastUiUpdateAt = 0L
                var lastUiProgress = -1f

                YoutubeDL.getInstance().execute(request, currentProcessId) { itemProgress, eta, line ->
                    val overallProgress = if (totalItems > 1) {
                        (completedBeforeThisItem + itemProgress.coerceIn(0f, 100f) / 100f) / totalItems.toFloat()
                    } else {
                        itemProgress.coerceIn(0f, 100f) / 100f
                    }.coerceIn(0f, 1f)

                    val now = SystemClock.elapsedRealtime()
                    val shouldRefreshUi = itemProgress >= 99.5f ||
                        now - lastUiUpdateAt >= PROGRESS_UI_INTERVAL_MS ||
                        abs(overallProgress - lastUiProgress) >= PROGRESS_UI_DELTA

                    if (shouldRefreshUi) {
                        lastUiUpdateAt = now
                        lastUiProgress = overallProgress
                        val compactLine = if (line.isNotBlank()) compactStatus(line) else ""

                        _state.value = _state.value.copy(
                            progress = overallProgress,
                            etaSeconds = eta,
                            status = when {
                                totalItems > 1 && compactLine.isNotBlank() -> "Item $itemNumber de $totalItems • $compactLine"
                                totalItems > 1 -> "Item $itemNumber de $totalItems • $itemTitle"
                                compactLine.isNotBlank() -> compactLine
                                else -> "Baixando…"
                            }
                        )
                    }
                }
                Unit
            }

            processId = null

            if (result.isSuccess) return result

            val error = result.exceptionOrNull() ?: RuntimeException("Falha no download")
            finalError = error
            if (!isRetryableDownloadError(error) || attemptIndex == attempts.lastIndex) break
        }

        return Result.failure(finalError ?: RuntimeException("Falha no download"))
    }

    private fun buildDownloadRequest(
        url: String,
        option: DownloadOption,
        outputDir: File,
        outputTemplate: String,
        playerClients: String?,
        compatibilityMode: Boolean,
        restrictFilenames: Boolean
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
            .addOption("-o", File(outputDir, outputTemplate).absolutePath)
            .addOption("--trim-filenames", "64")
            .addOption("--no-warnings")
            .addOption("--newline")
            .addOption("--no-playlist")
            .addOption("--retries", "3")
            .addOption("--fragment-retries", "3")

        if (restrictFilenames) request.addOption("--restrict-filenames")
        if (playerClients != null) request.addOption("--extractor-args", "youtube:player_client=$playerClients")

        when (option.type) {
            DownloadType.BEST_AV -> {
                request.addOption(
                    "-f",
                    if (compatibilityMode) "b[ext=mp4]/b/bv*+ba/b" else "bestvideo*+bestaudio/best"
                )
                request.addOption("--merge-output-format", if (compatibilityMode) "mp4" else "mkv")
            }

            DownloadType.VIDEO_AUDIO -> {
                val height = option.height
                val ext = option.container
                val selector = when {
                    height != null && ext == "mp4" && !compatibilityMode ->
                        "bv*[height=$height][ext=mp4]+ba[ext=m4a]/b[height=$height][ext=mp4]/bv*[height=$height]+ba/b[height=$height]"
                    height != null && ext == "webm" && !compatibilityMode ->
                        "bv*[height=$height][ext=webm]+ba[ext=webm]/b[height=$height][ext=webm]/bv*[height=$height]+ba/b[height=$height]"
                    height != null && !compatibilityMode ->
                        "bv*[height=$height]+ba/b[height=$height]"
                    height != null && ext == "mp4" ->
                        "b[height<=$height][ext=mp4]/bv*[height<=$height][ext=mp4]+ba[ext=m4a]/bv*[height<=$height]+ba/b"
                    height != null ->
                        "b[height<=$height]/bv*[height<=$height]+ba/b"
                    ext == "mp4" ->
                        "b[ext=mp4]/bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b"
                    else -> "bestvideo*+bestaudio/best"
                }
                request.addOption("-f", selector)
                request.addOption("--merge-output-format", when (ext) {
                    "mp4" -> "mp4"
                    "webm" -> "webm"
                    else -> "mkv"
                })
            }

            DownloadType.VIDEO_ONLY -> {
                val height = option.height
                val ext = option.container
                val selector = when {
                    height != null && ext != null && !compatibilityMode -> "bv*[height=$height][ext=$ext]/bv*[height=$height]"
                    height != null && !compatibilityMode -> "bv*[height=$height]"
                    height != null && ext != null -> "bv*[height<=$height][ext=$ext]/bv*[height<=$height]/bestvideo"
                    height != null -> "bv*[height<=$height]/bestvideo"
                    ext != null -> "bv*[ext=$ext]/bestvideo"
                    else -> "bestvideo/bv"
                }
                request.addOption("-f", selector)
            }

            DownloadType.AUDIO_ORIGINAL -> {
                val selector = option.sourceExt?.let { "ba[ext=$it]/ba/b" } ?: "bestaudio/best"
                request.addOption("-f", selector)
            }

            DownloadType.AUDIO_CONVERT -> {
                request.addOption("-f", if (compatibilityMode) "ba[ext=m4a]/ba/b" else "bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", option.audioFormat ?: "mp3")
                request.addOption("--audio-quality", "0")
            }
        }

        return request
    }

    fun cancel() {
        cancelRequested = true
        processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        processId = null
        _state.value = _state.value.copy(
            downloading = false,
            status = "Download cancelado",
            error = null
        )
    }

    private fun normalizePlaylistEntryUrl(item: JSONObject): String {
        val candidates = listOf(
            item.optString("webpage_url"),
            item.optString("original_url"),
            item.optString("url")
        ).map { it.trim() }.filter { it.isNotBlank() }

        candidates.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }?.let { return it }
        candidates.firstOrNull { it.startsWith("//") }?.let { return "https:$it" }

        val id = item.optString("id").trim()
        val extractor = listOf(
            item.optString("ie_key"),
            item.optString("extractor_key"),
            item.optString("extractor")
        ).joinToString(" ").lowercase(Locale.ROOT)

        val looksLikeYouTube = extractor.contains("youtube") || id.matches(Regex("^[A-Za-z0-9_-]{11}$"))
        return if (looksLikeYouTube && id.isNotBlank()) {
            "https://www.youtube.com/watch?v=$id"
        } else {
            ""
        }
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return host == "youtu.be" ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
    }

    private fun isRetryableDownloadError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase(Locale.ROOT)
        return message.contains("403") ||
            message.contains("forbidden") ||
            message.contains("requested format is not available") ||
            message.contains("unable to download video data") ||
            message.contains("fragment") ||
            message.contains("timeout") ||
            message.contains("timed out") ||
            message.contains("connection reset") ||
            message.contains("file name too long") ||
            message.contains("errno 36") ||
            message.contains("unable to open for writing")
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("File name too long", ignoreCase = true) || raw.contains("Errno 36", ignoreCase = true) ->
                "A plataforma gerou um nome de arquivo incompatível com o Android. O GetMuvi tentou também um nome seguro, mas essa mídia ainda não pôde ser salva."
            raw.contains("login", ignoreCase = true) || raw.contains("cookies", ignoreCase = true) || raw.contains("private", ignoreCase = true) ->
                "Esse conteúdo parece exigir login, cookies ou permissão privada. Use um link público e acessível sem conta."
            raw.contains("403", ignoreCase = true) || raw.contains("Forbidden", ignoreCase = true) ->
                "A plataforma recusou a rota de download (erro 403). O GetMuvi já tentou uma rota alternativa quando disponível. Tente novamente em alguns instantes."
            raw.contains("Requested format is not available", ignoreCase = true) ->
                "Esse formato não está disponível para este link. Escolha outra qualidade ou formato."
            raw.isNotBlank() -> raw
            else -> "Não foi possível concluir o download."
        }
    }

    private fun exportToTree(context: Context, source: File, treeUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Pasta selecionada indisponível")

        source.listFiles()?.filter { it.isFile }?.forEach { file ->
            val mime = when (file.extension.lowercase(Locale.ROOT)) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "opus" -> "audio/opus"
                "ogg" -> "audio/ogg"
                "webm" -> if (file.name.contains("audio", ignoreCase = true)) "audio/webm" else "video/webm"
                "mkv" -> "video/x-matroska"
                else -> "video/mp4"
            }

            root.findFile(file.name)?.delete()
            val target = root.createFile(mime, file.name) ?: error("Não foi possível criar ${file.name}")

            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Não foi possível gravar ${file.name}")
        }
    }

    private fun compactStatus(line: String): String {
        val clean = line.trim().replace(STATUS_WHITESPACE, " ")
        return if (clean.length <= 90) clean else clean.take(87) + "…"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(initialUrl: String, vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf(initialUrl) }
    var kind by remember { mutableStateOf(MediaKind.VIDEO_AUDIO) }
    var selectedOptionId by remember { mutableStateOf("best-av") }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.resetToken) {
        if (state.resetToken > 0) {
            url = ""
            kind = MediaKind.VIDEO_AUDIO
            selectedOptionId = "best-av"
        }
    }

    LaunchedEffect(state.media) {
        state.media?.options?.firstOrNull()?.let { first ->
            kind = first.kind
            selectedOptionId = first.id
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setFolder(uri)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Get",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                "Muvi",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            Color(0xFF20F3E8),
                                            Color(0xFF1597FF),
                                            Color(0xFF765BFF),
                                            Color(0xFFF05CFF)
                                        )
                                    ),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                        Text(
                            "Baixador de músicas e vídeos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { BrandCard() }

            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        vm.clearSuccess()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Cole o link da mídia ou playlist") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let {
                                url = it
                                vm.clearSuccess()
                            }
                        }) {
                            Icon(Icons.Rounded.ContentPaste, "Colar")
                        }
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }

            item {
                Button(
                    onClick = { vm.analyze(url) },
                    enabled = url.isNotBlank() && !state.analyzing && !state.downloading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.analyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Analisando formatos…")
                    } else {
                        Icon(Icons.Rounded.Search, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Analisar link")
                    }
                }
            }

            state.successMessage?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedButton(
                                onClick = { openDownloadFolder(context, state.customFolder) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Folder, null)
                                Spacer(Modifier.size(8.dp))
                                Text("Abrir pasta")
                            }
                        }
                    }
                }
            }

            state.error?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            state.media?.let { media ->
                item { MediaCard(media) }

                item {
                    Text("O que você quer baixar?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MediaKind.entries.forEach { itemKind ->
                            val enabled = media.options.any { it.kind == itemKind }
                            FilterChip(
                                selected = kind == itemKind,
                                onClick = {
                                    kind = itemKind
                                    media.options.firstOrNull { it.kind == itemKind }?.let { selectedOptionId = it.id }
                                },
                                enabled = enabled,
                                label = { Text(itemKind.title) },
                                leadingIcon = {
                                    Icon(
                                        when (itemKind) {
                                            MediaKind.VIDEO_AUDIO -> Icons.Rounded.Download
                                            MediaKind.VIDEO_ONLY -> Icons.Rounded.Movie
                                            MediaKind.AUDIO -> Icons.Rounded.AudioFile
                                        },
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                val visibleOptions = media.options.filter { it.kind == kind }
                item {
                    Text(
                        when (kind) {
                            MediaKind.VIDEO_AUDIO -> "Qualidade e formato"
                            MediaKind.VIDEO_ONLY -> "Qualidade do vídeo"
                            MediaKind.AUDIO -> "Formato do áudio"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(visibleOptions, key = { it.id }) { option ->
                    DownloadOptionCard(
                        option = option,
                        selected = option.id == selectedOptionId,
                        onClick = { selectedOptionId = option.id }
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("Pasta de download", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (state.customFolder == null) "Downloads/GetMuvi" else "Pasta personalizada selecionada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { folderPicker.launch(null) }, enabled = !state.downloading) {
                                Text("Alterar")
                            }
                        }
                    }
                }

                item {
                    if (state.downloading) {
                        DownloadProgress(state = state, onCancel = vm::cancel)
                    } else {
                        val selectedOption = media.options.firstOrNull { it.id == selectedOptionId }
                        Button(
                            onClick = { selectedOption?.let { vm.download(url, it) } },
                            enabled = selectedOption != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.size(8.dp))
                            Text(if (media.playlistCount > 0) "Baixar lista (${media.playlistCount})" else "Baixar agora")
                        }
                    }
                }

                if (media.playlistEntries.isNotEmpty()) {
                    item {
                        Text("Itens encontrados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(media.playlistEntries.take(12)) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (state.media == null && !state.analyzing) {
                item { EmptyState() }
            }
        }
    }
}

@Composable
private fun BrandCard() {
    val gradient = Brush.linearGradient(
        listOf(Color(0xFF19E7DE), Color(0xFF098FFF), Color(0xFF654CFF), Color(0xFFE752FF))
    )
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient, RoundedCornerShape(28.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.getmuvi_icon),
                contentDescription = "GetMuvi",
                modifier = Modifier.size(72.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Baixe do seu jeito", fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(
                    "Escolha vídeo, áudio, qualidade e formato conforme a mídia oferecer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun MediaCard(media: MediaSummary) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    if (media.playlistCount > 0) Icons.Rounded.PlaylistPlay else Icons.Rounded.Movie,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (media.playlistCount > 0) "Lista encontrada" else "Mídia encontrada",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                media.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (media.uploader.isNotBlank()) Text(media.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val details = buildList {
                media.durationSeconds?.let { add(formatDuration(it)) }
                if (media.playlistCount > 0) add("${media.playlistCount} itens")
                if (media.playlistCount == 0) add("${media.options.count { it.kind == MediaKind.VIDEO_AUDIO }} opções de vídeo")
            }.joinToString(" • ")

            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DownloadOptionCard(option: DownloadOption, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        label = {
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(option.title, fontWeight = FontWeight.SemiBold)
                Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingIcon = {
            Icon(
                when (option.kind) {
                    MediaKind.VIDEO_AUDIO -> Icons.Rounded.Download
                    MediaKind.VIDEO_ONLY -> Icons.Rounded.Movie
                    MediaKind.AUDIO -> Icons.Rounded.AudioFile
                },
                null
            )
        }
    )
}

@Composable
private fun DownloadProgress(state: UiState, onCancel: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.totalItems > 1) "Baixando lista" else "Baixando",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text("${(state.progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }

            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())

            if (state.totalItems > 1) {
                Text(
                    "${state.completedItems} de ${state.totalItems} itens concluídos",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(state.status, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)

            if (state.etaSeconds > 0 && state.totalItems <= 1) {
                Text("Tempo estimado: ${formatDuration(state.etaSeconds)}", style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Cancel, null)
                Spacer(Modifier.size(8.dp))
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text("Cole um link para começar", fontWeight = FontWeight.SemiBold)
            Text(
                "O GetMuvi analisa a mídia e mostra somente as opções encontradas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildOptionSubtitle(label: String, bytes: Long?): String =
    if (bytes != null) "$label · aprox. ${formatBytes(bytes)}" else label

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    else String.format(Locale.getDefault(), "%.0f MB", mb)
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val GetMuviColors = darkColorScheme(
    primary = Color(0xFF25D9D0),
    onPrimary = Color(0xFF001F1E),
    primaryContainer = Color(0xFF103A45),
    onPrimaryContainer = Color(0xFFD3FAFF),
    secondary = Color(0xFF7A9FFF),
    secondaryContainer = Color(0xFF243663),
    background = Color(0xFF070B18),
    surface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFF151B2E),
    onSurface = Color(0xFFF5F7FF),
    onSurfaceVariant = Color(0xFFB9C1D8)
)

@Composable
fun GetMuviTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GetMuviColors, content = content)
}
