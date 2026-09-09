package com.carlosvale.ytdownloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val DEFAULT_DOWNLOAD_DOCUMENT_ID = "primary:Download/GetMuvi"

/**
 * Abre a pasta usada no último download.
 *
 * Alguns fabricantes não expõem um visualizador de diretórios para ACTION_VIEW.
 * Nesses aparelhos fazemos fallback para o seletor de pastas já posicionado no
 * diretório correto, preservando o comportamento em diferentes gerenciadores.
 */
fun openDownloadFolder(context: Context, customFolder: Uri?) {
    val documentUri = customFolder?.toDocumentUri()
        ?: DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_AUTHORITY,
            DEFAULT_DOWNLOAD_DOCUMENT_ID
        )

    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (viewIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(viewIntent)
        return
    }

    val initialTree = customFolder ?: DocumentsContract.buildTreeDocumentUri(
        EXTERNAL_STORAGE_AUTHORITY,
        DEFAULT_DOWNLOAD_DOCUMENT_ID
    )

    val fallbackIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialTree)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(fallbackIntent)
}

private fun Uri.toDocumentUri(): Uri = runCatching {
    if (DocumentsContract.isTreeUri(this)) {
        DocumentsContract.buildDocumentUriUsingTree(
            this,
            DocumentsContract.getTreeDocumentId(this)
        )
    } else {
        this
    }
}.getOrDefault(this)
