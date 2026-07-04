package com.asaketik

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)
    private val hidManager = HidManager(application, preferencesManager)
    private val sppManager = BluetoothSppManager(application)
    private val udpManager = UdpManager()

    val darkTheme = preferencesManager.darkThemeFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val hidConnectionState = hidManager.connectionState.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        HidManager.ConnectionState.DISCONNECTED
    )
    val sppConnectionState = sppManager.connectionState.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        HidManager.ConnectionState.DISCONNECTED
    )
    val sppReceivedMessage: StateFlow<String?> = sppManager.receivedMessage

    val autoReconnect = preferencesManager.autoReconnectFlow.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val lastHidAddress = preferencesManager.lastHidAddressFlow.stateIn(viewModelScope, SharingStarted.Lazily, "")
    val sppEnabled = preferencesManager.sppEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val udpEnabled = preferencesManager.udpEnabledFlow.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val udpTargetIp = preferencesManager.udpTargetIpFlow.stateIn(viewModelScope, SharingStarted.Lazily, "192.168.1.100")
    val udpTargetPort = preferencesManager.udpTargetPortFlow.stateIn(viewModelScope, SharingStarted.Lazily, 8888)

    fun registerHid() {
        hidManager.register()
    }

    fun unregisterHid() {
        hidManager.unregister()
    }

    fun sendKey(modifier: Byte, keyCode: Byte) {
        hidManager.sendKey(modifier, keyCode)
        if (udpEnabled.value) {
            viewModelScope.launch {
                runCatching {
                    udpManager.sendKey(udpTargetIp.value, udpTargetPort.value, modifier, keyCode)
                }
            }
        }
    }

    fun sendMouse(buttons: Byte, dx: Byte, dy: Byte, wheel: Byte = 0) {
        hidManager.sendMouseReport(buttons, dx, dy, wheel)
        if (udpEnabled.value) {
            viewModelScope.launch {
                runCatching {
                    udpManager.sendMouseReport(udpTargetIp.value, udpTargetPort.value, buttons, dx, dy, wheel)
                }
            }
        }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            preferencesManager.setDarkTheme(!darkTheme.value)
        }
    }

    fun toggleAutoReconnect() {
        viewModelScope.launch {
            preferencesManager.setAutoReconnect(!autoReconnect.value)
        }
    }

    fun sendSppMessage(message: String) {
        sppManager.sendMessage(message)
    }

    fun setSppEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSppEnabled(enabled)
            if (enabled) sppManager.startServer() else sppManager.stopServer()
        }
    }

    fun setUdpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setUdpEnabled(enabled)
        }
    }

    fun setUdpTarget(ip: String, port: Int) {
        viewModelScope.launch {
            preferencesManager.setUdpTargetIp(ip)
            preferencesManager.setUdpTargetPort(port)
        }
    }

    override fun onCleared() {
        hidManager.destroy()
        sppManager.destroy()
        udpManager.close()
        super.onCleared()
    }
}
