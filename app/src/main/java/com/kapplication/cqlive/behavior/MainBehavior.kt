package com.kapplication.cqlive.behavior

import android.app.Activity
import com.kapplication.cqlive.message.CommonMessage
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.starcor.xul.XulView
import com.starcor.xulapp.XulPresenter
import com.starcor.xulapp.behavior.XulBehaviorManager
import com.starcor.xulapp.behavior.XulUiBehavior
import com.starcor.xulapp.message.XulSubscriber
import com.starcor.xulapp.utils.XulLog

class MainBehavior(xulPresenter: XulPresenter) : BaseBehavior(xulPresenter) {
    companion object {
        const val NAME = "MainBehavior"

        fun register() {
            XulBehaviorManager.registerBehavior(NAME,
                    object : XulBehaviorManager.IBehaviorFactory {
                        override fun createBehavior(
                                xulPresenter: XulPresenter): XulUiBehavior {
                            return MainBehavior(xulPresenter)
                        }

                        override fun getBehaviorClass(): Class<*> {
                            return MainBehavior::class.java
                        }
                    })
        }
    }

    private var mMediaPlayer: StandardGSYVideoPlayer? = null

    override fun xulOnRenderIsReady() {
        initView()
        requestPlayUrl()
        super.xulOnRenderIsReady()
    }

    private fun initView() {
        mMediaPlayer = xulGetRenderContext().findItemById("player").externalView as StandardGSYVideoPlayer
        mMediaPlayer!!.setVideoAllCallBack(object: GSYSampleCallBack() {
            override fun onAutoComplete(url: String?, vararg objects: Any?) {
                (context as Activity).finish()
                super.onAutoComplete(url, *objects)
            }
        })
        mMediaPlayer!!.setBottomProgressBarDrawable(null)
    }

    private fun requestPlayUrl() {
        mMediaPlayer!!.setUp("", true, "name")
        mMediaPlayer!!.startPlayLogic()
    }

    @XulSubscriber(tag = CommonMessage.EVENT_HALF_SECOND)
    private fun onHalfSecondPassed(dummy: Any) {
    }

    @XulSubscriber(tag = CommonMessage.EVENT_HALF_HOUR)
    private fun onHalfHourPassed(dummy: Any) {
    }

    override fun xulDoAction(view: XulView?, action: String?, type: String?, command: String?, userdata: Any?) {
        XulLog.i(NAME, "action = $action, type = $type, command = $command, userdata = $userdata")
        when (command) {
            "open_vod_player" -> XulLog.d(NAME, "open vod player")
            "open_live_player" -> XulLog.d(NAME, "open live player")
        }
        super.xulDoAction(view, action, type, command, userdata)
    }
}