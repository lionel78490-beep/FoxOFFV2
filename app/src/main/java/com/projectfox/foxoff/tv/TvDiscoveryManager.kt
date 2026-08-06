package com.projectfox.foxoff.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.projectfox.foxoff.core.logging.FoxLogger
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class TvDiscoveryManager(
    private val context: Context,
    private val onDeviceFound: (TvDevice) -> Unit
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val serviceTypes = listOf(
        "_androidtvremote2._tcp.",
        "_androidtvremote._tcp.",
        "_googlecast._tcp.",
        "_remotemessage._tcp.",
        "_app-remote._tcp.",
        "_spotify-connect._tcp.",
        "_dlna._tcp.",
        "_upnp._tcp.",
        "_adb._tcp."
    )

    private val activeListeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private val discoveredDevices = mutableMapOf<String, TvDevice>()
    private fun getServicePriority(service: String): Int {
        return when (service) {
            "_androidtvremote2._tcp." -> 1
            "_androidtvremote._tcp." -> 2
            "_remotemessage._tcp." -> 3
            "_googlecast._tcp." -> 4
            "_app-remote._tcp." -> 5
            "_spotify-connect._tcp." -> 6
            "_dlna._tcp." -> 7
            "_upnp._tcp." -> 8
            "_adb._tcp." -> 9
            else -> 99
        }
    }

    fun startDiscovery() {
        FoxLogger.i("FOX-TV | SCAN START")
        
        // 1. mDNS Discovery
        FoxLogger.i("FOX-TV | mDNS lancé")
        serviceTypes.forEach { type ->
            val protocolName = when (type) {
                "_androidtvremote2._tcp." -> "AndroidTVRemote2"
                "_androidtvremote._tcp." -> "AndroidTVRemote"
                "_googlecast._tcp." -> "Google Cast"
                else -> type
            }
            FoxLogger.i("FOX-TV | $protocolName lancé")
            
            val listener = createDiscoveryListener(type)
            activeListeners[type] = listener
            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                FoxLogger.e("FOX-TV | Discovery | Failed to start for $type", e)
            }
        }
        
        // 2. SSDP Discovery (UPnP)
        FoxLogger.i("FOX-TV | SSDP lancé")
        scope.launch {
            runSsdpDiscovery()
        }
        
        // Timer to stop
        scope.launch {
            delay(60000)
            stopDiscovery()
            FoxLogger.i("FOX-TV | Scan terminé")
        }
    }

    private suspend fun runSsdpDiscovery() = withContext(Dispatchers.IO) {
        try {
            val ssdpRequest = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: ssdp:all\r\n" +
                    "\r\n"
            
            val socket = DatagramSocket()
            socket.soTimeout = 5000
            val group = InetAddress.getByName("239.255.255.250")
            val packet = DatagramPacket(ssdpRequest.toByteArray(), ssdpRequest.length, group, 1900)
            
            FoxLogger.i("FOX-TV | Scanner | SSDP Request sent")
            socket.send(packet)
            
            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 10000) { // Listen for 10s
                try {
                    socket.receive(receivePacket)
                    val data = String(receivePacket.data, 0, receivePacket.length)
                    val address = receivePacket.address.hostAddress
                    FoxLogger.i("FOX-TV | Scanner | SSDP Response from $address")
                    
                    val server = data.lines().find { it.startsWith("SERVER:", true) }?.substringAfter(":")?.trim()
                    FoxLogger.i("FOX-TV | Device trouvé (SSDP) | IP: $address | Info: $server")

                    val device = TvDevice(
                        id = "ssdp_$address",
                        name = server ?: "UPnP Device",
                        address = address ?: "",
                        port = 0,
                        discoveryType = "SSDP/UPnP",
                        manufacturer = server?.split("/")?.firstOrNull()
                    )
                    publishDevice(device)
                } catch (e: Exception) {
                    break // Timeout or error
                }
            }
            socket.close()
        } catch (e: Exception) {
            FoxLogger.e("FOX-TV | Scanner | SSDP Error", e)
        }
    }

    private fun createDiscoveryListener(serviceType: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}

        override fun onServiceFound(service: NsdServiceInfo) {
            FoxLogger.i("FOX-TV | Scanner | Found: ${service.serviceName} ($serviceType)")
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    FoxLogger.e("FOX-TV | Scanner | Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val address = serviceInfo.host?.hostAddress ?: ""
                    val txt = decodeTxtRecords(serviceInfo)
                    
                    FoxLogger.i("FOX-TV | Device trouvé | Nom: ${serviceInfo.serviceName} | IP: $address | Service: $serviceType | Fabricant: ${txt["md"] ?: txt["mf"] ?: "Inconnu"}")
                    
                    scope.launch {
                        val startTime = System.currentTimeMillis()
                        val openPorts = scanPorts(address)
                        val responseTime = System.currentTimeMillis() - startTime
                        
                        val device = TvDevice(
                            id = serviceInfo.serviceName,
                            name = txt["fn"] ?: serviceInfo.serviceName,
                            address = address,
                            port = serviceInfo.port,
                            discoveryType = serviceType,
                            manufacturer = txt["md"] ?: txt["mf"],
                            model = txt["md"],
                            openPorts = openPorts,
                            txtRecords = txt,
                            lastResponseTimeMs = responseTime,
                            services = listOf(serviceType)
                        )
                        publishDevice(device)
                    }
                }
            })
        }

        override fun onServiceLost(service: NsdServiceInfo) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    private suspend fun scanPorts(address: String): List<Int> = withContext(Dispatchers.IO) {
        val portsToScan = listOf(6466, 6467, 8008, 8009, 1900, 5555)
        val openPorts = mutableListOf<Int>()
        
        portsToScan.forEach { port ->
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), 400)
                    openPorts.add(port)
                }
            } catch (e: Exception) {}
        }
        openPorts
    }

    private fun decodeTxtRecords(info: NsdServiceInfo): Map<String, String> {
        val records = mutableMapOf<String, String>()
        try {
            info.attributes.forEach { (key, value) ->
                records[key] = String(value)
            }
        } catch (e: Exception) {}
        return records
    }
    private fun publishDevice(device: TvDevice) {

        val existing = discoveredDevices[device.address]

        if (existing == null) {

            discoveredDevices[device.address] = device
            onDeviceFound(device)

            FoxLogger.i(
                "FOX-TV | Device ajouté : ${device.name} (${device.discoveryType})"
            )

            return
        }


        val oldPriority = getServicePriority(existing.discoveryType)
        val newPriority = getServicePriority(device.discoveryType)


        if (newPriority < oldPriority) {

            discoveredDevices[device.address] = device
            onDeviceFound(device)

            FoxLogger.i(
                "FOX-TV | Device remplacé par priorité : ${device.discoveryType}"
            )

        } else {

            FoxLogger.i(
                "FOX-TV | Device ignoré : ${device.discoveryType} moins prioritaire que ${existing.discoveryType}"
            )
        }
    }
    fun stopDiscovery() {
        activeListeners.values.forEach { 
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) {}
        }
        activeListeners.clear()
    }
}
