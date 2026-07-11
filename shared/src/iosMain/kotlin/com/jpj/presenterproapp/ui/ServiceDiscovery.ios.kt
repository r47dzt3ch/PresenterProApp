package com.jpj.presenterproapp.ui

import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject

actual class ServiceDiscovery actual constructor() {
    private var browser: NSNetServiceBrowser? = null
    private var delegate: BrowserDelegate? = null

    actual fun startDiscovery(onServiceDiscovered: (name: String, ip: String, port: Int) -> Unit) {
        browser = NSNetServiceBrowser()
        delegate = BrowserDelegate(onServiceDiscovered)
        browser?.delegate = delegate
        browser?.searchForServicesOfType("_presenterpro._tcp.", inDomain = "local.")
    }

    actual fun stopDiscovery() {
        browser?.stop()
        browser = null
        delegate = null
    }
}

private class BrowserDelegate(
    private val onServiceDiscovered: (name: String, ip: String, port: Int) -> Unit
) : NSObject(), NSNetServiceBrowserDelegateProtocol, NSNetServiceDelegateProtocol {
    private val services = mutableListOf<NSNetService>()

    override fun netServiceBrowser(
        browser: NSNetServiceBrowser,
        didFindService: NSNetService,
        moreComing: Boolean
    ) {
        services.add(didFindService)
        didFindService.delegate = this
        didFindService.resolveWithTimeout(5.0)
    }

    override fun netServiceDidResolveAddress(sender: NSNetService) {
        val name = sender.name
        val port = sender.port.toInt()
        val host = sender.hostName ?: "localhost"
        onServiceDiscovered(name, host, port)
    }
}
