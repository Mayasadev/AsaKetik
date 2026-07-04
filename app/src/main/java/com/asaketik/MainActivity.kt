package com.asaketik

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.ExperimentalLayoutApi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val darkTheme by viewModel.darkTheme.collectAsState()

            AsaKetikTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionGate()
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@Composable
private fun PermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        launcher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        )
    }
}

@Composable
fun AsaKetikTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AsaKetik", style = MaterialTheme.typography.headlineMedium)
            Switch(
                checked = viewModel.darkTheme.collectAsState().value,
                onCheckedChange = { viewModel.toggleDarkTheme() }
            )
        }

        HidPanel(viewModel)
        KeyboardPanel(viewModel)
        TouchpadPanel(viewModel)
        SppPanel(viewModel)
        UdpPanel(viewModel)
    }
}

@Composable
private fun HidPanel(viewModel: MainViewModel) {
    val hidState by viewModel.hidConnectionState.collectAsState()
    val autoReconnect by viewModel.autoReconnect.collectAsState()
    val lastAddress by viewModel.lastHidAddress.collectAsState()

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bluetooth HID", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${hidState.name}")
            Row {
                Button(onClick = { viewModel.registerHid() }) { Text("Daftarkan HID") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.unregisterHid() }) { Text("Lepas HID") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = autoReconnect, onCheckedChange = { viewModel.toggleAutoReconnect() })
                Text("Auto-reconnect")
            }
            Text("Alamat terakhir: ${lastAddress.ifEmpty { "-" }}")
        }
    }
}

@Composable
private fun KeyboardPanel(viewModel: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Keyboard", style = MaterialTheme.typography.titleMedium)
            VirtualKeyboard(onKeyPressed = viewModel::sendKey)
        }
    }
}

@Composable
private fun TouchpadPanel(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Touchpad", style = MaterialTheme.typography.titleMedium)
            VirtualTouchpad(
                onMove = { dx, dy -> viewModel.sendMouse(0, dx.toHidByte(), dy.toHidByte()) },
                onClick = { button ->
                    viewModel.sendMouse(button.toByte(), 0, 0)
                    scope.launch {
                        delay(80)
                        viewModel.sendMouse(0, 0, 0)
                    }
                },
                onScroll = { wheel -> viewModel.sendMouse(0, 0, 0, wheel.toHidByte()) }
            )
        }
    }
}

@Composable
private fun SppPanel(viewModel: MainViewModel) {
    val sppState by viewModel.sppConnectionState.collectAsState()
    val sppEnabled by viewModel.sppEnabled.collectAsState()
    val received by viewModel.sppReceivedMessage.collectAsState()
    var sppText by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bluetooth SPP", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${sppState.name}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = sppEnabled, onCheckedChange = { viewModel.setSppEnabled(it) })
                Text("Aktifkan server SPP")
            }
            if (sppState == HidManager.ConnectionState.CONNECTED) {
                OutlinedTextField(
                    value = sppText,
                    onValueChange = { sppText = it },
                    label = { Text("Kirim pesan") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    viewModel.sendSppMessage(sppText)
                    sppText = ""
                }) { Text("Kirim") }
                received?.let { Text("Diterima: $it") }
            }
        }
    }
}

@Composable
private fun UdpPanel(viewModel: MainViewModel) {
    val udpEnabled by viewModel.udpEnabled.collectAsState()
    val targetIp by viewModel.udpTargetIp.collectAsState()
    val targetPort by viewModel.udpTargetPort.collectAsState()
    var ip by remember(targetIp) { mutableStateOf(targetIp) }
    var port by remember(targetPort) { mutableStateOf(targetPort.toString()) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("WiFi UDP", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = udpEnabled, onCheckedChange = { viewModel.setUdpEnabled(it) })
                Text("Aktifkan UDP")
            }
            OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP Tujuan") })
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
            Button(onClick = {
                viewModel.setUdpTarget(ip.trim(), port.toIntOrNull() ?: 8888)
            }) { Text("Simpan") }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun VirtualKeyboard(onKeyPressed: (modifier: Byte, keyCode: Byte) -> Unit) {
    val modifierState = remember { mutableStateListOf(false, false, false, false) }
    val modifierByte = buildModifierByte(modifierState)
    val rows = listOf(
        listOf("Q" to 0x14, "W" to 0x1A, "E" to 0x08, "R" to 0x15, "T" to 0x17, "Y" to 0x1C, "U" to 0x18, "I" to 0x0C, "O" to 0x12, "P" to 0x13),
        listOf("A" to 0x04, "S" to 0x16, "D" to 0x07, "F" to 0x09, "G" to 0x0A, "H" to 0x0B, "J" to 0x0D, "K" to 0x0E, "L" to 0x0F),
        listOf("Z" to 0x1D, "X" to 0x1B, "C" to 0x06, "V" to 0x19, "B" to 0x05, "N" to 0x11, "M" to 0x10)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleButton("Ctrl", modifierState[0]) { modifierState[0] = it }
            ToggleButton("Shift", modifierState[1]) { modifierState[1] = it }
            ToggleButton("Alt", modifierState[2]) { modifierState[2] = it }
            ToggleButton("Win", modifierState[3]) { modifierState[3] = it }
        }
        rows.forEach { row ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (label, keyCode) ->
                    Button(onClick = { onKeyPressed(modifierByte, keyCode.toByte()) }) {
                        Text(label)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onKeyPressed(modifierByte, 0x2C) }) { Text("Space") }
            Button(onClick = { onKeyPressed(modifierByte, 0x28) }) { Text("Enter") }
            Button(onClick = { onKeyPressed(modifierByte, 0x2A) }) { Text("Backspace") }
        }
    }
}

private fun buildModifierByte(modifierState: List<Boolean>): Byte {
    var modifier = 0
    if (modifierState[0]) modifier = modifier or 0x01
    if (modifierState[1]) modifier = modifier or 0x02
    if (modifierState[2]) modifier = modifier or 0x04
    if (modifierState[3]) modifier = modifier or 0x08
    return modifier.toByte()
}

@Composable
fun ToggleButton(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        label = { Text(label) }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun VirtualTouchpad(
    onMove: (dx: Float, dy: Float) -> Unit,
    onClick: (button: Int) -> Unit,
    onScroll: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onMove(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick(1) },
                    onLongPress = { onClick(2) }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, _, _ ->
                    onScroll(pan.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text("Sentuh & Seret", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun Float.toHidByte(): Byte {
    return roundToInt().coerceIn(-127, 127).toByte()
}
