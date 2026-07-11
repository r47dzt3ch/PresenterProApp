package com.jpj.presenterproapp.ui

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.content.Context
import com.jpj.presenterproapp.AppContext

actual class ServiceDiscovery actual constructor() {
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    actual fun startDiscovery(onServiceDiscovered: (name: String, ip: String, port: Int) -> Unit) {
        val context = AppContext.context ?: return
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                try {
                    nsdManager?.stopServiceDiscovery(this)
                } catch (e: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                try {
                    nsdManager?.stopServiceDiscovery(this)
                } catch (e: Exception) {}
            }

            override fun onDiscoveryStarted(serviceType: String?) {}

            override fun onDiscoveryStopped(serviceType: String?) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                if (serviceInfo == null) return
                // Check if the service matches our custom type
                if (serviceInfo.serviceType.contains("_presenterpro")) {
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(resolvedInfo: NsdServiceInfo?, errorCode: Int) {}

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo?) {
                            if (resolvedServiceInfo == null) return
                            val host = resolvedServiceInfo.host?.hostAddress ?: return
                            val port = resolvedServiceInfo.port
                            val name = resolvedServiceInfo.serviceName ?: "PyPresenterPro Desktop"
                            onServiceDiscovered(name, host, port)
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
        }

        try {
            nsdManager?.discoverServices("_presenterpro._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun stopDiscovery() {
        try {
            discoveryListener?.let {
                nsdManager?.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            // Ignore
        }
        discoveryListener = null
        nsdManager = null
    }
}
