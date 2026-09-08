package com.carlosvale.ytdownloader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
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
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedUrl = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        setContent {
            YTDownloaderTheme {
                DownloaderScreen(initialUrl = sharedUrl.orEmpty())
            }
        }
    }
}

enum class DownloadMode(val title: String, val subtitle: String) {
    BEST_AV("Máxima qualidade", "Vídeo + áudio na melhor qualidade disponível"),
    MP4_AV("Máxima em MP4", "Prioriza vídeo MP4 + áudio M4A"),
    VIDEO_ONLY("Só vídeo · máxima", "Melhor faixa de vídeo, sem áudio"),
    VIDEO_MP4("Só vídeo · MP4", "Melhor vídeo possível em MP4"),
    AUDIO_ONLY("Só áudio · original", "Preserva o melhor formato de áudio"),
    AUDIO_MP3("Só áudio · MP3", "Converte o melhor áudio para MP3")
}

data class MediaSummary(
    val title: String,
    val uploader: String = "",
    val durationSeconds: Long? = null,
    val playlistCount: Int = 0,
    val entries: List<String> = emptyList()
)

data class UiState(
    val analyzing: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val etaSeconds: Long = 0,
    val status: String = "",
    val media: MediaSummary? = null,
    val error: String? = null,
    val customFolder: Uri? = null
)

