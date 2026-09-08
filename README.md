# YT Downloader Android

Aplicativo Android nativo em Kotlin + Jetpack Compose para analisar links e baixar mídia autorizada, incluindo playlists.

## Recursos
- Análise de vídeo ou playlist por URL
- Vídeo + áudio em qualidade máxima
- Vídeo + áudio priorizando MP4
- Somente vídeo em qualidade máxima ou MP4
- Somente áudio no formato original ou MP3
- Pasta padrão em `Downloads/YT Downloader`
- Pasta personalizada via seletor de diretório do Android
- Barra de progresso e notificação de download
- Cancelamento de download
- Interface responsiva em Material 3

> Use apenas com conteúdo que você criou ou tem autorização para baixar. O app não implementa bypass de DRM.

## Build
O workflow do GitHub Actions compila um APK debug automaticamente a cada push para `main`.

Tecnologias principais: Android API 37 (target 36), Kotlin 2.3.21, Jetpack Compose BOM 2026.08.00, yt-dlp Android 0.18.1 e FFmpeg 0.18.1.
