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
    private val updateMutex = Mutex()

    @Volatile
    private var engineChecked = false

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
     * Atualiza o yt-dlp no primeiro uso sem travar a abertura do aplicativo.
     * Se a atualização falhar por falta de rede, a versão embutida continua
     * disponível e o app ainda pode tentar o download.
     */
    suspend fun ensureEngineReady() {
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
                    Log.w("YTDownloader", "Não foi possível atualizar o yt-dlp; usando versão local", it)
                }
            }

            engineChecked = true
        }
    }
}
