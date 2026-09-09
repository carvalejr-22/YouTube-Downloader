package com.carlosvale.ytdownloader

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoFile
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(initialUrl: String, vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf(initialUrl) }
    var kind by remember { mutableStateOf(MediaKind.VIDEO_AUDIO) }
    var videoHeight by remember { mutableStateOf<Int?>(null) }
    var videoContainer by remember { mutableStateOf(VideoContainer.MP4) }
    var audioTarget by remember { mutableStateOf(AudioTarget.MP3) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(state.resetToken) {
        if (state.resetToken > 0) {
            url = ""
            kind = MediaKind.VIDEO_AUDIO
            videoHeight = null
            videoContainer = VideoContainer.MP4
            audioTarget = AudioTarget.MP3
        }
    }

    LaunchedEffect(state.media) {
        state.media?.let { media ->
            kind = when {
                media.hasVideo && media.hasAudio -> MediaKind.VIDEO_AUDIO
                media.hasVideo -> MediaKind.VIDEO_ONLY
                else -> MediaKind.AUDIO_ONLY
            }
            videoHeight = null
            videoContainer = VideoContainer.MP4
            audioTarget = AudioTarget.MP3
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.getmuvi_logo),
                            contentDescription = "GetMuvi",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                        )
                        Column {
                            Text("GetMuvi", fontWeight = FontWeight.ExtraBold)
                            Text(
                                "Baixador de músicas e vídeos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { BrandHero() }

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
                    placeholder = { Text("https://…") },
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
                    shape = RoundedCornerShape(18.dp)
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
                        Text("Analisar link", fontWeight = FontWeight.Bold)
                    }
                }
            }

            state.successMessage?.let { message ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null)
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
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
                    SelectionCard(
                        media = media,
                        kind = kind,
                        onKindChange = {
                            kind = it
                            videoHeight = null
                        },
                        videoHeight = videoHeight,
                        onVideoHeightChange = { videoHeight = it },
                        videoContainer = videoContainer,
                        onVideoContainerChange = { videoContainer = it },
                        audioTarget = audioTarget,
                        onAudioTargetChange = { audioTarget = it }
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
                        val selection = DownloadSelection(
                            kind = kind,
                            videoHeight = videoHeight,
                            videoContainer = videoContainer,
                            audioTarget = audioTarget
                        )
                        Button(
                            onClick = { vm.download(url, selection) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (media.playlistCount > 0) "Baixar lista (${media.playlistCount})" else "Baixar agora",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (media.entries.isNotEmpty()) {
                    item {
                        Text(
                            "Itens encontrados",
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

            if (state.history.isNotEmpty()) {
                item {
                    HistoryCard(history = state.history, onClear = vm::clearHistory)
                }
            }
        }
    }
}

@Composable
private fun BrandHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF082B59),
                        Color(0xFF12328D),
                        Color(0xFF4C1D95)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.getmuvi_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(78.dp).clip(RoundedCornerShape(22.dp))
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Sua mídia, do seu jeito.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    "Escolha qualidade e formato somente entre as opções encontradas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDCE7FF)
                )
            }
        }
    }
}

