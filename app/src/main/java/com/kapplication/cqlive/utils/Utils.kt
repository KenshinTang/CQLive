package com.kapplication.cqlive.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.starcor.xul.XulDataNode
import com.starcor.xulapp.utils.XulLog
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.StringWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * Created by Kenshin on 2018/7/19.
 */
class Utils{
    companion object {
//        const val HOST = "http://dev.yusihuo.com/zhiboapi.php"
        const val HOST = "http://cqgd.yusihuo.com//zhiboapi.php"

        fun printXulDataNode(data: XulDataNode) {
            var xmlSerializer: XmlSerializer? = null
            try {
                xmlSerializer = XmlPullParserFactory.newInstance().newSerializer()
                val stringWriter = StringWriter()

                xmlSerializer!!.setOutput(stringWriter)
                xmlSerializer.startDocument("utf-8", true)
                XulDataNode.dumpXulDataNode(data, xmlSerializer)
                xmlSerializer.endDocument()
                xmlSerializer.flush()

                val info = stringWriter.toString()
                printLongLog(info)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

        fun printLongLog(s: String) {
            var index = 0
            val maxLength = 3000
            var sub: String
            XulLog.i("Utils", "-------------------------------")
            while (index < s.length) {
                if (s.length <= index + maxLength) {
                    sub = s.substring(index)
                } else {
                    sub = s.substring(index, maxLength + index)
                }

                index += maxLength
                XulLog.i("Utils", sub)
            }
            XulLog.i("Utils", "-------------------------------")
        }

        fun getVersionName(context: Context): String {
            val packInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return packInfo.versionName
        }

        fun getIpAddress(context: Context): String {
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

        fun getMac(context: Context): String {
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

        fun getBytesPerSecond(time: Int, totalBytes: Long): String {
            if (time == 0) {
                return "0 Kb/s"
            }
            val tempResult: Float = (totalBytes / time).toFloat() * 1000 * 8
            return if (tempResult.toInt() > (1024 * 1024)) {
                String.format("%.2f Mb/s", ((tempResult / 1024 / 1024)))
            } else {
                "${(tempResult / 1024).toInt()} Kb/s"
            }
        }
    }
}