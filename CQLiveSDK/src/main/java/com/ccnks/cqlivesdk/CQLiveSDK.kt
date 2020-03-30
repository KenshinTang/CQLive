package com.ccnks.cqlivesdk

import android.content.Context
import android.util.Log
import com.ccnks.cqlivesdk.model.Category
import com.ccnks.cqlivesdk.model.Channel
import com.ccnks.cqlivesdk.util.Utils

class CQLiveSDK {
    companion object {
        private const val TAG: String = "CQLiveSDK"

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
                Log.i(TAG, "CQLiveSDK init. mac=$mac, packageName=$packageName, versionName=$versionName")
                hasInitialized = true
            } else {
                Log.w(TAG, "CQLiveSDK had already Initialized.")
            }
        }

        fun getCategoryList(callback: LiveCallback) {
            callback.onSuccess(result = ArrayList<Category>())
            callback.onFailed(0, "test")
        }

        fun getChannelList(categoryId: String, callback: LiveCallback) {
            callback.onSuccess(result = ArrayList<Channel>())
            callback.onFailed(0, "test")
        }

        fun getPlayUrl(channelId: String, callback: LiveCallback) {
            callback.onSuccess(result = ArrayList<String>())
            callback.onFailed(0, "test")
        }
    }
}