@Composable
private fun SelectionCard(
    media: MediaSummary,
    kind: MediaKind,
    onKindChange: (MediaKind) -> Unit,
    videoHeight: Int?,
    onVideoHeightChange: (Int?) -> Unit,
    videoContainer: VideoContainer,
    onVideoContainerChange: (VideoContainer) -> Unit,
    audioTarget: AudioTarget,
    onAudioTargetChange: (AudioTarget) -> Unit
) {
    val availableKinds = buildList {
        if (media.hasVideo && media.hasAudio) add(MediaKind.VIDEO_AUDIO)
        if (media.hasVideo) add(MediaKind.VIDEO_ONLY)
        if (media.hasAudio) add(MediaKind.AUDIO_ONLY)
    }.ifEmpty { listOf(MediaKind.VIDEO_AUDIO, MediaKind.AUDIO_ONLY) }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Escolha o download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)

            Text("Tipo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableKinds.forEach { item ->
                    FilterChip(
                        selected = kind == item,
                        onClick = { onKindChange(item) },
                        label = { Text(item.title) },
                        leadingIcon = {
                            Icon(
                                when (item) {
                                    MediaKind.VIDEO_AUDIO -> Icons.Rounded.Movie
                                    MediaKind.VIDEO_ONLY -> Icons.Rounded.VideoFile
                                    MediaKind.AUDIO_ONLY -> Icons.Rounded.AudioFile
                                },
                                null
                            )
                        }
                    )
                }
            }

            if (kind != MediaKind.AUDIO_ONLY) {
                Text("Qualidade", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = videoHeight == null,
                        onClick = { onVideoHeightChange(null) },
                        label = { Text("Melhor") }
                    )
                    media.videoHeights.forEach { height ->
                        FilterChip(
                            selected = videoHeight == height,
                            onClick = { onVideoHeightChange(height) },
                            label = { Text("${height}p") }
                        )
                    }
                }

                Text("Formato", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VideoContainer.entries.forEach { container ->
                        FilterChip(
                            selected = videoContainer == container,
                            onClick = { onVideoContainerChange(container) },
                            label = {
                                Column(Modifier.padding(vertical = 3.dp)) {
                                    Text(container.title, fontWeight = FontWeight.SemiBold)
                                    Text(container.subtitle, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        )
                    }
                }
            } else {
                Text("Formato do áudio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val targets = buildList {
                        add(AudioTarget.MP3)
                        add(AudioTarget.ORIGINAL)
                        add(AudioTarget.M4A)
                        add(AudioTarget.OPUS)
                        if (media.audioSourceFormats.any { it == "webm" }) add(AudioTarget.WEBM)
                    }
                    targets.chunked(2).forEach { rowTargets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTargets.forEach { target ->
                                FilterChip(
                                    selected = audioTarget == target,
                                    onClick = { onAudioTargetChange(target) },
                                    modifier = Modifier.weight(1f),
                                    label = {
                                        Column(Modifier.padding(vertical = 4.dp)) {
                                            Text(target.title, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                target.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                )
                            }
                            if (rowTargets.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (media.formatNote.isNotBlank()) {
                Text(
                    media.formatNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            if (media.audioSourceFormats.isNotEmpty() && kind == MediaKind.AUDIO_ONLY) {
                Text(
                    "Fonte detectada: ${media.audioSourceFormats.joinToString(", ").uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaCard(media: MediaSummary) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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

            if (media.uploader.isNotBlank()) {
                Text(media.uploader, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val details = buildList {
                media.durationSeconds?.let { add(formatDuration(it)) }
                if (media.playlistCount > 0) add("${media.playlistCount} itens")
                media.videoHeights.firstOrNull()?.let { add("até ${it}p detectado") }
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
                Text("${(state.progress * 100).toInt()}%", fontWeight = FontWeight.ExtraBold)
            }

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth()
            )

            if (state.totalItems > 1) {
                Text(
                    "${state.completedItems} de ${state.totalItems} itens concluídos",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (state.etaSeconds > 0 && state.totalItems <= 1) {
                Text(
                    "Tempo estimado: ${formatDuration(state.etaSeconds)}",
                    style = MaterialTheme.typography.labelSmall
                )
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
private fun HistoryCard(history: List<DownloadHistoryItem>, onClear: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.size(8.dp))
                Text("Histórico", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.DeleteSweep, "Limpar histórico")
                }
            }

            history.take(6).forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${item.option} • ${formatHistoryDate(item.timestamp)}${if (item.itemCount > 1) " • ${item.itemCount} itens" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Text("Cole um link e deixe o GetMuvi analisar", fontWeight = FontWeight.SemiBold)
            Text(
                "As qualidades disponíveis aparecem automaticamente. Baixe apenas conteúdos que você pode salvar.",
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

private fun formatHistoryDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return SimpleDateFormat("dd/MM • HH:mm", Locale("pt", "BR")).format(Date(timestamp))
}

private val GetMuviColors = darkColorScheme(
    primary = Color(0xFF21D4FD),
    onPrimary = Color(0xFF001F2A),
    primaryContainer = Color(0xFF123B66),
    onPrimaryContainer = Color(0xFFD7ECFF),
    secondary = Color(0xFF9B7BFF),
    secondaryContainer = Color(0xFF35235D),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Color(0xFFF472D0),
    background = Color(0xFF07111F),
    onBackground = Color(0xFFF4F7FF),
    surface = Color(0xFF0B1626),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF17243A),
    onSurfaceVariant = Color(0xFFBFC9D9)
)

@Composable
fun GetMuviTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GetMuviColors,
        content = content
    )
}
