package com.jpj.presenterproapp

import platform.UIKit.UIDevice
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

    @Composable
    override fun RemoteImage(
        url: String,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        onLoading: @Composable () -> Unit,
        onFailure: @Composable (Throwable) -> Unit
    ) {
        // Placeholder for iOS until Coil multiplatform or Ktor implementation
        Text("Image loading not yet implemented on iOS", color = Color.White)
    }
}

actual fun getPlatform(): Platform = IOSPlatform()