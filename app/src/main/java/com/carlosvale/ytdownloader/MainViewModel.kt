package com.carlosvale.ytdownloader

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("getmuvi", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState(history = loadHistory()))
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

    fun clearHistory() {
        preferences.edit().remove("download_history").apply()
        _state.value = _state.value.copy(history = emptyList())
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
                app.ensureEngineReady()
                _state.value = _state.value.copy(status = "Analisando link e formatos…")

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

                var formatInfo = parseFormatInfo(json)
                var formatNote = ""

                if (playlistEntries.isNotEmpty()) {
                    _state.value = _state.value.copy(status = "Verificando qualidades da lista…")
                    val firstItemInfo = runCatching {
                        val firstJson = loadDetailedJson(playlistEntries.first().url)
                        parseFormatInfo(firstJson)
                    }.getOrNull()

                    if (firstItemInfo != null) {
                        formatInfo = firstItemInfo
                        formatNote = "Qualidades baseadas no primeiro item da lista; podem variar entre os vídeos."
                    }
                }

                MediaSummary(
                    title = json.optString("title", "Mídia encontrada"),
                    uploader = json.optString("uploader", json.optString("channel", "")),
                    durationSeconds = if (json.has("duration") && !json.isNull("duration")) json.optLong("duration") else null,
                    playlistCount = playlistEntries.size,
                    entries = playlistEntries.map { it.title }.take(12),
                    playlistEntries = playlistEntries,
                    videoHeights = formatInfo.videoHeights,
                    audioSourceFormats = formatInfo.audioSourceFormats,
                    hasVideo = formatInfo.hasVideo,
                    hasAudio = formatInfo.hasAudio,
                    formatNote = formatNote
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

    private fun loadDetailedJson(url: String): JSONObject {
        val request = YoutubeDLRequest(url)
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption("--no-warnings")
            .addOption("--no-playlist")

        if (isYouTubeUrl(url)) {
            request.addOption(
                "--extractor-args",
                "youtube:player_client=default,web_embedded,tv_downgraded"
            )
        }

        return JSONObject(YoutubeDL.getInstance().execute(request).out.trim())
    }

    private fun parseFormatInfo(json: JSONObject): FormatInfo {
        val formats = json.optJSONArray("formats")
        val heights = linkedSetOf<Int>()
        val audioFormats = linkedSetOf<String>()
        var hasVideo = false
        var hasAudio = false

        if (formats != null) {
            for (i in 0 until formats.length()) {
                val format = formats.optJSONObject(i) ?: continue
                val vcodec = format.optString("vcodec").lowercase()
                val acodec = format.optString("acodec").lowercase()
                val ext = format.optString("ext").lowercase()
                val height = format.optInt("height", 0)

                if (vcodec.isNotBlank() && vcodec != "none") {
                    hasVideo = true
                    if (height > 0) heights += height
                }
                if (acodec.isNotBlank() && acodec != "none") {
                    hasAudio = true
                    if (ext.isNotBlank()) audioFormats += ext
                }
            }
        }

        val rootVcodec = json.optString("vcodec").lowercase()
        val rootAcodec = json.optString("acodec").lowercase()
        val rootHeight = json.optInt("height", 0)
        val rootExt = json.optString("ext").lowercase()

        if (rootVcodec.isNotBlank() && rootVcodec != "none") {
            hasVideo = true
            if (rootHeight > 0) heights += rootHeight
        }
        if (rootAcodec.isNotBlank() && rootAcodec != "none") {
            hasAudio = true
            if (rootExt.isNotBlank()) audioFormats += rootExt
        }

        if (formats == null && !hasVideo && !hasAudio) {
            hasVideo = true
            hasAudio = true
        }

        return FormatInfo(
            videoHeights = heights.filter { it >= 144 }.distinct().sortedDescending(),
            audioSourceFormats = audioFormats.distinct().sorted(),
            hasVideo = hasVideo,
            hasAudio = hasAudio
        )
    }

    fun download(url: String, selection: DownloadSelection) {
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
                app.ensureEngineReady()

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
                            selection = selection,
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
                        selection = selection,
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

                val updatedHistory = addHistory(
                    title = media?.title.orEmpty().ifBlank { "Download" },
                    option = selectionLabel(selection),
                    itemCount = completed
                )
                val nextReset = _state.value.resetToken + 1
                val savedFolder = _state.value.customFolder

                _state.value = UiState(
                    successMessage = message,
                    customFolder = savedFolder,
                    history = updatedHistory,
                    resetToken = nextReset
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
            }
        }
    }

    private fun executeWithFallback(
        url: String,
        selection: DownloadSelection,
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
                    selection = selection,
                    outputDir = outputDir,
                    outputTemplate = outputTemplate,
                    playerClients = attempt.playerClients,
                    compatibilityMode = attempt.compatibilityMode,
                    restrictFilenames = attempt.restrictFilenames
                )

                YoutubeDL.getInstance().execute(request, currentProcessId) { itemProgress, eta, line ->
                    val overallProgress = if (totalItems > 1) {
                        (completedBeforeThisItem + itemProgress.coerceIn(0f, 100f) / 100f) / totalItems.toFloat()
                    } else {
                        itemProgress.coerceIn(0f, 100f) / 100f
                    }

                    _state.value = _state.value.copy(
                        progress = overallProgress.coerceIn(0f, 1f),
                        etaSeconds = eta,
                        status = when {
                            totalItems > 1 && line.isNotBlank() -> "Item $itemNumber de $totalItems • ${compactStatus(line)}"
                            totalItems > 1 -> "Item $itemNumber de $totalItems • $itemTitle"
                            line.isNotBlank() -> compactStatus(line)
                            else -> "Baixando…"
                        }
                    )
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
        selection: DownloadSelection,
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

        if (restrictFilenames) {
            request.addOption("--restrict-filenames")
        }

        if (playerClients != null) {
            request.addOption("--extractor-args", "youtube:player_client=$playerClients")
        }

        val heightFilter = selection.videoHeight?.let { "[height<=$it]" }.orEmpty()

        when (selection.kind) {
            MediaKind.VIDEO_AUDIO -> {
                if (selection.videoContainer == VideoContainer.MP4) {
                    val selector = if (compatibilityMode) {
                        "b$heightFilter[ext=mp4]/b$heightFilter/b"
                    } else {
                        "bv*$heightFilter[ext=mp4]+ba[ext=m4a]/b$heightFilter[ext=mp4]/bv*$heightFilter+ba/b$heightFilter/b"
                    }
                    request.addOption("-f", selector)
                    request.addOption("--merge-output-format", "mp4")
                } else {
                    request.addOption(
                        "-f",
                        if (compatibilityMode) "b$heightFilter/b" else "bv*$heightFilter+ba/b$heightFilter/b"
                    )
                    request.addOption("--merge-output-format", "mkv")
                }
            }

            MediaKind.VIDEO_ONLY -> {
                if (selection.videoContainer == VideoContainer.MP4) {
                    request.addOption(
                        "-f",
                        "bv*$heightFilter[ext=mp4]/bestvideo$heightFilter[ext=mp4]/bv*$heightFilter/bestvideo$heightFilter"
                    )
                    request.addOption("--remux-video", "mp4")
                } else {
                    request.addOption("-f", "bv*$heightFilter/bestvideo$heightFilter")
                }
            }

            MediaKind.AUDIO_ONLY -> {
                when (selection.audioTarget) {
                    AudioTarget.WEBM -> {
                        request.addOption("-f", "ba[ext=webm]/bestaudio[ext=webm]/bestaudio/best")
                    }
                    AudioTarget.ORIGINAL -> {
                        request.addOption("-f", if (compatibilityMode) "ba[ext=m4a]/ba/b" else "bestaudio/best")
                        request.addOption("-x")
                    }
                    AudioTarget.MP3 -> {
                        request.addOption("-f", if (compatibilityMode) "ba[ext=m4a]/ba/b" else "bestaudio/best")
                        request.addOption("-x")
                        request.addOption("--audio-format", "mp3")
                        request.addOption("--audio-quality", "0")
                    }
                    AudioTarget.M4A -> {
                        request.addOption("-f", if (compatibilityMode) "ba[ext=m4a]/ba/b" else "bestaudio/best")
                        request.addOption("-x")
                        request.addOption("--audio-format", "m4a")
                        request.addOption("--audio-quality", "0")
                    }
                    AudioTarget.OPUS -> {
                        request.addOption("-f", if (compatibilityMode) "ba/ba[ext=m4a]/b" else "bestaudio/best")
                        request.addOption("-x")
                        request.addOption("--audio-format", "opus")
                        request.addOption("--audio-quality", "0")
                    }
                }
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

    private fun selectionLabel(selection: DownloadSelection): String {
        return when (selection.kind) {
            MediaKind.AUDIO_ONLY -> "Áudio • ${selection.audioTarget.title}"
            MediaKind.VIDEO_ONLY -> {
                val quality = selection.videoHeight?.let { "${it}p" } ?: "Melhor qualidade"
                "Vídeo • $quality • ${selection.videoContainer.title}"
            }
            MediaKind.VIDEO_AUDIO -> {
                val quality = selection.videoHeight?.let { "${it}p" } ?: "Melhor qualidade"
                "Vídeo + áudio • $quality • ${selection.videoContainer.title}"
            }
        }
    }

    private fun addHistory(title: String, option: String, itemCount: Int): List<DownloadHistoryItem> {
        val updated = listOf(
            DownloadHistoryItem(
                title = title,
                option = option,
                timestamp = System.currentTimeMillis(),
                itemCount = itemCount
            )
        ) + _state.value.history

        val limited = updated.take(20)
        val array = JSONArray()
        limited.forEach { item ->
            array.put(
                JSONObject()
                    .put("title", item.title)
                    .put("option", item.option)
                    .put("timestamp", item.timestamp)
                    .put("itemCount", item.itemCount)
            )
        }
        preferences.edit().putString("download_history", array.toString()).apply()
        return limited
    }

    private fun loadHistory(): List<DownloadHistoryItem> {
        val raw = preferences.getString("download_history", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        DownloadHistoryItem(
                            title = item.optString("title", "Download"),
                            option = item.optString("option", ""),
                            timestamp = item.optLong("timestamp", 0L),
                            itemCount = item.optInt("itemCount", 1)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
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
        ).joinToString(" ").lowercase()

        val looksLikeYouTube = extractor.contains("youtube") || id.matches(Regex("^[A-Za-z0-9_-]{11}$"))
        return if (looksLikeYouTube && id.isNotBlank()) {
            "https://www.youtube.com/watch?v=$id"
        } else {
            ""
        }
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "youtu.be" ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtube-nocookie.com" ||
            host.endsWith(".youtube-nocookie.com")
    }

    private fun isRetryableDownloadError(error: Throwable): Boolean {
        val message = error.message.orEmpty().lowercase()
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
            raw.contains("File name too long", ignoreCase = true) ||
                raw.contains("Errno 36", ignoreCase = true) ->
                "A plataforma gerou um nome de arquivo incompatível com o Android. O GetMuvi tentou também um nome seguro, mas essa mídia ainda não pôde ser salva."

            raw.contains("login", ignoreCase = true) ||
                raw.contains("cookies", ignoreCase = true) ||
                raw.contains("private", ignoreCase = true) ->
                "Esse conteúdo parece exigir login, cookies ou permissão privada. Use um link público e acessível sem conta."

            raw.contains("403", ignoreCase = true) || raw.contains("Forbidden", ignoreCase = true) ->
                "A plataforma recusou a rota de download (erro 403). O app já tentou rotas alternativas quando disponíveis. Tente novamente em alguns instantes."

            raw.contains("Requested format is not available", ignoreCase = true) ->
                "Essa qualidade ou formato não está disponível para este link. Analise novamente e escolha outra opção."

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
                "aac" -> "audio/aac"
                "opus" -> "audio/opus"
                "ogg" -> "audio/ogg"
                "wav" -> "audio/wav"
                "webm" -> if (file.name.contains("video", ignoreCase = true)) "video/webm" else "audio/webm"
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
