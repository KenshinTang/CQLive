package com.ccnks.cqlivesdk

import android.content.Context
import android.util.Log
import com.alibaba.fastjson.JSON
import com.ccnks.cqlivesdk.model.CategoryList
import com.ccnks.cqlivesdk.model.ProgramList
import com.ccnks.cqlivesdk.util.Utils
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class CQLiveSDK {
    companion object {
        private const val TAG: String = "CQLiveSDK"
        private const val HOST: String = "http://cqgd.yusihuo.com//zhiboapi.php"

        protected lateinit var okHttpClient: OkHttpClient
        protected lateinit var cacheControl: CacheControl

        private var hasInitialized: Boolean = false

        private lateinit var applicationContext: Context
        private var mac = "null"
        private var packageName = "null"
        private var versionName = "null"

        fun init(context: Context) {
            if (!hasInitialized) {
                applicationContext = context.applicationContext
                mac = Utils.getMac(applicationContext)
                packageName = applicationContext.packageName
                versionName = Utils.getVersionName(applicationContext)

                okHttpClient = OkHttpClient.Builder()
                    .addNetworkInterceptor {
                        val response = it.proceed(it.request())
                        val onlineCacheTime = 5*60
                        return@addNetworkInterceptor response.newBuilder()
                            .removeHeader("Pragma")
                            .header("Cache-Control", "public, max-age=$onlineCacheTime")
                            .build()
                    }
                    .cache(Cache(applicationContext.getDir("okhttpCache_sdk", Context.MODE_PRIVATE), 5*1024*1024))
                    .build()
                cacheControl = CacheControl.Builder().maxAge(5, TimeUnit.MINUTES).build()

                Log.i(TAG, "CQLiveSDK init. mac=$mac, packageName=$packageName, versionName=$versionName")
                hasInitialized = true
            } else {
                Log.w(TAG, "CQLiveSDK had already Initialized.")
            }
        }

        fun getCategoryList(callback: LiveCallback) {
            val urlBuilder = HttpUrl.parse(HOST)!!.newBuilder()
                .addQueryParameter("m", "Live")
                .addQueryParameter("c", "Live")
                .addQueryParameter("a", "getLiveListIncludeCategory")

            Log.i(TAG, "Request url: ${urlBuilder.build()}")

            val request: Request = Request.Builder().cacheControl(cacheControl).url(urlBuilder.build()).build()
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call?, response: Response?) {
                    response!!.body().use { responseBody ->
                        if (!response.isSuccessful) {
                            Log.e(TAG, "getLiveListIncludeCategory onResponse, but is not Successful")
                            throw IOException("Unexpected code $response")
                        }

                        Log.i(TAG, "getLiveListIncludeCategory onResponse")

                        val resultStr : String = responseBody!!.string()
                        val result: CategoryList = JSON.parseObject(resultStr, CategoryList::class.java)
                        Log.i(TAG, "result = $result")
                        if (result.ret == 0) {
                            callback.onSuccess(result = result.data)
                        } else {
                            callback.onFailed(result.ret, result.reason)
                        }
                    }
                }

                override fun onFailure(call: Call?, e: IOException?) {
                    Log.e(TAG, "getAssetCategoryList onFailure")
                    callback.onFailed(-1, e?.toString() ?: "unknown")
                }
            })
        }

        fun getProgramList(channelId: String, callback: LiveCallback) {
            val urlBuilder = HttpUrl.parse(HOST)!!.newBuilder()
                .addQueryParameter("m", "Live")
                .addQueryParameter("c", "LivePlayBill")
                .addQueryParameter("a", "getPlayBillList")
                .addQueryParameter("live_id", channelId)

            Log.i(TAG, "Request url: ${urlBuilder.build()}")

            val request: Request = Request.Builder().cacheControl(cacheControl).url(urlBuilder.build()).build()
            okHttpClient.newCall(request).enqueue(object : Callback{
                override fun onResponse(call: Call?, response: Response?) {
                    response!!.body().use { responseBody ->
                        if (!response.isSuccessful) {
                            Log.e(TAG, "getPlayBillList onResponse, but is not Successful")
                            throw IOException("Unexpected code $response")
                        }

                        Log.i(TAG, "getPlayBillList onResponse")

                        val resultStr: String = responseBody!!.string()
                        val result: ProgramList = JSON.parseObject(resultStr, ProgramList::class.java)
                        Log.i(TAG, "result = $result")
                        if (result.ret == 0) {
                            callback.onSuccess(result = result.data)
                        } else {
                            callback.onFailed(result.ret, result.reason)
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                }
            })
        }
    }
}