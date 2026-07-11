package com.jpj.presenterproapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform