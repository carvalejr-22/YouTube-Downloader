# GetMuvi - regras de compatibilidade para o motor nativo de mídia.
# O R8 pode otimizar todo o restante do app, mas preservamos a ponte
# yt-dlp/FFmpeg porque ela usa integração com código nativo.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**
