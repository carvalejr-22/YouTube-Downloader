package com.carlosvale.ytdownloader

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent as activitySetContent
import androidx.compose.runtime.Composable

/**
 * Mantém a inicialização do Compose disponível para as Activities do app.
 */
fun ComponentActivity.setContent(content: @Composable () -> Unit) {
    activitySetContent(content = content)
}
