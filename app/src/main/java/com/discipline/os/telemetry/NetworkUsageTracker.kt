package com.discipline.os.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import java.text.DecimalFormat

data class NetworkDataStats(
    val totalBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val mobileBytes: Long = 0L,
    val connectionType: String = "Unknown",
    val isConnected: Boolean = true
) {
    val formattedTotal: String get() = formatBytes(totalBytes)
    val formattedWifi: String get() = formatBytes(wifiBytes)
    val formattedMobile: String get() = formatBytes(mobileBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 MB"
            val df = DecimalFormat("#,##0.1")
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return if (gb >= 1.0) {
                "${df.format(gb)} GB"
            } else if (mb >= 1.0) {
                "${df.format(mb)} MB"
            } else {
                "${df.format(kb)} KB"
            }
        }
    }
}

object NetworkUsageTracker {

    fun getNetworkStats(context: Context): NetworkDataStats {
        val totalRx = TrafficStats.getTotalRxBytes()
        val totalTx = TrafficStats.getTotalTxBytes()
        val mobileRx = TrafficStats.getMobileRxBytes()
        val mobileTx = TrafficStats.getMobileTxBytes()

        val validTotalRx = if (totalRx == TrafficStats.UNSUPPORTED.toLong()) 0L else totalRx
        val validTotalTx = if (totalTx == TrafficStats.UNSUPPORTED.toLong()) 0L else totalTx
        val validMobileRx = if (mobileRx == TrafficStats.UNSUPPORTED.toLong()) 0L else mobileRx
        val validMobileTx = if (mobileTx == TrafficStats.UNSUPPORTED.toLong()) 0L else mobileTx

        val totalBytes = validTotalRx + validTotalTx
        val mobileBytes = validMobileRx + validMobileTx
        val wifiBytes = (totalBytes - mobileBytes).coerceAtLeast(0L)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        var connType = "Offline"
        var isConnected = false

        cm?.activeNetwork?.let { activeNet ->
            cm.getNetworkCapabilities(activeNet)?.let { caps ->
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    isConnected = true
                    connType = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (5G/4G)"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                        else -> "Local Network"
                    }
                }
            }
        }

        return NetworkDataStats(
            totalBytes = totalBytes,
            wifiBytes = wifiBytes,
            mobileBytes = mobileBytes,
            connectionType = connType,
            isConnected = isConnected
        )
    }
}
