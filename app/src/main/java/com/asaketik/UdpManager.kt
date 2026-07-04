package com.asaketik

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpManager {
    private var socket: DatagramSocket? = null

    suspend fun sendKey(targetIp: String, targetPort: Int, modifier: Byte, keyCode: Byte) {
        withContext(Dispatchers.IO) {
            val data = byteArrayOf(0x01, modifier, keyCode)
            sendPacket(targetIp, targetPort, data)
            delay(50)
            sendPacket(targetIp, targetPort, byteArrayOf(0x01, 0, 0))
        }
    }

    suspend fun sendMouseReport(targetIp: String, targetPort: Int, buttons: Byte, dx: Byte, dy: Byte, wheel: Byte) {
        withContext(Dispatchers.IO) {
            sendPacket(targetIp, targetPort, byteArrayOf(0x02, buttons, dx, dy, wheel))
        }
    }

    private fun sendPacket(ip: String, port: Int, data: ByteArray) {
        if (socket == null || socket?.isClosed == true) {
            socket = DatagramSocket()
        }
        val address = InetAddress.getByName(ip)
        val packet = DatagramPacket(data, data.size, address, port)
        socket?.send(packet)
    }

    fun close() {
        socket?.close()
        socket = null
    }
}
