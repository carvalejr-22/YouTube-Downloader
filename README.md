# GetMuvi — Baixador de Músicas e Vídeos

**Versão de teste: 0.3.0**

GetMuvi é um aplicativo Android nativo em Kotlin + Jetpack Compose para analisar links e baixar mídia autorizada de plataformas compatíveis com o mecanismo yt-dlp. O foco desta versão é tornar o fluxo simples: colar o link, analisar, escolher apenas entre as qualidades detectadas e baixar.

## O que mudou na 0.3.0
- Nova marca **GetMuvi** e nova identidade visual original, sem uso da identidade do YouTube
- Novo ícone e arte do aplicativo
- UX redesenhado em Material 3 com identidade azul, ciano e roxa
- Seleção por tipo: **Vídeo + áudio**, **Só vídeo** ou **Só áudio**
- Qualidades de vídeo exibidas dinamicamente conforme o link, como 1080p, 720p, 480p e 360p quando disponíveis
- Opção **Melhor** para utilizar a melhor qualidade detectada
- Vídeo em **MP4** ou no **melhor formato** disponível
- Áudio em **Original, MP3, M4A e Opus**
- **WebM** aparece como opção de áudio somente quando a fonte analisada oferece WebM
- Histórico local dos últimos downloads
- Pasta padrão alterada para `Downloads/GetMuvi`
- Pasta personalizada continua disponível pelo seletor do Android
- Progresso, ETA, cancelamento e suporte a playlists mantidos
- Fallbacks atuais para YouTube e outras plataformas foram preservados
- Tratamento de nomes longos do Facebook mantido
- Mecanismo continua sendo atualizado no primeiro uso

## Plataformas
O app usa os extratores disponíveis no yt-dlp. Na prática, isso permite analisar diversas plataformas, incluindo YouTube, Instagram, Facebook e outras, desde que a mídia esteja pública e o extrator correspondente funcione.

O suporte pode mudar conforme cada plataforma altera seus sites, APIs e mecanismos de entrega de mídia.

## Playlists e coleções
Quando uma lista é encontrada, o GetMuvi exibe os itens e usa o primeiro item para estimar as qualidades disponíveis. Uma lista pode conter vídeos com formatos diferentes, portanto uma qualidade específica pode não existir em todos os itens; os fallbacks do mecanismo continuam ativos.

## Descrição curta sugerida
Baixe músicas e vídeos nos formatos e qualidades disponíveis no link, com uma interface simples, rápida e moderna.

## Descrição longa sugerida
O GetMuvi analisa links públicos de mídia e apresenta as opções de download encontradas de forma clara e organizada. Você escolhe entre vídeo com áudio, somente vídeo ou somente áudio, define a qualidade disponível e seleciona o formato desejado. O aplicativo também oferece suporte a playlists quando a plataforma disponibiliza seus itens, escolha de pasta, barra de progresso, cancelamento e histórico local de downloads.

A disponibilidade de formatos e plataformas depende da fonte original e do mecanismo de extração. Conteúdos privados, protegidos por DRM, removidos ou que exigem autenticação podem não funcionar.

## Uso responsável
Use o GetMuvi apenas para conteúdo que você criou, que esteja livre para download ou para o qual você possua autorização. O aplicativo não implementa bypass de DRM.

## Build
O GitHub Actions compila automaticamente um APK debug para `main` e para a branch de teste `getmuvi-v0.3.0-test`.

Tecnologias principais: Android API 37 (target 36), Kotlin, Jetpack Compose, yt-dlp Android e FFmpeg.

## Backups
- `backup-baixamidia-0.2.1`: versão funcional imediatamente anterior à mudança para GetMuvi
- `backup/yt-downloader-working-2026-09-08`: backup anterior já existente
