package com.kapplication.cqlive

import com.kapplication.cqlive.behavior.MainBehavior
import com.kapplication.cqlive.message.CommonMessage
import com.starcor.xulapp.XulApplication
import com.starcor.xulapp.message.XulMessageCenter
import com.starcor.xulapp.utils.XulLog
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import com.kapplication.cqlive.widget.PlayerSeekBarRender
import com.shuyu.gsyvideoplayer.player.SystemPlayerManager
import com.starcor.xulapp.debug.XulDebugServer


class KApplication : XulApplication() {

    private val XUL_FIRST_PAGE = "xul_layouts/pages/xul_main_page.xml"
    private val XUL_GLOBAL_BINDINGS = "xul_layouts/xul_global_bindings.xml"
    private val XUL_GLOBAL_SELECTORS = "xul_layouts/xul_global_selectors.xml"
    private val XUL_GLOBAL_DIALOGS = "xul_layouts/pages/xul_global_dialogs.xml"

    override fun onCreate() {
        XulLog.i("CQLive", "KApplication onCreate.")
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
        XulDebugServer.startUp()
        super.onCreate()
        startCommonMessage()
    }

    override fun onLoadXul() {
        xulLoadLayouts(XUL_FIRST_PAGE)
        xulLoadLayouts(XUL_GLOBAL_BINDINGS)
        xulLoadLayouts(XUL_GLOBAL_SELECTORS)
        xulLoadLayouts(XUL_GLOBAL_DIALOGS)
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

        PlayerSeekBarRender.register()
    }

    private fun startCommonMessage() {
        XulMessageCenter.buildMessage()
            .setTag(CommonMessage.EVENT_HALF_SECOND)
            .setInterval(500)
            .setRepeat(Integer.MAX_VALUE)
            .postSticky()

        XulMessageCenter.buildMessage()
            .setTag(CommonMessage.EVENT_FIVE_SECOND)
            .setDelay(1000 * 5)
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