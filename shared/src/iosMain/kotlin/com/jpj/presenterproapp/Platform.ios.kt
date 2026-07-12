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

    override fun connectToWifi(
        ssid: String,
        password: String,
        onSuccess: (gatewayIp: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val configuration = platform.NetworkExtension.NEHotspotConfiguration(ssid, password, false)
        configuration.joinOnce = true
        platform.NetworkExtension.NEHotspotConfigurationManager.sharedManager().applyConfiguration(configuration) { error ->
            if (error == null) {
                onSuccess("192.168.137.1")
            } else {
                onError(error.localizedDescription ?: "Unknown error")
            }
        }
    }
}

actual fun getPlatform(): Platform = IOSPlatform()