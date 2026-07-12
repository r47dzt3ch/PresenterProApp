package com.jpj.presenterproapp.ui

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.content.Context
import com.jpj.presenterproapp.AppContext

actual class ServiceDiscovery actual constructor() {
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolveQueue = mutableListOf<NsdServiceInfo>()
    private var isResolving = false

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
                if (serviceInfo.serviceType.contains("_presenterpro")) {
                    synchronized(resolveQueue) {
                        resolveQueue.add(serviceInfo)
                        resolveNext(onServiceDiscovered)
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
        }

        try {
            nsdManager?.discoverServices("_presenterpro._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resolveNext(onServiceDiscovered: (name: String, ip: String, port: Int) -> Unit) {
        val manager = nsdManager ?: return
        synchronized(resolveQueue) {
            if (isResolving || resolveQueue.isEmpty()) return
            isResolving = true
            val nextService = resolveQueue.removeAt(0)
            
            try {
                manager.resolveService(nextService, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(resolvedInfo: NsdServiceInfo?, errorCode: Int) {
                        synchronized(resolveQueue) {
                            isResolving = false
                            resolveNext(onServiceDiscovered)
                        }
                    }

                    override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo?) {
                        synchronized(resolveQueue) {
                            isResolving = false
                        }
                        if (resolvedServiceInfo != null) {
                            val host = resolvedServiceInfo.host?.hostAddress
                            val port = resolvedServiceInfo.port
                            val name = resolvedServiceInfo.serviceName ?: "PyPresenterPro Desktop"
                            if (host != null) {
                                onServiceDiscovered(name, host, port)
                            }
                        }
                        synchronized(resolveQueue) {
                            resolveNext(onServiceDiscovered)
                        }
                    }
                })
            } catch (e: Exception) {
                isResolving = false
                resolveNext(onServiceDiscovered)
            }
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
        synchronized(resolveQueue) {
            resolveQueue.clear()
            isResolving = false
        }
    }
}
