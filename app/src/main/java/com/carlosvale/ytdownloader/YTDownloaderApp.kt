package com.carlosvale.ytdownloader

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class YTDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)

            // O YouTube altera o mecanismo de entrega dos arquivos com frequência.
            // Mantemos o yt-dlp atualizado antes da primeira análise/download para
            // evitar falhas como "HTTP Error 403: Forbidden" com extratores antigos.
            runCatching {
                YoutubeDL.getInstance().updateYoutubeDL(
                    this,
                    YoutubeDL.UpdateChannel.NIGHTLY
                )
            }.onFailure {
                // Se a atualização estiver temporariamente indisponível, o app
                // ainda abre e tenta usar a versão embutida.
                Log.w("YTDownloader", "Não foi possível atualizar o yt-dlp; usando versão local", it)
            }
        }.onFailure {
            Log.e("YTDownloader", "Falha ao inicializar mecanismo de mídia", it)
        }
    }
}
