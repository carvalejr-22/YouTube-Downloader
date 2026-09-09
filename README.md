# GetMuvi — Baixador de Músicas e Vídeos

GetMuvi é um aplicativo Android nativo em Kotlin + Jetpack Compose para analisar links de mídia pública/autorizada e apresentar opções de download conforme os formatos realmente disponibilizados pela plataforma e pelo mecanismo yt-dlp.

## GetMuvi 0.3.0 — versão de teste
- Nova identidade visual GetMuvi, sem utilizar identidade visual de outras plataformas
- Novo ícone original e splash screen
- UX reorganizado em três fluxos: **Vídeo + áudio**, **Só vídeo** e **Só áudio**
- Resoluções exibidas dinamicamente, como 2160p, 1440p, 1080p, 720p, 480p, 360p e outras, somente quando encontradas
- Opções de contêiner de vídeo como MP4 e WebM quando disponíveis
- Áudio em MP3, M4A, Opus, formato original e WebM quando a fonte oferece faixa WebM
- Estimativa de tamanho quando a própria plataforma informa `filesize` ou `filesize_approx`
- Opção "Melhor qualidade" para escolha automática
- Pasta padrão `Downloads/GetMuvi` e suporte a pasta personalizada
- Barra de progresso, ETA, cancelamento e progresso por item em listas
- Compartilhamento de links diretamente para o app
- Mantida a lógica de fallback e compatibilidade da versão 0.2.1

## Compatibilidade
O suporte depende dos extratores do yt-dlp e das regras de cada serviço. Conteúdos privados, protegidos por DRM, removidos, geobloqueados ou que exijam autenticação/cookies podem não funcionar. O app não implementa bypass de DRM.

Use o GetMuvi apenas com conteúdo próprio, livre para download ou para o qual você tenha autorização.

## Build
O GitHub Actions compila automaticamente um APK debug em `main` e na branch de teste `getmuvi-0.3.0-test`. O artefato desta versão se chama `GetMuvi-0.3.0-debug`.

Tecnologias principais: Android API 37 (target 36), Kotlin, Jetpack Compose, yt-dlp Android e FFmpeg.

## Backups
- `backup-baixamidia-0.2.1`: versão funcional imediatamente anterior ao rebranding GetMuvi.
- `backup/yt-downloader-working-2026-09-08`: versão funcional anterior ao BaixaMídia 0.2.0.
