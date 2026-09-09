package com.carlosvale.ytdownloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import java.io.File

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/**
 * Abre exatamente a pasta usada no último download.
 *
 * Para a pasta padrão, o URI é calculado a partir do caminho real retornado
 * pelo Android (ex.: /storage/emulated/0/Download/GetMuvi), em vez de depender
 * de um caminho fixo presumido. Para uma pasta escolhida pelo usuário, usamos
 * diretamente o tree URI concedido pelo seletor de pastas.
 *
 * O ACTION_OPEN_DOCUMENT_TREE com EXTRA_INITIAL_URI é priorizado porque alguns
 * gerenciadores de arquivos de fabricantes resolvem ACTION_VIEW para diretórios,
 * mas ignoram a subpasta e abrem apenas a raiz de Downloads.
 */
fun openDownloadFolder(context: Context, customFolder: Uri?) {
    val targetTreeUri = customFolder ?: defaultDownloadTreeUri()
    val exactDocumentUri = targetTreeUri.toDocumentUri()

    val primaryIntent = buildExactFolderIntent(context, customFolder, exactDocumentUri)
    if (primaryIntent.resolveActivity(context.packageManager) != null) {
        runCatching {
            context.startActivity(primaryIntent)
        }.onSuccess { return }
    }

    // Fallback para gerenciadores que conseguem visualizar um document URI
    // diretamente. O URI continua apontando para a pasta exata.
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(exactDocumentUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (viewIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(viewIntent)
        return
    }

    // Último fallback: abre o seletor de pastas já posicionado no diretório
    // exato. Isso evita cair na raiz do armazenamento.
    val fallbackIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, exactDocumentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(fallbackIntent)
}

private fun buildExactFolderIntent(
    context: Context,
    customFolder: Uri?,
    exactDocumentUri: Uri
): Intent {
    val baseIntent = if (customFolder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val folder = defaultDownloadFolder()
        val storageManager = context.getSystemService(StorageManager::class.java)
        storageManager?.getStorageVolume(folder)?.createOpenDocumentTreeIntent()
            ?: Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    } else {
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    return baseIntent.apply {
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, exactDocumentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun defaultDownloadFolder(): File =
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "GetMuvi")
        .apply { mkdirs() }

private fun defaultDownloadTreeUri(): Uri {
    val folder = defaultDownloadFolder().canonicalFile
    val primaryRoot = Environment.getExternalStorageDirectory().canonicalFile

    val relativePath = runCatching {
        folder.relativeTo(primaryRoot).invariantSeparatorsPath
    }.getOrElse {
        // Fallback defensivo para aparelhos que expõem o armazenamento primário
        // por um caminho não convencional.
        "Download/GetMuvi"
    }.trim('/')

    val documentId = if (relativePath.isBlank()) "primary:" else "primary:$relativePath"
    return DocumentsContract.buildTreeDocumentUri(
        EXTERNAL_STORAGE_AUTHORITY,
        documentId
    )
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
