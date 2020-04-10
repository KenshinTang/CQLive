package com.kapplication.cqlivesdk.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

class Utils {
    companion object {
        public fun getVersionName(context: Context): String {
            return try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "1.0"
            }
        }

        public fun getMac(context: Context): String {
            var macString = ""
            val buf = StringBuilder()
            try {
                val mac: ByteArray
                val ne = NetworkInterface.getByInetAddress(InetAddress.getByName(getIpAddress(context)))
                mac = ne.hardwareAddress
                for (b in mac) {
                    buf.append(String.format("%02X:", b))
                }
                if (buf.isNotEmpty()) {
                    buf.deleteCharAt(buf.length - 1)
                }
                macString = buf.toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return macString
        }

        public fun getIpAddress(context: Context): String {
            val info = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
            if (info != null && info.isConnected) {
                when {
                    info.type == ConnectivityManager.TYPE_MOBILE -> try {
                        val en = NetworkInterface.getNetworkInterfaces()
                        while (en.hasMoreElements()) {
                            val intf = en.nextElement()
                            val enumIpAddr = intf.inetAddresses
                            while (enumIpAddr.hasMoreElements()) {
                                val inetAddress = enumIpAddr.nextElement()
                                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                                    return inetAddress.getHostAddress()
                                }
                            }
                        }
                    } catch (e: SocketException) {
                        e.printStackTrace()
                    }
                    info.type == ConnectivityManager.TYPE_WIFI -> {
                        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                        val wifiInfo = wifiManager.connectionInfo
                        return intIP2StringIP(wifiInfo.ipAddress)
                    }
                    info.type == ConnectivityManager.TYPE_ETHERNET -> return getLocalIp()
                }
            }
            return ""
        }

        private fun intIP2StringIP(ip: Int): String {
            return (ip and 0xFF).toString() + "." +
                    (ip shr 8 and 0xFF) + "." +
                    (ip shr 16 and 0xFF) + "." +
                    (ip shr 24 and 0xFF)
        }

        private fun getLocalIp(): String {
            try {
                val en = NetworkInterface
                    .getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val intf = en.nextElement()
                    val enumIpAddr = intf
                        .inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            return inetAddress.getHostAddress()
                        }
                    }
                }
            } catch (ex: SocketException) {

            }

            return "0.0.0.0"
        }
    }
}