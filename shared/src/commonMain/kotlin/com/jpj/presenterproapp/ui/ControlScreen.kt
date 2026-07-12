package com.jpj.presenterproapp.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.juul.kable.Advertisement
import com.benasher44.uuid.uuidFrom
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.Filter
import com.juul.kable.State
import com.juul.kable.peripheral
import com.jpj.presenterproapp.getPlatform
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
    val current_index: Int,
    val presentation_id: String = ""
)

@Serializable
data class WSMessage(
    val type: String,
    val current_index: Int,
    val total_slides: Int? = null,
    val presentation_id: String? = null
)

@Serializable
data class BleStateResponse(
    val index: Int,
    val total: Int,
    val notes: String
)

class ControlScreenModel(
    val ip: String,
    val port: Int,
    val isBle: Boolean,
    val bleAddress: String?,
    val bleAdvertisement: Advertisement? = null
) : ScreenModel {
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

    private val _presentationId = MutableStateFlow("")
    val presentationId = _presentationId.asStateFlow()

    private val _isBleMode = MutableStateFlow(isBle)
    val isBleMode = _isBleMode.asStateFlow()

    private val _currentIp = MutableStateFlow(ip)
    val currentIp = _currentIp.asStateFlow()

    private val _currentPort = MutableStateFlow(port)
    val currentPort = _currentPort.asStateFlow()

    private var blePeripheral: Peripheral? = null

    init {
        if (isBle) {
            startBleConnection()
        } else {
            loadSlides()
            startWebSocket()
        }
    }

    private fun loadSlides() {
        screenModelScope.launch {
            try {
                val response: SlidesResponse = client.get("http://${_currentIp.value}:${_currentPort.value}/slides").body()
                _slides.value = response.slides
                _currentIndex.value = response.current_index
                _presentationId.value = response.presentation_id
                _isConnected.value = true
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    private fun startWebSocket() {
        screenModelScope.launch {
            try {
                client.webSocket("ws://${_currentIp.value}:${_currentPort.value}/ws/mirror") {
                    _isConnected.value = true
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val msg = Json.decodeFromString<WSMessage>(text)
                            if (msg.type == "state") {
                                _currentIndex.value = msg.current_index
                                if (!msg.presentation_id.isNullOrEmpty() && msg.presentation_id != _presentationId.value) {
                                    loadSlides()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    private fun startBleConnection() {
        screenModelScope.launch {
            try {
                val peripheral = if (bleAdvertisement != null) {
                    screenModelScope.peripheral(bleAdvertisement)
                } else {
                    // Scan for the advertisement matching our SERVICE_UUID
                    val scanner = Scanner {
                        filters = listOf(Filter.Service(uuidFrom("4fafc201-1fb5-459e-8fcc-c5c9c331914b")))
                    }
                    val advertisement = scanner.advertisements.first()
                    screenModelScope.peripheral(advertisement)
                }

                // Track connection status
                screenModelScope.launch {
                    peripheral.state.collect { state ->
                        _isConnected.value = (state == State.Connected)
                    }
                }

                peripheral.connect()
                blePeripheral = peripheral

                // 1. Send pairing request to Desktop
                try {
                    val cmdChar = com.juul.kable.characteristicOf(
                        service = "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
                        characteristic = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
                    )
                    val deviceName = getPlatform().name
                    val pairCmd = "action=request_pair&device=$deviceName"
                    peripheral.write(cmdChar, pairCmd.encodeToByteArray())
                } catch (cmdErr: Exception) {
                    println("Failed to send pairing request: ${cmdErr.message}")
                }

                // 2. Poll Wi-Fi status characteristic from BLE until accepted
                try {
                    val wifiChar = com.juul.kable.characteristicOf(
                        service = "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
                        characteristic = "e74b3e6c-a496-48c5-9276-f3680e599ad6"
                    )
                    
                    // Poll status in a coroutine loop
                    screenModelScope.launch {
                        for (i in 1..30) { // Timeout after 30 seconds
                            kotlinx.coroutines.delay(1000)
                            try {
                                val wifiBytes = peripheral.read(wifiChar)
                                val wifiJson = wifiBytes.decodeToString()
                                
                                val statusRegex = """"status"\s*:\s*"([^"]+)"""".toRegex()
                                val status = statusRegex.find(wifiJson)?.groupValues?.get(1) ?: "none"
                                
                                if (status == "accepted") {
                                    val ssidRegex = """"ssid"\s*:\s*"([^"]+)"""".toRegex()
                                    val passRegex = """"passphrase"\s*:\s*"([^"]+)"""".toRegex()
                                    
                                    val ssid = ssidRegex.find(wifiJson)?.groupValues?.get(1)
                                    val passphrase = passRegex.find(wifiJson)?.groupValues?.get(1)

                                    if (ssid != null && passphrase != null) {
                                        getPlatform().connectToWifi(ssid, passphrase,
                                            onSuccess = { gatewayIp ->
                                                _currentIp.value = gatewayIp
                                                _currentPort.value = 5000
                                                _isBleMode.value = false
                                                loadSlides()
                                                startWebSocket()
                                            },
                                            onError = { error ->
                                                println("Wi-Fi Direct auto-connect failed: $error")
                                            }
                                        )
                                    }
                                    break
                                } else if (status == "declined") {
                                    println("Pairing request was declined by the presenter.")
                                    break
                                }
                            } catch (e: Exception) {
                                println("Failed to read pairing status: ${e.message}")
                            }
                        }
                    }
                } catch (wifiErr: Exception) {
                    println("Could not initialize pairing status polling: ${wifiErr.message}")
                }

                // 2. Observe slide state characteristic updates (as fallback/backup)
                val stateChar = com.juul.kable.characteristicOf(
                    service = "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
                    characteristic = "d66e744b-449e-4c74-a60d-c07a9e99e28a"
                )

                peripheral.observe(stateChar)
                    .collect { data ->
                        try {
                            val text = data.decodeToString()
                            val state = Json.decodeFromString<BleStateResponse>(text)
                            _currentIndex.value = state.index
                            
                            // Dynamically generate basic slide representations
                            val dummyList = List(state.total) { i ->
                                if (i == state.index) {
                                    SlideInfo(title = "Slide ${i + 1}", notes = state.notes)
                                } else {
                                    SlideInfo(title = "Slide ${i + 1}", notes = "")
                                }
                            }
                            _slides.value = dummyList
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    fun next() {
        if (_isBleMode.value) {
            sendBleCommand("action=next")
        } else {
            screenModelScope.launch {
                try {
                    client.post("http://${_currentIp.value}:${_currentPort.value}/control/navigate?action=next")
                } catch (e: Exception) {}
            }
        }
    }

    fun prev() {
        if (_isBleMode.value) {
            sendBleCommand("action=prev")
        } else {
            screenModelScope.launch {
                try {
                    client.post("http://${_currentIp.value}:${_currentPort.value}/control/navigate?action=prev")
                } catch (e: Exception) {}
            }
        }
    }

    fun toggleStandby() {
        if (_isBleMode.value) {
            sendBleCommand("action=standby")
        } else {
            screenModelScope.launch {
                try {
                    client.post("http://${_currentIp.value}:${_currentPort.value}/control/standby")
                } catch (e: Exception) {}
            }
        }
    }

    private fun sendBleCommand(command: String) {
        screenModelScope.launch {
            try {
                val peripheral = blePeripheral ?: return@launch
                val cmdChar = com.juul.kable.characteristicOf(
                    service = "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
                    characteristic = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
                )
                peripheral.write(cmdChar, command.encodeToByteArray(), com.juul.kable.WriteType.WithoutResponse)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDispose() {
        client.close()
        screenModelScope.launch {
            try {
                blePeripheral?.disconnect()
            } catch (e: Exception) {}
        }
        super.onDispose()
    }
}

data class ControlScreen(
    val ip: String,
    val port: Int,
    val isBle: Boolean = false,
    val bleAddress: String? = null,
    val bleAdvertisement: Advertisement? = null
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ControlScreenModel(ip, port, isBle, bleAddress, bleAdvertisement) }
        val currentIndex by model.currentIndex.collectAsState()
        val slides by model.slides.collectAsState()
        val isConnected by model.isConnected.collectAsState()
        val presentationId by model.presentationId.collectAsState()
        val isBleMode by model.isBleMode.collectAsState()
        val currentIp by model.currentIp.collectAsState()
        val currentPort by model.currentPort.collectAsState()

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "PRESENTER PRO",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 1.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBackIosNew,
                                contentDescription = "Back",
                                tint = NeonCyan
                            )
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isConnected) Color(0xFF152A2D) else Color(0xFF2C1E21)
                                )
                                .border(
                                    1.dp,
                                    if (isConnected) NeonCyan.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isConnected) (if (isBleMode) "BLE ONLINE" else "WIFI ONLINE") else "OFFLINE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) NeonCyan else Color.Red
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = DarkBg
                    )
                )
            },
            bottomBar = {
                BottomAppBar(
                    containerColor = DarkSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .height(72.dp)
                        .border(
                            1.dp,
                            AccentTeal.copy(alpha = 0.2f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { model.prev() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(DarkBg, RoundedCornerShape(24.dp))
                                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateBefore,
                                contentDescription = "Previous",
                                tint = NeonCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { model.toggleStandby() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(NeonCyan, RoundedCornerShape(28.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Standby",
                                tint = DarkBg,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { model.next() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(DarkBg, RoundedCornerShape(24.dp))
                                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.NavigateNext,
                                contentDescription = "Next",
                                tint = NeonCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            },
            containerColor = DarkBg
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (isConnected) {
                    if (slides.isNotEmpty() && currentIndex < slides.size) {
                        // Slide Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkSurface)
                                .border(1.dp, AccentTeal.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        if (dragAmount > 50) model.prev()
                                        else if (dragAmount < -50) model.next()
                                        change.consume()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBleMode) {
                                // BLE Mode doesn't support streaming heavy images over radio, show active state
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Bluetooth Offline Control",
                                        color = MutedGrey,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Slide ${currentIndex + 1} of ${slides.size}",
                                        color = NeonCyan,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            } else {
                                val imageUrl = "http://$currentIp:$currentPort/slides/$currentIndex/image?pid=$presentationId"
                                getPlatform().RemoteImage(
                                    url = imageUrl,
                                    contentDescription = "Slide Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    onLoading = {
                                        CircularProgressIndicator(color = NeonCyan)
                                    },
                                    onFailure = { exception ->
                                        Text(
                                            text = "Load Error: ${exception.message}",
                                            color = Color.Red,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Speaker Notes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SPEAKER NOTES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "SLIDE ${currentIndex + 1} / ${slides.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MutedGrey
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, AccentTeal.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = slides[currentIndex].notes.ifEmpty { "No notes defined for this slide." },
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    } else {
                        // Loading presentation data
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = NeonCyan)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Synchronizing presentation data...", color = MutedGrey)
                            }
                        }
                    }
                } else {
                    // Connection lost state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Connecting to server...",
                                color = NeonCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Please wait while we establish a reliable connection link.",
                                color = MutedGrey,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            CircularProgressIndicator(color = NeonCyan)
                        }
                    }
                }
            }
        }
    }
}
