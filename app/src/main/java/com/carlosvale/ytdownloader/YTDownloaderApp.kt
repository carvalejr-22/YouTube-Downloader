package com.carlosvale.ytdownloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Mantém os componentes pesados fora da inicialização visual do app.
 * O yt-dlp é carregado apenas quando o usuário analisa uma mídia e o FFmpeg
 * somente quando a operação realmente precisa mesclar ou converter arquivos.
 */
class YTDownloaderApp : Application() {
    private val initMutex = Mutex()
    private val updateMutex = Mutex()

    @Volatile
    private var downloaderInitialized = false

    @Volatile
    private var ffmpegInitialized = false

    @Volatile
    private var engineChecked = false

    /** Inicializa e atualiza o yt-dlp no primeiro uso efetivo. */
    suspend fun ensureDownloaderReady() {
        ensureDownloaderInitialized()
        if (engineChecked) return

        updateMutex.withLock {
            if (engineChecked) return

            withContext(Dispatchers.IO) {
                runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@YTDownloaderApp,
                        YoutubeDL.UpdateChannel.NIGHTLY
                    )
                }.onFailure {
                    Log.w(TAG, "Não foi possível atualizar o yt-dlp; usando versão local", it)
                }
            }

            engineChecked = true
        }
    }

    /**
     * O FFmpeg é o componente mais pesado do app e não é necessário para
     * análise, vídeo sem áudio ou áudio original. Carregá-lo sob demanda
     * reduz o uso de RAM na abertura e nas tarefas mais simples.
     */
    suspend fun ensureFfmpegReady() {
        if (ffmpegInitialized) return

        initMutex.withLock {
            if (ffmpegInitialized) return

            withContext(Dispatchers.IO) {
                runCatching {
                    FFmpeg.getInstance().init(this@YTDownloaderApp)
                }.onFailure {
                    Log.e(TAG, "Falha ao inicializar FFmpeg", it)
                }.getOrThrow()
            }

            ffmpegInitialized = true
        }
    }

    private suspend fun ensureDownloaderInitialized() {
        if (downloaderInitialized) return

        initMutex.withLock {
            if (downloaderInitialized) return

            withContext(Dispatchers.IO) {
                runCatching {
                    YoutubeDL.getInstance().init(this@YTDownloaderApp)
                }.onFailure {
                    Log.e(TAG, "Falha ao inicializar yt-dlp", it)
                }.getOrThrow()
            }

            downloaderInitialized = true
        }
    }

    private companion object {
        const val TAG = "GetMuvi"
    }
}
