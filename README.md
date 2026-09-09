# BaixaMídia Android

Aplicativo Android nativo em Kotlin + Jetpack Compose para analisar links e baixar mídia autorizada de plataformas compatíveis com o yt-dlp, incluindo vídeos, áudios e listas quando a plataforma disponibiliza essas informações.

## Recursos
- Análise de links de YouTube, Instagram, Facebook e outras plataformas suportadas pelo mecanismo
- Vídeo + áudio em qualidade máxima
- Vídeo + áudio priorizando MP4
- Somente vídeo em qualidade máxima ou MP4, quando a plataforma oferece faixa de vídeo separada
- Somente áudio no melhor formato disponível ou convertido para MP3
- Tratamento de títulos muito longos para evitar `File name too long` no Android
- Pasta padrão em `Downloads/BaixaMidia`
- Pasta personalizada via seletor de diretório do Android
- Barra de progresso
- Cancelamento de download
- Interface responsiva em Material 3
- Atualização automática do mecanismo yt-dlp no primeiro uso

## Limitações
O suporte depende dos extratores do yt-dlp e das regras de cada plataforma. Conteúdos privados, protegidos por DRM, removidos ou que exigem login/cookies podem não funcionar. Listas e coleções também dependem de como cada serviço expõe os itens.

> Use apenas com conteúdo que você criou, que é livre para download ou para o qual você tem autorização. O app não implementa bypass de DRM.

## Build
O workflow do GitHub Actions compila um APK debug automaticamente a cada push para `main`.

Tecnologias principais: Android API 37 (target 36), Kotlin, Jetpack Compose, yt-dlp Android e FFmpeg.

## Backup da versão anterior
A versão funcional anterior ao BaixaMídia 0.2.0 está preservada na branch `backup/yt-downloader-working-2026-09-08`.
