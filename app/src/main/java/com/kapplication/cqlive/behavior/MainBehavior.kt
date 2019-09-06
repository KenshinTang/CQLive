package com.kapplication.cqlive.behavior

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kapplication.cqlive.message.CommonMessage
import com.kapplication.cqlive.widget.NoUiGSYPlayer
import com.kapplication.cqlive.widget.XulExt_GSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.starcor.xul.IXulExternalView
import com.starcor.xul.Wrapper.XulMassiveAreaWrapper
import com.starcor.xul.XulDataNode
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

    private var mMediaPlayer: StandardGSYVideoPlayer = NoUiGSYPlayer(context)
    private var mCategoryListWrapper: XulMassiveAreaWrapper? = null
    private var mChannelListWrapper: XulMassiveAreaWrapper? = null

    override fun xulOnRenderIsReady() {
        XulLog.i("CQLive", "xulOnRenderIsReady")
        initView()
        requestPlayUrl()
        super.xulOnRenderIsReady()
    }

    override fun initRenderContextView(renderContextView: View): View {
        XulLog.i("CQLive", "initRenderContextView")
        val viewRoot = FrameLayout(_presenter.xulGetContext())
        val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
        viewRoot.addView(mMediaPlayer, matchParent, matchParent)
        viewRoot.addView(renderContextView, matchParent, matchParent)
        return viewRoot
    }


    private fun initView() {
        mCategoryListWrapper = XulMassiveAreaWrapper.fromXulView(xulGetRenderContext().findItemById("category"))
        mChannelListWrapper = XulMassiveAreaWrapper.fromXulView(xulGetRenderContext().findItemById("channel"))


        val testCategoryNode = XulDataNode.obtainDataNode("text")
        testCategoryNode.appendChild("name", "全部频道")
        testCategoryNode.appendChild("icon", "https://image.flaticon.com/icons/png/512/97/97895.png")
        mCategoryListWrapper?.addItem(testCategoryNode)
        mCategoryListWrapper?.addItem(testCategoryNode)
        mCategoryListWrapper?.addItem(testCategoryNode)
        mCategoryListWrapper?.addItem(testCategoryNode)
//        mCategoryListWrapper?.addItem(testNode)
//        mCategoryListWrapper?.addItem(testNode)
//        mCategoryListWrapper?.addItem(testNode)
//        mCategoryListWrapper?.addItem(testNode)
//        mCategoryListWrapper?.addItem(testNode)
//        mCategoryListWrapper?.addItem(testNode)
//        mCategoryListWrapper?.addItem(testNode)
        mCategoryListWrapper?.syncContentView()


        val testChannelNode = XulDataNode.obtainDataNode("text")
        testCategoryNode.appendChild("channel_name", "上海东方卫视")
        testCategoryNode.appendChild("channel_id", "001")
        testCategoryNode.appendChild("channel_icon", "https://image.flaticon.com/icons/png/512/97/97895.png")
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.addItem(testCategoryNode)
        mChannelListWrapper?.syncContentView()
    }

    private fun requestPlayUrl() {
        mMediaPlayer.setUp("http://117.59.125.132/__cl/cg:ingest01/__c/cctv3/__op/default/__f/index.m3u8", true, "name")
        mMediaPlayer.startPlayLogic()
    }

    override fun xulCreateExternalView(cls: String, x: Int, y: Int, width: Int, height: Int, view: XulView): IXulExternalView? {
        if ("GSYVideoPlayer" == cls) {
            val player = XulExt_GSYVideoPlayer(context)
            _presenter.xulGetRenderContextView().addView(player, width, height)
            return player
        }

        return null
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