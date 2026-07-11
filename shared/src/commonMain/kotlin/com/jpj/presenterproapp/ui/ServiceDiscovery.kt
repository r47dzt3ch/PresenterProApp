package com.jpj.presenterproapp.ui

expect class ServiceDiscovery() {
    fun startDiscovery(onServiceDiscovered: (name: String, ip: String, port: Int) -> Unit)
    fun stopDiscovery()
}
