package com.kapplication.cqlive

import com.kapplication.cqlive.behavior.MainBehavior
import com.kapplication.cqlive.message.CommonMessage
import com.starcor.xulapp.XulApplication
import com.starcor.xulapp.message.XulMessageCenter
import com.starcor.xulapp.utils.XulLog

class KApplication : XulApplication() {

    private val XUL_FIRST_PAGE = "xul_layouts/pages/xul_main_page.xml"
    private val XUL_GLOBAL_BINDINGS = "xul_layouts/xul_global_bindings.xml"
    private val XUL_GLOBAL_SELECTORS = "xul_layouts/xul_global_selectors.xml"
    private val XUL_GLOBAL_DIALOGS = "xul_layouts/pages/xul_global_dialogs.xml"

    override fun onCreate() {
        XulLog.i("CQLive", "KApplication onCreate.")

        super.onCreate()
        startCommonMessage()
    }

    override fun onLoadXul() {
        xulLoadLayouts(XUL_FIRST_PAGE)
        xulLoadLayouts(XUL_GLOBAL_BINDINGS)
        xulLoadLayouts(XUL_GLOBAL_SELECTORS)
        xulLoadLayouts(XUL_GLOBAL_DIALOGS)
        super.onLoadXul()
    }

    override fun onRegisterXulBehaviors() {
        registerComponent()
        UiManager.initUiManager()
    }

    private fun registerComponent() {
//        val appPkgName = packageName
//        val behaviorPkgName = appPkgName + ".behavior"
//        autoRegister(behaviorPkgName, XulUiBehavior::class.java)

        MainBehavior.register()
    }

    private fun startCommonMessage() {
        XulMessageCenter.buildMessage()
            .setTag(CommonMessage.EVENT_HALF_SECOND)
            .setInterval(500)
            .setRepeat(Integer.MAX_VALUE)
            .postSticky()

        XulMessageCenter.buildMessage()
            .setTag(CommonMessage.EVENT_TEN_MINUTES)
            .setInterval(1000 * 60 * 10)
            .setRepeat(Integer.MAX_VALUE)
            .setDelay(1000 * 60 * 1)
            .postSticky()

        XulMessageCenter.buildMessage()
            .setTag(CommonMessage.EVENT_HALF_HOUR)
            .setInterval(1000 * 60 * 30)
//                .setInterval(1000 * 10)
            .setRepeat(Integer.MAX_VALUE)
            .setDelay(1000 * 60 * 30)
            .postSticky()
    }
}