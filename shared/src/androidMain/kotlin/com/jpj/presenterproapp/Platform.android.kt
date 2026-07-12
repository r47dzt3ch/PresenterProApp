package com.jpj.presenterproapp

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier

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

    override fun connectToWifi(
        ssid: String,
        password: String,
        onSuccess: (gatewayIp: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val context = AppContext.context
        if (context == null) {
            onError("Context not initialized")
            return
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        fun getGatewayIp(): String {
            val dhcp = wifiManager.dhcpInfo
            val gateway = dhcp.gateway
            if (gateway == 0) return "192.168.137.1"
            return "${gateway and 0xFF}.${(gateway shr 8) and 0xFF}.${(gateway shr 16) and 0xFF}.${(gateway shr 24) and 0xFF}"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        connectivityManager.bindProcessToNetwork(network)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            onSuccess(getGatewayIp())
                        }, 1500)
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        onError("Wi-Fi direct connection timed out or rejected")
                    }
                })
            } catch (e: Exception) {
                onError("Failed to request network: ${e.message}")
            }
        } else {
            try {
                val wifiConfig = WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    preSharedKey = "\"$password\""
                }
                val netId = wifiManager.addNetwork(wifiConfig)
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    onSuccess(getGatewayIp())
                }, 1500)
            } catch (e: Exception) {
                onError("Legacy Wi-Fi configuration failed: ${e.message}")
            }
        }
    }
}

actual fun getPlatform(): Platform = AndroidPlatform()