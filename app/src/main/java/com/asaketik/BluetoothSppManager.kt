package com.asaketik

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothSppManager(private val context: Context) {
    private companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: BluetoothServerSocket? = null
    private var connectedSocket: BluetoothSocket? = null
    private var serverJob: Job? = null
    private var readJob: Job? = null
    private var outputStream: OutputStream? = null

    private val _connectionState = MutableStateFlow(HidManager.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<HidManager.ConnectionState> = _connectionState

    private val _receivedMessage = MutableStateFlow<String?>(null)
    val receivedMessage: StateFlow<String?> = _receivedMessage

    fun startServer() {
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e("SPP", "Bluetooth is disabled")
            _connectionState.value = HidManager.ConnectionState.DISCONNECTED
            return
        }

        serverJob?.cancel()
        serverJob = ioScope.launch {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("AsaKetikSPP", SPP_UUID)
                _connectionState.value = HidManager.ConnectionState.CONNECTING
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    connectedSocket = socket
                    _connectionState.value = HidManager.ConnectionState.CONNECTED
                    manageConnectedSocket(socket)
                }
            } catch (e: IOException) {
                Log.e("SPP", "Server error", e)
                _connectionState.value = HidManager.ConnectionState.DISCONNECTED
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        outputStream = socket.outputStream
        val inputStream: InputStream = socket.inputStream
        readJob?.cancel()
        readJob = ioScope.launch {
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        _receivedMessage.value = String(buffer, 0, bytes)
                    }
                } catch (e: IOException) {
                    Log.e("SPP", "Read error", e)
                    break
                }
            }
            outputStream = null
            connectedSocket = null
            _connectionState.value = HidManager.ConnectionState.DISCONNECTED
        }
    }

    fun sendMessage(message: String) {
        try {
            outputStream?.write(message.toByteArray())
            outputStream?.flush()
        } catch (e: IOException) {
            Log.e("SPP", "Send error", e)
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        readJob?.cancel()
        outputStream = null
        runCatching { connectedSocket?.close() }
        runCatching { serverSocket?.close() }
        connectedSocket = null
        serverSocket = null
        _connectionState.value = HidManager.ConnectionState.DISCONNECTED
    }

    fun destroy() {
        stopServer()
    }
}
