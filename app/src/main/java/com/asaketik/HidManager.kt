package com.asaketik

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

@SuppressLint("MissingPermission")
class HidManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var reconnectJob: Job? = null

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            _connectionState.value = if (registered) ConnectionState.CONNECTING else ConnectionState.DISCONNECTED
            Log.d("HID", "App registered: $registered")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    _connectionState.value = ConnectionState.CONNECTED
                    device?.address?.let { address ->
                        ioScope.launch {
                            preferencesManager.setLastHidAddress(address)
                        }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                    maybeReconnect()
                }

                else -> _connectionState.value = ConnectionState.CONNECTING
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as? BluetoothHidDevice
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "AsaKetik",
                "Bluetooth keyboard and mouse",
                "AsaKetik",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                HidReportDescriptor.DESCRIPTOR
            )
            val executor = Executor { command -> command.run() }
            val registered = hidDevice?.registerApp(sdp, null, null, executor, callback) == true
            _connectionState.value = if (registered) ConnectionState.CONNECTING else ConnectionState.DISCONNECTED
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedDevice = null
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED != intent.action) return
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            if (bondState == BluetoothDevice.BOND_BONDED) {
                maybeReconnect()
            }
        }
    }

    init {
        context.registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    fun register() {
        if (bluetoothAdapter?.isEnabled != true) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        bluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
    }

    fun unregister() {
        reconnectJob?.cancel()
        hidDevice?.unregisterApp()
        hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hidDevice = null
        connectedDevice = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun maybeReconnect() {
        reconnectJob?.cancel()
        reconnectJob = ioScope.launch {
            if (preferencesManager.autoReconnectFlow.firstOrNull() != true) return@launch

            val lastAddr = preferencesManager.lastHidAddressFlow.firstOrNull().orEmpty()
            while (isActive && lastAddr.isNotEmpty() && _connectionState.value != ConnectionState.CONNECTED) {
                try {
                    val device = bluetoothAdapter?.getRemoteDevice(lastAddr)
                    if (device?.bondState != BluetoothDevice.BOND_BONDED) {
                        device?.createBond()
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e("HID", "Stored Bluetooth address is invalid", e)
                    break
                } catch (e: RuntimeException) {
                    Log.e("HID", "Reconnect attempt failed", e)
                }
                delay(5000)
            }
        }
    }

    fun sendKey(modifier: Byte, keyCode: Byte) {
        val device = connectedDevice ?: return
        val report = byteArrayOf(modifier, 0x00, keyCode, 0x00, 0x00, 0x00, 0x00, 0x00)
        hidDevice?.sendReport(device, 0x01, report)
        mainHandler.postDelayed({
            hidDevice?.sendReport(device, 0x01, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
        }, 50)
    }

    fun sendMouseReport(buttons: Byte, dx: Byte, dy: Byte, wheel: Byte = 0) {
        val device = connectedDevice ?: return
        hidDevice?.sendReport(device, 0x02, byteArrayOf(buttons, dx, dy, wheel))
    }

    fun destroy() {
        runCatching { context.unregisterReceiver(bondStateReceiver) }
        reconnectJob?.cancel()
        unregister()
    }
}
