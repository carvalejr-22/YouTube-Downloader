from pathlib import Path
import re

path = Path("app/src/main/java/com/carlosvale/ytdownloader/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if old not in text:
        raise SystemExit(f"Trecho não encontrado para {label}; atualização interrompida por segurança.")
    text = text.replace(old, new, 1)


# Imports usados pelo limitador de atualizações da interface.
if "import android.os.SystemClock" not in text:
    replace_once(
        "import android.os.Environment\n",
        "import android.os.Environment\nimport android.os.SystemClock\n",
        "SystemClock"
    )
if "import kotlin.math.abs" not in text:
    replace_once(
        "import java.util.UUID\n",
        "import java.util.UUID\nimport kotlin.math.abs\n",
        "kotlin.math.abs"
    )

# Evita manter uma segunda lista de títulos da playlist na RAM.
text = text.replace("    val entries: List<String> = emptyList(),\n", "", 1)
text = text.replace("                    entries = playlistEntries.map { it.title }.take(12),\n", "", 1)

# Descritor mínimo: guardamos apenas o necessário para construir as opções.
old_descriptor = '''private data class FormatDescriptor(
    val height: Int?,
    val ext: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val bytes: Long?
)
'''
new_descriptor = '''private data class FormatDescriptor(
    val ext: String,
    val bytes: Long?
)

private const val PROGRESS_UI_INTERVAL_MS = 250L
private const val PROGRESS_UI_DELTA = 0.01f
private val STATUS_WHITESPACE = Regex("\\\\s+")
'''
replace_once(old_descriptor, new_descriptor, "descritor de formatos")

# Processa formatos em grupos por resolução sem manter listas duplicadas.
pattern = re.compile(
    r"    private fun buildDownloadOptions\(formatsArray: JSONArray\?, isPlaylist: Boolean\): List<DownloadOption> \{.*?\n    \}\n\n    private fun playlistFallbackOptions",
    re.S,
)
new_builder = '''    private fun buildDownloadOptions(formatsArray: JSONArray?, isPlaylist: Boolean): List<DownloadOption> {
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

    private fun playlistFallbackOptions'''
text, count = pattern.subn(new_builder, text, count=1)
if count != 1:
    raise SystemExit("Função buildDownloadOptions não pôde ser otimizada com segurança.")

# Inicialização pesada sob demanda.
replace_once(
    "                app.ensureEngineReady()\n                _state.value = _state.value.copy(status = \"Analisando mídia e formatos…\")",
    "                app.ensureDownloaderReady()\n                _state.value = _state.value.copy(status = \"Analisando mídia e formatos…\")",
    "motor na análise"
)
replace_once(
    "                app.ensureEngineReady()\n\n                var completed = 0",
    '''                app.ensureDownloaderReady()
                if (
                    option.type == DownloadType.BEST_AV ||
                    option.type == DownloadType.VIDEO_AUDIO ||
                    option.type == DownloadType.AUDIO_CONVERT
                ) {
                    app.ensureFfmpegReady()
                }

                var completed = 0''',
    "motor no download"
)

# Mantém a pasta escolhida após o download, permitindo abrir o destino.
replace_once(
    '''                _state.value = UiState(
                    successMessage = message,
                    resetToken = _state.value.resetToken + 1
                )''',
    '''                _state.value = UiState(
                    successMessage = message,
                    customFolder = customTree,
                    resetToken = _state.value.resetToken + 1
                )''',
    "estado de sucesso"
)

# Limita recomposições: yt-dlp pode emitir muitas linhas por segundo.
old_progress = '''                YoutubeDL.getInstance().execute(request, currentProcessId) { itemProgress, eta, line ->
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
                }'''
new_progress = '''                var lastUiUpdateAt = 0L
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
                }'''
replace_once(old_progress, new_progress, "throttle de progresso")

# Regex compilada uma vez em vez de ser recriada a cada linha de progresso.
replace_once(
    '        val clean = line.trim().replace(Regex("\\\\s+"), " ")',
    '        val clean = line.trim().replace(STATUS_WHITESPACE, " ")',
    "compactação de status"
)

# Limpa arquivos temporários também em falhas/cancelamentos.
replace_once(
    '''            } finally {
                processId = null
                cancelRequested = false
            }''',
    '''            } finally {
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
            }''',
    "limpeza de temporários"
)

# Botão para abrir a pasta junto à confirmação de download.
old_success = '''            state.successMessage?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }'''
new_success = '''            state.successMessage?.let { message ->
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
            }'''
replace_once(old_success, new_success, "botão Abrir pasta")

# A lista exibida reutiliza PlaylistEntry; não mantém títulos duplicados.
old_playlist_ui = '''                if (media.entries.isNotEmpty()) {
                    item {
                        Text("Itens encontrados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(media.entries) { title ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }'''
new_playlist_ui = '''                if (media.playlistEntries.isNotEmpty()) {
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
                }'''
replace_once(old_playlist_ui, new_playlist_ui, "lista de playlist")

path.write_text(text, encoding="utf-8")
print("GetMuvi 0.3.2: otimizações aplicadas com sucesso.")
