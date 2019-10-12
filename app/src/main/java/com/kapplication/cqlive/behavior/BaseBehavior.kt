package com.kapplication.cqlive.behavior

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.view.View
import android.view.WindowManager
import com.kapplication.cqlive.message.CommonMessage
import com.kapplication.cqlive.upgrade.UpgradeDialog
import com.kapplication.cqlive.upgrade.UpgradeUtils
import com.starcor.xul.Script.IScriptArguments
import com.starcor.xul.Script.IScriptContext
import com.starcor.xul.ScriptWrappr.Annotation.ScriptMethod
import com.starcor.xul.XulDataNode
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
            .addNetworkInterceptor {
                val response = it.proceed(it.request())
                val onlineCacheTime = 5*60
                return@addNetworkInterceptor response.newBuilder()
                    .removeHeader("Pragma")
                    .header("Cache-Control", "public, max-age=$onlineCacheTime")
                    .build()
            }
            .cache(Cache(XulApplication.getAppContext().getDir("okhttpCache", Context.MODE_PRIVATE), 5*1024*1024))
            .build()
    protected val cacheControl: CacheControl = CacheControl.Builder()
            .maxAge(5, TimeUnit.MINUTES)
            .build()

    companion object {
        const val NAME = "BaseBehavior"
    }

    override fun xulOnRenderIsReady() {
//        hideNavButtons()
        super.xulOnRenderIsReady()
    }

    protected fun handleError(dataNode: XulDataNode?) : Boolean {
        if (dataNode == null || dataNode.size() == 0) {
            return true
        }
        val code = dataNode.getAttributeValue("ret")
        val reason = dataNode.getAttributeValue("reason")

        XulLog.d(NAME, "Request result:, ret=$code, reason=$reason")

        if ("0" != code) {
            return true
        }
        return false
    }

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
        UpgradeUtils.instance.startCheckUpgrade(okHttpClient)
    }

    @XulSubscriber(tag = CommonMessage.EVENT_SHOW_UPGRADE)
    private fun onShowUpgradeDialog(dummy: Any) {
        XulLog.d("UpgradeUtils", "onShowUpgradeDialog: $this")
        val xulRenderContext = xulGetRenderContext()
        if (xulRenderContext == null
            || xulRenderContext.isDestroyed
            || xulRenderContext.isSuspended) {
            XulLog.w("UpgradeUtils", "$this :xulRenderContext is invalid.")
            return
        }

        val mUpgradeDialog = UpgradeDialog(_presenter.xulGetContext(), "page_upgrade_dialog")
        mUpgradeDialog.setOkBtnClickListener(DialogInterface.OnClickListener { _, _ -> UpgradeUtils.instance.doUpgrade() })
        // 对话框显示的时候停止检测升级
        mUpgradeDialog.setOnShowListener { UpgradeUtils.instance.stopCheckUpgrade() }
        // 对话框取消了重新开始检测.
        mUpgradeDialog.setOnDismissListener { UpgradeUtils.instance.restartCheckUpgrade() }

        if (mUpgradeDialog.isShowing) {
            XulLog.w("UpgradeUtils", "Upgrade dialog is already showing. just refresh the data!")
            return
        }
        mUpgradeDialog.show()
    }

    private fun hideNavButtons(){
        if (Build.VERSION.SDK_INT in 12..18) { // lower api
            val v: View = (context as Activity).window.decorView
            v.systemUiVisibility = View.GONE
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            val decorView: View = (context as Activity).window.decorView
            val uiOptions: Int = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE
            decorView.systemUiVisibility = uiOptions
            (context as Activity).window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
    }
}
