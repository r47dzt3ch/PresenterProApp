package com.jpj.presenterproapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.jpj.presenterproapp.ui.DiscoveryScreen
import media.kamel.image.config.KamelConfig
import media.kamel.image.config.LocalKamelConfig
import media.kamel.image.config.default

@Composable
fun App() {
    CompositionLocalProvider(LocalKamelConfig provides KamelConfig.default) {
        MaterialTheme {
            Navigator(DiscoveryScreen()) { navigator ->
                SlideTransition(navigator)
            }
        }
    }
}
