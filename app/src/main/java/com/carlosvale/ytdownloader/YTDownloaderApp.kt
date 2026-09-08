package com.carlosvale.ytdownloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class YTDownloaderApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineUpdateMutex = Mutex()

    @Volatile
    private var engineReady = false

    override fun onCreate() {
        super.onCreate()
        runCatching {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)

            // Atualiza o extrator em segundo plano sempre que o app inicia.
            // Isso evita o erro HTTP 403 causado por mudanças frequentes do YouTube.
            appScope.launch { ensureEngineReady() }
        }.onFailure {
            Log.e("YTDownloader", "Falha ao inicializar mecanismo de mídia", it)
        }
    }

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
                    // Sem conexão com o GitHub, continua usando a versão embutida.
                    Log.w("YTDownloader", "Não foi possível atualizar o yt-dlp; usando versão local", it)
                }
            }

            engineReady = true
        }
    }
}
