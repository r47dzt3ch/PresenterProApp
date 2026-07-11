package com.jpj.presenterproapp.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SlideInfo(
    val title: String,
    val notes: String = ""
)

@Serializable
data class SlidesResponse(
    val slides: List<SlideInfo>,
    val current_index: Int
)

@Serializable
data class WSMessage(
    val type: String,
    val current_index: Int,
    val total_slides: Int? = null
)

class ControlScreenModel(val ip: String, val port: Int) : ScreenModel {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(WebSockets)
    }

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex = _currentIndex.asStateFlow()

    private val _slides = MutableStateFlow<List<SlideInfo>>(emptyList())
    val slides = _slides.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    init {
        loadSlides()
        startWebSocket()
    }

    private fun loadSlides() {
        screenModelScope.launch {
            try {
                val response: SlidesResponse = client.get("http://$ip:$port/slides").body()
                _slides.value = response.slides
                _currentIndex.value = response.current_index
                _isConnected.value = true
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    private fun startWebSocket() {
        screenModelScope.launch {
            try {
                client.webSocket("ws://$ip:$port/ws/mirror") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val msg = Json.decodeFromString<WSMessage>(text)
                            if (msg.type == "state") {
                                _currentIndex.value = msg.current_index
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    fun next() {
        screenModelScope.launch {
            client.post("http://$ip:$port/control/navigate?action=next")
        }
    }

    fun prev() {
        screenModelScope.launch {
            client.post("http://$ip:$port/control/navigate?action=prev")
        }
    }

    fun toggleStandby() {
        screenModelScope.launch {
            client.post("http://$ip:$port/control/standby")
        }
    }

    override fun onDispose() {
        client.close()
        super.onDispose()
    }
}

data class ControlScreen(val ip: String, val port: Int) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model = rememberScreenModel { ControlScreenModel(ip, port) }
        val currentIndex by model.currentIndex.collectAsState()
        val slides by model.slides.collectAsState()
        val isConnected by model.isConnected.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Presenter Remote") },
                    actions = {
                        if (!isConnected) {
                            Text("Offline", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                        } else {
                            Text("Online", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                        }
                    }
                )
            },
            bottomBar = {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { model.prev() }) {
                            Icon(Icons.Default.NavigateBefore, contentDescription = "Previous")
                        }
                        IconButton(onClick = { model.toggleStandby() }) {
                            Icon(Icons.Default.Pause, contentDescription = "Standby")
                        }
                        IconButton(onClick = { model.next() }) {
                            Icon(Icons.Default.NavigateNext, contentDescription = "Next")
                        }
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (slides.isNotEmpty() && currentIndex < slides.size) {
                    val imageUrl = "http://$ip:$port/slides/$currentIndex/image"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(8.dp)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    if (dragAmount > 50) model.prev()
                                    else if (dragAmount < -50) model.next()
                                    change.consume()
                                }
                            }
                    ) {
                        KamelImage(
                            resource = { asyncPainterResource(imageUrl) },
                            contentDescription = "Slide Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    
                    Text(
                        text = "Notes:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = slides[currentIndex].notes.ifEmpty { "No notes for this slide." },
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (isConnected) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Unable to connect to server.")
                    }
                }
            }
        }
    }
}
