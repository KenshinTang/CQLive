package com.kapplication.cqlive.behavior

import android.content.Context
import com.kapplication.cqlive.message.CommonMessage
import com.starcor.xul.Script.IScriptArguments
import com.starcor.xul.Script.IScriptContext
import com.starcor.xul.ScriptWrappr.Annotation.ScriptMethod
import com.starcor.xul.XulView
import com.starcor.xulapp.XulApplication
import com.starcor.xulapp.XulPresenter
import com.starcor.xulapp.behavior.XulUiBehavior
import com.starcor.xulapp.message.XulSubscriber
import com.starcor.xulapp.utils.XulLog
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

abstract class BaseBehavior(xulPresenter: XulPresenter) : XulUiBehavior(xulPresenter) {
    protected val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .cache(Cache(XulApplication.getAppContext().getDir("okhttpCache", Context.MODE_PRIVATE), 5*1024*1024))
            .build()
    protected val cacheControl: CacheControl = CacheControl.Builder()
            .maxAge(5, TimeUnit.MINUTES)
            .build()

    companion object {
        const val NAME = "BaseBehavior"
    }

    override fun xulOnRenderIsReady() {
        super.xulOnRenderIsReady()
    }

    protected abstract fun appOnStartUp(success: Boolean)

    @ScriptMethod("refreshBindingByView")
    fun _script_refreshBindingByView(ctx: IScriptContext, args: IScriptArguments): Boolean? {
        if (args.size() != 2) {
            return java.lang.Boolean.FALSE
        }

        val bindingId = args.getString(0)
        val argView = args.getScriptableObject(1)
        if (bindingId == null || argView == null) {
            return java.lang.Boolean.FALSE
        }

        val xulView = argView.objectValue.unwrappedObject as XulView
        if (xulView.bindingData != null && !xulView.bindingData!!.isEmpty()) {
            _xulRenderContext.refreshBinding(
                    bindingId, xulView.bindingData!![0].makeClone())
            return java.lang.Boolean.TRUE
        } else {
            return java.lang.Boolean.FALSE
        }
    }

//    protected fun openVideoListPage(packageId: String?) {
//        XulLog.i(NAME, "openVideoListPage($packageId)")
//        val extInfo = XulDataNode.obtainDataNode("extInfo")
//        extInfo.appendChild("packageId", packageId)
//        UiManager.openUiPage("VideoListPage", extInfo)
//    }


    @XulSubscriber(tag = CommonMessage.EVENT_TEN_MINUTES)
    private fun on10MinutesPassed(dummy: Any) {
        XulLog.d("UpgradeUtils", "check upgrade.")
//        UpgradeUtils.instance.startCheckUpgrade(okHttpClient)
    }

    @XulSubscriber(tag = CommonMessage.EVENT_SHOW_UPGRADE)
    private fun onShowUpgradeDialog(dummy: Any) {
        XulLog.d("UpgradeUtils", "onShowUpgradeDialog: " + this)
    }
}
