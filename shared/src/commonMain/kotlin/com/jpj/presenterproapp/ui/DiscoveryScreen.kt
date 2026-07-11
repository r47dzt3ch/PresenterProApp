package com.jpj.presenterproapp.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.juul.kable.Advertisement
import com.juul.kable.Scanner
import com.juul.kable.Filter
import com.juul.kable.uuidFrom
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// High-tech Premium Dark Palette
val DarkBg = Color(0xFF0B0C10)
val DarkSurface = Color(0xFF1F2833)
val DarkBorder = Color(0xFF45A29E)
val NeonCyan = Color(0xFF66FCF1)
val AccentTeal = Color(0xFF45A29E)
val MutedGrey = Color(0xFFC5C6C7)

data class DiscoveredServer(
    val name: String,
    val ip: String,
    val port: Int,
    val isBle: Boolean = false,
    val bleAdvertisement: Advertisement? = null
)

class DiscoveryScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val coroutineScope = rememberCoroutineScope()

        var discoveredServers by remember { mutableStateOf(emptyList<DiscoveredServer>()) }
        var showManualConnect by remember { mutableStateOf(false) }

        var ipAddress by remember { mutableStateOf("192.168.1.100") }
        var port by remember { mutableStateOf("5000") }

        // Start mDNS Network Discovery
        DisposableEffect(Unit) {
            val discovery = ServiceDiscovery()
            discovery.startDiscovery { name, ip, port ->
                val newServer = DiscoveredServer(name, ip, port, isBle = false)
                if (discoveredServers.none { it.ip == ip && it.port == port }) {
                    discoveredServers = discoveredServers + newServer
                }
            }
            onDispose {
                discovery.stopDiscovery()
            }
        }

        // Start BLE Scan for offline devices
        LaunchedEffect(Unit) {
            try {
                val bleScanner = Scanner {
                    filters = listOf(
                        Filter.Service(uuidFrom("4fafc201-1fb5-459e-8fcc-c5c9c331914b"))
                    )
                }
                bleScanner.advertisements
                    .catch { e -> println("BLE Scan Error: ${e.message}") }
                    .collect { advertisement ->
                        val name = advertisement.name ?: "PyPresenterPro BLE"
                        val address = advertisement.address
                        val newBleServer = DiscoveredServer(
                            name = name,
                            ip = address, // Use hardware address as unique identifier
                            port = 0,
                            isBle = true,
                            bleAdvertisement = advertisement
                        )
                        if (discoveredServers.none { it.ip == address }) {
                            discoveredServers = discoveredServers + newBleServer
                        }
                    }
            } catch (e: Exception) {
                println("Failed to start BLE scanner: ${e.message}")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // Brand Header
                Text(
                    text = "PyPresenterPro",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "NEXT-GEN PRESENTATION CONTROL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MutedGrey,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Connection List Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface.copy(alpha = 0.6f))
                        .border(1.dp, AccentTeal.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    if (discoveredServers.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Searching for active servers...",
                                color = MutedGrey,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Ensure desktop server is running on the same network or within BLE range",
                                color = MutedGrey.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp, top = 8.dp)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "AVAILABLE CONNECTIONS",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(discoveredServers) { server ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkBg.copy(alpha = 0.8f))
                                            .border(1.dp, NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (server.isBle && server.bleAdvertisement != null) {
                                                    // Navigate passing BLE info
                                                    navigator.push(
                                                        ControlScreen(
                                                            ip = server.ip,
                                                            port = 0,
                                                            isBle = true,
                                                            bleAddress = server.ip
                                                        )
                                                    )
                                                } else {
                                                    navigator.push(
                                                        ControlScreen(
                                                            ip = server.ip,
                                                            port = server.port,
                                                            isBle = false
                                                        )
                                                    )
                                                }
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (server.isBle) Color(0xFF1E2638) else Color(0xFF152A2D)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (server.isBle) Icons.Default.Bluetooth else Icons.Default.Wifi,
                                                contentDescription = null,
                                                tint = if (server.isBle) Color(0xFF2979FF) else NeonCyan
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = server.name,
                                                color = Color.white,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = if (server.isBle) "Offline Mode via Bluetooth" else "${server.ip}:${server.port}",
                                                color = MutedGrey,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Text(
                                            text = "CONNECT",
                                            color = NeonCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Manual connection collapsible card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface.copy(alpha = 0.4f))
                        .border(1.dp, AccentTeal.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManualConnect = !showManualConnect }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Manual Connection Backup",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = if (showManualConnect) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = NeonCyan
                        )
                    }

                    AnimatedVisibility(visible = showManualConnect) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = { ipAddress = it },
                                label = { Text("Desktop IP Address") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = AccentTeal,
                                    focusedLabelColor = NeonCyan,
                                    unfocusedLabelColor = MutedGrey,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it },
                                label = { Text("Port") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = AccentTeal,
                                    focusedLabelColor = NeonCyan,
                                    unfocusedLabelColor = MutedGrey,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    navigator.push(
                                        ControlScreen(
                                            ipAddress,
                                            port.toIntOrNull() ?: 5000,
                                            isBle = false
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "Connect Manually",
                                    color = DarkBg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