private data class DownloadAttempt(
    val label: String,
    val playerClients: String? = null,
    val compatibilityMode: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var processId: String? = null

    fun setFolder(uri: Uri?) {
        _state.value = _state.value.copy(customFolder = uri)
    }

    fun analyze(url: String) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            _state.value = _state.value.copy(error = "Cole um link válido.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(
                analyzing = true,
                error = null,
                media = null,
                status = "Atualizando mecanismo…"
            )

            runCatching {
                val app = getApplication<YTDownloaderApp>()
                app.ensureEngineReady()
                _state.value = _state.value.copy(status = "Analisando link…")

                val request = YoutubeDLRequest(cleanUrl)
                    .addOption("--dump-single-json")
                    .addOption("--flat-playlist")
                    .addOption("--skip-download")
                    .addOption("--no-warnings")
                    .addOption("--extractor-args", "youtube:player_client=default,web_embedded,tv_downgraded")

                val output = YoutubeDL.getInstance().execute(request).out.trim()
                val json = JSONObject(output)
                val entriesArray = json.optJSONArray("entries")
                val entries = mutableListOf<String>()

                if (entriesArray != null) {
                    for (i in 0 until entriesArray.length()) {
                        val item = entriesArray.optJSONObject(i)
                        val title = item?.optString("title").orEmpty()
                        if (title.isNotBlank()) entries += title
                    }
                }

                MediaSummary(
                    title = json.optString("title", "Mídia encontrada"),
                    uploader = json.optString("uploader", json.optString("channel", "")),
                    durationSeconds = if (json.has("duration") && !json.isNull("duration")) json.optLong("duration") else null,
                    playlistCount = entriesArray?.length() ?: 0,
                    entries = entries.take(12)
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

    fun download(url: String, mode: DownloadMode) {
        if (_state.value.downloading) return
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return

        val app = getApplication<YTDownloaderApp>()
        viewModelScope.launch(Dispatchers.IO) {
            val customTree = _state.value.customFolder
            val jobDir = if (customTree == null) {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "YT Downloader")
            } else {
                File(app.cacheDir, "export-${System.currentTimeMillis()}")
            }

            jobDir.mkdirs()
            _state.value = _state.value.copy(
                downloading = true,
                progress = 0f,
                error = null,
                status = "Atualizando mecanismo…"
            )

            val attempts = listOf(
                DownloadAttempt("rota padrão"),
                DownloadAttempt(
                    label = "rota alternativa",
                    playerClients = "web_embedded,tv_downgraded"
                ),
                DownloadAttempt(
                    label = "modo compatibilidade",
                    playerClients = "web_embedded,tv_downgraded",
                    compatibilityMode = true
                )
            )

            var finalError: Throwable? = null
            var succeeded = false

            try {
                app.ensureEngineReady()

                for ((index, attempt) in attempts.withIndex()) {
                    if (!_state.value.downloading) break

                    if (index > 0) {
                        jobDir.listFiles()?.forEach { file ->
                            if (file.name.endsWith(".part") || file.name.endsWith(".ytdl")) file.delete()
                        }
                    }

                    _state.value = _state.value.copy(
                        progress = 0f,
                        status = if (index == 0) "Preparando download…" else "Tentando ${attempt.label}…"
                    )

                    val currentProcessId = UUID.randomUUID().toString()
                    processId = currentProcessId

                    val result = runCatching {
                        val request = buildDownloadRequest(
                            url = cleanUrl,
                            mode = mode,
                            outputDir = jobDir,
                            playerClients = attempt.playerClients,
                            compatibilityMode = attempt.compatibilityMode
                        )

                        YoutubeDL.getInstance().execute(request, currentProcessId) { progress, eta, line ->
                            _state.value = _state.value.copy(
                                progress = progress.coerceIn(0f, 100f) / 100f,
                                etaSeconds = eta,
                                status = if (line.isBlank()) "Baixando…" else compactStatus(line)
                            )
                        }
                    }

                    processId = null

                    if (result.isSuccess) {
                        succeeded = true
                        break
                    }

                    val error = result.exceptionOrNull() ?: RuntimeException("Falha no download")
                    finalError = error

                    if (!isRetryableYouTubeError(error) || index == attempts.lastIndex) {
                        break
                    }
                }

                if (!succeeded) throw finalError ?: RuntimeException("Falha no download")

                if (customTree != null) {
                    _state.value = _state.value.copy(status = "Salvando na pasta escolhida…")
                    exportToTree(app, jobDir, customTree)
                    jobDir.deleteRecursively()
                }

                _state.value = _state.value.copy(
                    downloading = false,
                    progress = 1f,
                    status = "Download concluído",
                    error = null
                )
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    downloading = false,
                    error = friendlyError(error),
                    status = ""
                )
            } finally {
                processId = null
            }
        }
    }

    private fun buildDownloadRequest(
        url: String,
        mode: DownloadMode,
        outputDir: File,
        playerClients: String?,
        compatibilityMode: Boolean
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
            .addOption("-o", File(outputDir, "%(title)s.%(ext)s").absolutePath)
            .addOption("--no-warnings")
            .addOption("--newline")
            .addOption("--retries", "3")
            .addOption("--fragment-retries", "3")

        if (playerClients != null) {
            request.addOption("--extractor-args", "youtube:player_client=$playerClients")
        }

        when (mode) {
            DownloadMode.BEST_AV -> {
                request.addOption(
                    "-f",
                    if (compatibilityMode) "b[ext=mp4]/b/bv*+ba/b" else "bestvideo*+bestaudio/best"
                )
                request.addOption("--merge-output-format", if (compatibilityMode) "mp4" else "mkv")
            }

            DownloadMode.MP4_AV -> {
                request.addOption(
                    "-f",
                    if (compatibilityMode) {
                        "b[ext=mp4]/bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b"
                    } else {
                        "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b"
                    }
                )
                request.addOption("--merge-output-format", "mp4")
            }

            DownloadMode.VIDEO_ONLY -> request.addOption(
                "-f",
                if (compatibilityMode) "bv*[protocol^=http]/bestvideo*" else "bestvideo*"
            )

            DownloadMode.VIDEO_MP4 -> {
                request.addOption(
                    "-f",
                    if (compatibilityMode) {
                        "bv*[ext=mp4][protocol^=http]/bv*[ext=mp4]/bestvideo*"
                    } else {
                        "bestvideo*[ext=mp4]/bestvideo*"
                    }
                )
                request.addOption("--remux-video", "mp4")
            }

            DownloadMode.AUDIO_ONLY -> request.addOption(
                "-f",
                if (compatibilityMode) "ba[ext=m4a]/ba/b" else "bestaudio/best"
            )

            DownloadMode.AUDIO_MP3 -> {
                request.addOption(
                    "-f",
                    if (compatibilityMode) "ba[ext=m4a]/ba/b" else "bestaudio/best"
                )
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
            }
        }

        return request
    }

    fun cancel() {
        processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        processId = null
        _state.value = _state.value.copy(
            downloading = false,
            status = "Download cancelado"
        )
    }

    private fun isRetryableYouTubeError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
        return message.contains("403") ||
            message.contains("forbidden") ||
            message.contains("requested format is not available") ||
            message.contains("unable to download video data") ||
            message.contains("fragment")
    }

    private fun friendlyError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("403", ignoreCase = true) || raw.contains("Forbidden", ignoreCase = true) ->
                "O YouTube recusou as rotas disponíveis para este vídeo (erro 403). O app já tentou rotas alternativas automaticamente. Tente novamente em alguns instantes ou teste outro vídeo."

            raw.contains("Requested format is not available", ignoreCase = true) ->
                "Essa qualidade não está disponível por uma rota compatível. Tente outra opção de formato."

            raw.isNotBlank() -> raw
            else -> "Não foi possível concluir o download."
        }
    }

    private fun exportToTree(context: Context, source: File, treeUri: Uri) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Pasta selecionada indisponível")

        source.listFiles()?.filter { it.isFile }?.forEach { file ->
            val mime = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                else -> "video/mp4"
            }

            root.findFile(file.name)?.delete()
            val target = root.createFile(mime, file.name)
                ?: error("Não foi possível criar ${file.name}")

            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Não foi possível gravar ${file.name}")
        }
    }

    private fun compactStatus(line: String): String {
        val clean = line.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= 90) clean else clean.take(87) + "…"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(initialUrl: String, vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf(initialUrl) }
    var mode by remember { mutableStateOf(DownloadMode.MP4_AV) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
                title = {
                    Column {
                        Text("YT Downloader", fontWeight = FontWeight.Bold)
                        Text(
                            "Vídeo, áudio e playlists",
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
            item {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Link do vídeo ou playlist") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.text?.let { url = it } }) {
                            Icon(Icons.Rounded.ContentPaste, "Colar")
                        }
                    },
                    shape = RoundedCornerShape(18.dp)
                )
            }

            item {
                Button(
                    onClick = { vm.analyze(url) },
                    enabled = url.isNotBlank() && !state.analyzing && !state.downloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.analyzing) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.padding(4.dp))
                        Text("Analisando…")
                    } else {
                        Icon(Icons.Rounded.Search, null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Analisar link")
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
                    Text("Formato", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DownloadMode.entries.forEach { itemMode ->
                            ModeChip(itemMode, selected = mode == itemMode) { mode = itemMode }
                        }
                    }
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
                            Icon(Icons.Rounded.Folder, null)
                            Column(Modifier.weight(1f)) {
                                Text("Pasta de download", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (state.customFolder == null) "Downloads/YT Downloader" else "Pasta personalizada selecionada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { folderPicker.launch(null) },
                                enabled = !state.downloading
                            ) {
                                Text("Alterar")
                            }
                        }
                    }
                }

                item {
                    if (state.downloading) {
                        DownloadProgress(state = state, onCancel = vm::cancel)
                    } else {
                        Button(
                            onClick = { vm.download(url, mode) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.padding(4.dp))
                            Text(if (media.playlistCount > 0) "Baixar playlist (${media.playlistCount})" else "Baixar")
                        }
                    }
                }

                if (media.entries.isNotEmpty()) {
                    item {
                        Text(
                            "Itens da playlist",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(media.entries) { title ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlaylistPlay,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun MediaCard(media: MediaSummary) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (media.playlistCount > 0) Icons.Rounded.PlaylistPlay else Icons.Rounded.Movie,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    if (media.playlistCount > 0) "Playlist encontrada" else "Vídeo encontrado",
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

            if (media.uploader.isNotBlank()) {
                Text(media.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val details = buildList {
                media.durationSeconds?.let { add(formatDuration(it)) }
                if (media.playlistCount > 0) add("${media.playlistCount} vídeos")
            }.joinToString(" • ")

            if (details.isNotBlank()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModeChip(mode: DownloadMode, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Column(Modifier.padding(vertical = 5.dp)) {
                Text(mode.title, fontWeight = FontWeight.SemiBold)
                Text(
                    mode.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingIcon = {
            Icon(
                if (mode == DownloadMode.AUDIO_ONLY || mode == DownloadMode.AUDIO_MP3) {
                    Icons.Rounded.AudioFile
                } else {
                    Icons.Rounded.Movie
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
                Text("Baixando", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${(state.progress * 100).toInt()}%", fontWeight = FontWeight.Bold)
            }

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (state.etaSeconds > 0) {
                Text(
                    "Tempo estimado: ${formatDuration(state.etaSeconds)}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Cancel, null)
                Spacer(Modifier.padding(4.dp))
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
            Text("Cole um link e toque em Analisar", fontWeight = FontWeight.SemiBold)
            Text(
                "O app identifica vídeo ou playlist e mostra as opções de saída.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun YTDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
