package com.jpj.presenterproapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.jpj.presenterproapp.ui.DiscoveryScreen

@Composable
fun App() {
    MaterialTheme {
        Navigator(DiscoveryScreen()) { navigator ->
            SlideTransition(navigator)
        }
    }
}
