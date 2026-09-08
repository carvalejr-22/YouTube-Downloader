package com.carlosvale.ytdownloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class YTDownloaderApp : Application() {
    private val engineUpdateMutex = Mutex()

    @Volatile
    private var engineReady = false

    override fun onCreate() {
        super.onCreate()
        runCatching {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
        }.onFailure {
            Log.e("YTDownloader", "Falha ao inicializar mecanismo de mídia", it)
        }
    }

    /**
     * Mantém o yt-dlp atualizado antes de liberar a interface para uso.
     * O YouTube altera com frequência a forma de entregar os arquivos e
     * versões antigas do yt-dlp podem retornar HTTP 403 mesmo quando a
     * análise do vídeo funciona normalmente.
     */
    suspend fun ensureEngineReady() {
        if (engineReady) return

        engineUpdateMutex.withLock {
            if (engineReady) return

            withContext(Dispatchers.IO) {
                runCatching {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        this@YTDownloaderApp,
                        YoutubeDL.UpdateChannel.NIGHTLY
                    )
                }.onFailure {
                    // Se o GitHub estiver temporariamente indisponível, o app
                    // continua usando a versão embutida em vez de bloquear.
                    Log.w("YTDownloader", "Não foi possível atualizar o yt-dlp; usando versão local", it)
                }
            }

            engineReady = true
        }
    }
}
