package com.jpj.presenterproapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

interface Platform {
    val name: String
    
    @Composable
    fun RemoteImage(
        url: String,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        onLoading: @Composable () -> Unit,
        onFailure: @Composable (Throwable) -> Unit
    )
}

expect fun getPlatform(): Platform