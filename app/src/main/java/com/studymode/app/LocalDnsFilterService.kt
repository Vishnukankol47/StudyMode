package com.studymode.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LocalDnsFilterService : VpnService() {
    private val localStore by lazy { LocalStudyStore(applicationContext) }
    private val dnsWorkers = ThreadPoolExecutor(
        DNS_WORKER_COUNT,
        DNS_WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(DNS_QUEUE_CAPACITY),
    )
    private val outputLock = Any()
    private var tunnel: ParcelFileDescriptor? = null
    private var tunnelOutput: FileOutputStream? = null
    private var readerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFilter(clearError = true)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startFilter()
        return START_STICKY
    }

    override fun onRevoke() {
        updateState(active = false, error = "Android VPN access was revoked.")
        stopFilter(clearError = false)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopFilter(clearError = false)
        dnsWorkers.shutdownNow()
        super.onDestroy()
    }

    private fun startFilter() {
        if (tunnel != null) return
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            updateState(
                active = false,
                error = "Site filtering is unavailable on Android 9 to preserve its Private DNS settings.",
            )
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, createNotification())
        val fileDescriptor = Builder()
            .setSession("StudyMode local domain filter")
            .setMtu(TUN_MTU)
            .addAddress(VPN_CLIENT_ADDRESS, VPN_PREFIX_LENGTH)
            .addDnsServer(VPN_DNS_ADDRESS)
            .addRoute(VPN_DNS_ADDRESS, VPN_PREFIX_LENGTH)
            .establish()

        if (fileDescriptor == null) {
            updateState(active = false, error = "Android could not establish the local VPN.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        tunnel = fileDescriptor
        tunnelOutput = FileOutputStream(fileDescriptor.fileDescriptor)
        updateState(active = true, error = null)
        readerThread = Thread({ readTunnel(fileDescriptor) }, "StudyModeDnsTunnel").apply {
            isDaemon = true
            start()
        }
    }

    private fun readTunnel(fileDescriptor: ParcelFileDescriptor) {
        val input = FileInputStream(fileDescriptor.fileDescriptor)
        val packetBuffer = ByteArray(MAX_TUN_PACKET_SIZE)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val packetLength = input.read(packetBuffer)
                if (packetLength < 0) break
                val packet = packetBuffer.copyOf(packetLength)
                try {
                    dnsWorkers.execute { processDnsPacket(packet) }
                } catch (exception: RejectedExecutionException) {
                    updateState(active = true, error = "The local DNS filter is busy; some lookups may time out.")
                }
            }
        } catch (exception: IOException) {
            if (tunnel === fileDescriptor) {
                updateState(active = false, error = "The local VPN tunnel stopped unexpectedly.")
                stopSelf()
            }
        } finally {
            input.close()
        }
    }

    private fun processDnsPacket(packet: ByteArray) {
        if (tunnel == null) return
        val request = parseIpv4DnsRequest(packet, packet.size, VPN_DNS_ADDRESS_BYTES) ?: return
        if (request.dnsPayload.size < DNS_HEADER_LENGTH ||
            (request.dnsPayload[2].toInt() and 0x80) != 0
        ) {
            return
        }
        val question = parseDnsQuestion(request.dnsPayload, request.dnsPayload.size) ?: return
        var response = when {
            localStore.isDomainBlocked(question.hostname) ->
                buildDnsErrorResponse(request.dnsPayload, request.dnsPayload.size, DNS_NAME_ERROR)
            else -> forwardDnsQuery(request.dnsPayload)
                ?: buildDnsErrorResponse(request.dnsPayload, request.dnsPayload.size, DNS_SERVER_FAILURE)
        } ?: return

        if (!LocalStudyDnsResponse.isValid(request.dnsPayload, response)) {
            response = buildDnsErrorResponse(request.dnsPayload, request.dnsPayload.size, DNS_SERVER_FAILURE)
                ?: return
        }
        if (response.size + IPV4_UDP_HEADER_SIZE > TUN_MTU) {
            response = buildDnsErrorResponse(request.dnsPayload, request.dnsPayload.size, DNS_SERVER_FAILURE)
                ?: return
        }
        val responsePacket = buildIpv4UdpResponse(request, response) ?: return
        synchronized(outputLock) {
            try {
                tunnelOutput?.write(responsePacket)
            } catch (exception: IOException) {
                updateState(active = false, error = "The local VPN could not return a DNS response.")
                stopSelf()
            }
        }
    }

    private fun forwardDnsQuery(query: ByteArray): ByteArray? {
        val networks = underlyingNetworks()
        if (networks.isEmpty()) {
            updateState(active = true, error = "No network DNS resolver is available.")
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (network in networks) {
                val response = querySystemResolver(network, query)
                if (response != null) {
                    updateState(active = true, error = null)
                    return response
                }
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val resolvers = activeNetworkDnsServers(networks)
            for (resolver in resolvers) {
                try {
                    val response = DatagramSocket().use { socket ->
                        if (!protect(socket)) {
                            null
                        } else {
                            socket.soTimeout = DNS_TIMEOUT_MILLIS
                            socket.connect(resolver, DNS_PORT)
                            socket.send(DatagramPacket(query, query.size))

                            val responseBuffer = ByteArray(MAX_DNS_RESPONSE_SIZE)
                            val incoming = DatagramPacket(responseBuffer, responseBuffer.size)
                            socket.receive(incoming)
                            incoming.data.copyOf(incoming.length)
                        }
                    }
                    if (response != null) {
                        updateState(active = true, error = null)
                        return response
                    }
                } catch (exception: SocketTimeoutException) {
                    continue
                } catch (exception: IOException) {
                    continue
                }
            }
        }

        updateState(active = true, error = "The network DNS resolver did not respond.")
        return null
    }

    @Suppress("DEPRECATION")
    private fun underlyingNetworks(): List<Network> {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivity.allNetworks
            .asSequence()
            .filter { network ->
                val capabilities = connectivity.getNetworkCapabilities(network)
                capabilities != null &&
                    capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
            }
            .distinct()
            .toList()
    }

    private fun activeNetworkDnsServers(networks: List<Network>): List<InetAddress> {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return networks.asSequence()
            .flatMap { network ->
                connectivity.getLinkProperties(network)?.dnsServers.orEmpty().asSequence()
            }
            .distinct()
            .toList()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun querySystemResolver(network: Network, query: ByteArray): ByteArray? {
        val completed = CountDownLatch(1)
        var response: ByteArray? = null
        DnsResolver.getInstance().rawQuery(
            network,
            query,
            DnsResolver.FLAG_EMPTY,
            mainExecutor,
            null,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) {
                    response = answer
                    completed.countDown()
                }

                override fun onError(error: DnsResolver.DnsException) {
                    completed.countDown()
                }
            },
        )
        return try {
            if (completed.await(DNS_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)) response else null
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymode)
            .setContentTitle("StudyMode site filter")
            .setContentText("Filtering selected DNS hostnames on this device")
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun stopFilter(clearError: Boolean) {
        var closeError: String? = null
        synchronized(outputLock) {
            tunnelOutput?.let {
                try {
                    it.close()
                } catch (_: IOException) {
                    closeError = "The local VPN tunnel could not close cleanly."
                }
            }
            tunnelOutput = null
        }
        tunnel?.let {
            try {
                it.close()
            } catch (_: IOException) {
                closeError = "The local VPN tunnel could not close cleanly."
            }
        }
        tunnel = null
        readerThread?.interrupt()
        readerThread = null
        val existingError = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(KEY_ERROR, null)
        updateState(active = false, error = closeError ?: if (clearError) null else existingError)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateState(active: Boolean, error: String?) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putString(KEY_ERROR, error)
            .apply()
    }

    companion object {
        private const val ACTION_STOP = "com.studymode.app.STOP_LOCAL_DNS_FILTER"
        private const val PREFERENCES_NAME = "local_dns_filter"
        private const val KEY_ACTIVE = "active"
        private const val KEY_ERROR = "error"
        private const val NOTIFICATION_CHANNEL_ID = "local_domain_filter"
        private const val NOTIFICATION_ID = 7101
        private const val DNS_WORKER_COUNT = 2
        private const val DNS_QUEUE_CAPACITY = 64
        private const val DNS_PORT = 53
        private const val DNS_HEADER_LENGTH = 12
        private const val DNS_TIMEOUT_MILLIS = 3_000
        private const val MAX_DNS_RESPONSE_SIZE = 4_096
        private const val MAX_TUN_PACKET_SIZE = 32_767
        private const val TUN_MTU = 1_500
        private const val IPV4_UDP_HEADER_SIZE = 28
        private const val VPN_CLIENT_ADDRESS = "10.111.222.1"
        private const val VPN_DNS_ADDRESS = "10.111.222.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val DNS_NAME_ERROR = 3
        private const val DNS_SERVER_FAILURE = 2
        private val VPN_DNS_ADDRESS_BYTES = byteArrayOf(10, 111, 222.toByte(), 2)

        fun start(context: Context) {
            createNotificationChannel(context)
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocalDnsFilterService::class.java),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LocalDnsFilterService::class.java).setAction(ACTION_STOP),
            )
        }

        fun isActive(context: Context): Boolean =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACTIVE, false)

        fun lastError(context: Context): String? =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ERROR, null)

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Local site filter",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }

        private object LocalStudyDnsResponse {
            fun isValid(query: ByteArray, response: ByteArray): Boolean =
                isDnsResponseForQuery(query, response)
        }
    }
}
