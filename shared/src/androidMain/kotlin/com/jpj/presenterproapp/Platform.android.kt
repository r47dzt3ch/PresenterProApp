package com.jpj.presenterproapp

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"

    @Composable
    override fun RemoteImage(
        url: String,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        onLoading: @Composable () -> Unit,
        onFailure: @Composable (Throwable) -> Unit
    ) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()