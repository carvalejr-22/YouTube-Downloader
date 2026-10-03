package com.carlosvale.ytdownloader

/** Diagnostics contain categories only: never URLs, cookies, filenames or server output. */
internal object DownloadErrors {
    fun code(error: Throwable): String {
        val message = generateSequence(error) { it.cause }.take(4)
            .joinToString(" ") { it.message.orEmpty().take(2048) }.lowercase()
        return when {
            "cancel" in message -> "CANCELLED"
            "no space" in message || "enospc" in message -> "STORAGE_FULL"
            "permission denied" in message || "pasta" in message || "cópia" in message || "salvar" in message -> "SAVE_FAILED"
            "429" in message || "too many requests" in message -> "RATE_LIMIT"
            "private video" in message || "login" in message || "sign in" in message || "registered users" in message || "cookies" in message || "age-restricted" in message -> "AUTH_REQUIRED"
            "removed" in message || "deleted" in message || "copyright" in message -> "CONTENT_REMOVED"
            "unavailable" in message -> "UNAVAILABLE"
            "cannot parse" in message || "unable to extract" in message -> "EXTRACTION_FAILED"
            "unsupported url" in message -> "UNSUPPORTED_LINK"
            "requested format" in message -> "FORMAT_UNAVAILABLE"
            "403" in message || "forbidden" in message -> "ACCESS_DENIED"
            "timeout" in message || "timed out" in message || "connection" in message || "resolve" in message -> "NETWORK"
            "arquivo final" in message || "gerado vazio" in message -> "OUTPUT_MISSING"
            else -> "UNKNOWN"
        }
    }

    fun message(error: Throwable): String {
        val code = code(error)
        val text = when (code) {
            "STORAGE_FULL" -> "Sem espaço para concluir. Libere espaço no aparelho ou na pasta escolhida."
            "SAVE_FAILED" -> "Não foi possível salvar. Selecione novamente uma pasta com acesso de gravação."
            "RATE_LIMIT" -> "A plataforma limitou as solicitações. Aguarde antes de tentar novamente."
            "AUTH_REQUIRED" -> "A plataforma pediu autenticação ou confirmação de acesso. Este app ainda não utiliza login."
            "CONTENT_REMOVED" -> "A plataforma informou remoção ou restrição do conteúdo."
            "UNAVAILABLE" -> "A plataforma informou vídeo indisponível nas tentativas realizadas. Isso também pode ser uma falha de compatibilidade."
            "EXTRACTION_FAILED" -> "Não foi possível interpretar os dados da plataforma. Isso não confirma que o vídeo seja privado."
            "UNSUPPORTED_LINK" -> "Este endereço não foi reconhecido. Use o link direto da publicação."
            "FORMAT_UNAVAILABLE" -> "O formato escolhido não está mais disponível. Analise o link novamente."
            "ACCESS_DENIED" -> "A plataforma recusou o acesso ao arquivo. Tente novamente mais tarde."
            "NETWORK" -> "Falha de conexão. Verifique sua rede e tente novamente."
            "OUTPUT_MISSING" -> "O processamento não entregou um arquivo final válido."
            "CANCELLED" -> "Operação cancelada."
            else -> "Não foi possível concluir. Copie o diagnóstico para investigar."
        }
        return "$text [$code]"
    }
}
