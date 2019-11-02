package com.kapplication.cqlive

import com.kapplication.cqlive.behavior.MainBehavior
import com.kapplication.cqlive.behavior.MainBehavior2
import com.kapplication.cqlive.message.CommonMessage
import com.kapplication.cqlive.widget.PlayerSeekBarRender
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import com.starcor.xulapp.XulApplication
import com.starcor.xulapp.debug.XulDebugServer
import com.starcor.xulapp.message.XulMessageCenter
import com.starcor.xulapp.utils.XulLog
import com.starcor.xulapp.utils.XulResPrefetchManager
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager


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

        preloadImage()
    }

    private fun preloadImage() {
        XulResPrefetchManager.prefetchImage("file:///.assets/images/icon_star.png", 32, 32, -1)
        XulResPrefetchManager.prefetchImage("file:///.assets/images/player/images/img_tv.png", 250, 173, -1)

//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777363447774.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777364337937.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777369564352.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777371698962.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777372592210.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777375889472.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777383948015.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777385949031.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777388254845.png", 60, 60, -1)
//        XulResPrefetchManager.prefetchImage("http://dev.yusihuo.com//Data/ZhiBoManage/File/Live/Icon/2019/09/06/156777390822252.png", 60, 60, -1)
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
        MainBehavior2.register()

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
//            .setDelay(1000 * 60 * 1)
            .setDelay(1000 * 6 * 1)
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