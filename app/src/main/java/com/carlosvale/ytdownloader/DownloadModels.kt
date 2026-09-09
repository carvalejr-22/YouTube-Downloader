package com.carlosvale.ytdownloader

import android.net.Uri

enum class MediaKind(val title: String) {
    VIDEO_AUDIO("Vídeo + áudio"),
    VIDEO_ONLY("Só vídeo"),
    AUDIO_ONLY("Só áudio")
}

enum class VideoContainer(val title: String, val subtitle: String) {
    MP4("MP4", "Mais compatível"),
    BEST("Melhor formato", "Preserva a melhor combinação")
}

enum class AudioTarget(val title: String, val subtitle: String) {
    ORIGINAL("Original", "Sem conversão desnecessária"),
    MP3("MP3", "Alta compatibilidade"),
    M4A("M4A", "Boa qualidade e tamanho"),
    OPUS("Opus", "Ótima eficiência"),
    WEBM("WebM", "Somente quando a fonte oferece")
}

data class DownloadSelection(
    val kind: MediaKind,
    val videoHeight: Int? = null,
    val videoContainer: VideoContainer = VideoContainer.MP4,
    val audioTarget: AudioTarget = AudioTarget.MP3
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
    val entries: List<String> = emptyList(),
    val playlistEntries: List<PlaylistEntry> = emptyList(),
    val videoHeights: List<Int> = emptyList(),
    val audioSourceFormats: List<String> = emptyList(),
    val hasVideo: Boolean = true,
    val hasAudio: Boolean = true,
    val formatNote: String = ""
)

data class DownloadHistoryItem(
    val title: String,
    val option: String,
    val timestamp: Long,
    val itemCount: Int
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
    val history: List<DownloadHistoryItem> = emptyList(),
    val resetToken: Int = 0
)

data class DownloadAttempt(
    val label: String,
    val playerClients: String? = null,
    val compatibilityMode: Boolean = false,
    val restrictFilenames: Boolean = false
)

data class FormatInfo(
    val videoHeights: List<Int>,
    val audioSourceFormats: List<String>,
    val hasVideo: Boolean,
    val hasAudio: Boolean
)
