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
        }.onFailure {
            Log.e("YTDownloader", "Falha ao inicializar mecanismo de mídia", it)
        }
    }
}
