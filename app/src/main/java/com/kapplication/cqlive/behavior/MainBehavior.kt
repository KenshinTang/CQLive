package com.kapplication.cqlive.behavior

import android.os.Handler
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kapplication.cqlive.message.CommonMessage
import com.kapplication.cqlive.utils.Utils
import com.kapplication.cqlive.widget.NoUiGSYPlayer
import com.kapplication.cqlive.widget.PlayerSeekBarRender
import com.kapplication.cqlive.widget.XulExt_GSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.starcor.xul.IXulExternalView
import com.starcor.xul.Wrapper.XulMassiveAreaWrapper
import com.starcor.xul.Wrapper.XulSliderAreaWrapper
import com.starcor.xul.XulArea
import com.starcor.xul.XulDataNode
import com.starcor.xul.XulView
import com.starcor.xulapp.XulApplication
import com.starcor.xulapp.XulPresenter
import com.starcor.xulapp.behavior.XulBehaviorManager
import com.starcor.xulapp.behavior.XulUiBehavior
import com.starcor.xulapp.message.XulSubscriber
import com.starcor.xulapp.utils.XulLog
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

class MainBehavior(xulPresenter: XulPresenter) : BaseBehavior(xulPresenter) {
    companion object {
        const val NAME = "MainBehavior"
        const val THREE_HOURS_IN_SECONDS = 3600 * 3

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

    private lateinit var mCategoryListWrapper: XulMassiveAreaWrapper
    private lateinit var mChannelListWrapper: XulMassiveAreaWrapper
    private lateinit var mTitleArea: XulArea
    private lateinit var mChannelArea: XulArea
    private lateinit var mControlArea: XulArea
    private lateinit var mMediaTimeStartView: XulView
    private lateinit var mMediaTimeEndView: XulView
    private lateinit var mSeekBarRender: PlayerSeekBarRender

    private var mIsChannelListShow: Boolean = false
    private var mIsControlFrameShow: Boolean = false

    private var mChannelNode: XulDataNode? = null
    private var mCurrentChannelId: String? = "429535885"
    private var mCurrentCategoryId: String? = ""
    private var mFirst: Boolean = true

    private val dateFormat = SimpleDateFormat("HH:mm:ss")
    private val currentDate = Date()
    private val timeshiftDate = Date()
    private val threeHoursAgoDate = Date()

    private val mMainBehavior = WeakReference<MainBehavior>(this)
    private val mHandler = HideUIHandler(mMainBehavior)

    class HideUIHandler(private val mainBehavior: WeakReference<MainBehavior>): Handler() {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                CommonMessage.EVENT_AUTO_HIDE_UI->{
                    if (mainBehavior.get()!!.mIsChannelListShow) {
                        mainBehavior.get()!!.showChannelList(false)
                    }
                    if (mainBehavior.get()!!.mIsControlFrameShow) {
                        mainBehavior.get()!!.showControlFrame(false)
                    }
                }
            }
        }
    }

    override fun xulOnRenderIsReady() {
        XulLog.i("CQLive", "xulOnRenderIsReady")
        initView()
        requestChannel()
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

        mTitleArea = xulGetRenderContext().findItemById("title-frame") as XulArea
        mChannelArea = xulGetRenderContext().findItemById("category-list") as XulArea
        mControlArea = xulGetRenderContext().findItemById("control-frame") as XulArea

        mMediaTimeStartView = xulGetRenderContext().findItemById("player-time-begin")
        mMediaTimeEndView = xulGetRenderContext().findItemById("player-time-end")
        mSeekBarRender = xulGetRenderContext().findItemById("player-pos").render as PlayerSeekBarRender
        mSeekBarRender.setSeekBarTips("直播中")
        mSeekBarRender.seekBarPos = 1.0f
    }

    private fun requestChannel() {
        val urlBuilder = HttpUrl.parse(Utils.HOST)!!.newBuilder()
            .addQueryParameter("m", "Live")
            .addQueryParameter("c", "Live")
            .addQueryParameter("a", "getLiveListIncludeCategory")

        XulLog.i(NAME, "Request url: ${urlBuilder.build()}")

        val request: Request = Request.Builder().cacheControl(cacheControl).url(urlBuilder.build()).build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call?, response: Response?) {
                response!!.body().use { responseBody ->
                    if (!response.isSuccessful) {
                        XulLog.e(NAME, "getLiveListIncludeCategory onResponse, but is not Successful")
                        throw IOException("Unexpected code $response")
                    }

                    XulLog.i(NAME, "getLiveListIncludeCategory onResponse")

                    val result : String = responseBody!!.string()
                    XulLog.json(NAME, result)

                    mChannelNode = XulDataNode.buildFromJson(result)
                    val dataNode = mChannelNode

                    if (handleError(dataNode)) {
                        XulApplication.getAppInstance().postToMainLooper {
                            showEmptyTips(true)
                        }
                    } else {
                        XulApplication.getAppInstance().postToMainLooper {
                            if (dataNode?.getChildNode("data")?.size() == 0) {
                                showEmptyTips(true)
                            } else {
                                var categoryNode: XulDataNode? = dataNode?.getChildNode("data")?.firstChild
                                mCurrentCategoryId = categoryNode?.getAttributeValue("category_id")
                                while (categoryNode != null) {
                                    mCategoryListWrapper.addItem(categoryNode)
                                    categoryNode = categoryNode.next
                                }

                                mCategoryListWrapper.syncContentView()

                                xulGetRenderContext().layout.requestFocus(mCategoryListWrapper.getItemView(0))
                                mCategoryListWrapper.getItemView(0)?.resetRender()
                            }
                        }
                    }
                }
            }

            override fun onFailure(call: Call?, e: IOException?) {
                XulLog.e(NAME, "getAssetCategoryList onFailure")
                XulApplication.getAppInstance().postToMainLooper {
                    showEmptyTips(true)
                }
            }
        })
    }

    private fun switchCategory(categoryId: String?) {
        if (categoryId.isNullOrEmpty()) {
            return
        }

        mChannelListWrapper.clear()
        mChannelListWrapper.asView?.parent?.dynamicFocus = null
        XulSliderAreaWrapper.fromXulView(mChannelListWrapper.asView)?.scrollTo(0, false)

        var categoryNode: XulDataNode? = mChannelNode?.getChildNode("data")?.firstChild
        var channelList: XulDataNode? = null
        while (categoryNode != null) {
            val id: String? = categoryNode.getAttributeValue("category_id")
            if (id == categoryId) {
                channelList = categoryNode.getChildNode("live_list")
                break
            }

            categoryNode = categoryNode.next
        }

        if (channelList != null && channelList.size() > 0) {
            showEmptyTips(false)
            var channelNode: XulDataNode? = channelList.firstChild
            while (channelNode != null) {
                channelNode.setAttribute("category_id", channelNode.parent.parent.getAttributeValue("category_id"))
                mChannelListWrapper.addItem(channelNode)
                channelNode = channelNode.next
            }

            mChannelListWrapper.syncContentView()

            var index = 0
            mChannelListWrapper.eachItem { idx, node ->
                val v: XulView? = mChannelListWrapper.getItemView(idx)
                if (node.getAttributeValue("live_id") == mCurrentChannelId) {
                    v?.addClass("category_checked")
                    mChannelListWrapper.asView?.parent?.dynamicFocus = v
                    index = idx
                } else {
                    v?.removeClass("category_checked")
                }
                v?.resetRender()
            }

            if (mFirst) {
                xulGetRenderContext().layout.requestFocus(mChannelListWrapper.getItemView(index))
                requestPlayUrl(mCurrentChannelId)
                mFirst = false
            }
        } else {
            showEmptyTips(true)
        }
    }

    private fun syncFocus() {
        mChannelListWrapper.eachItem { idx, node ->
            val v: XulView? = mChannelListWrapper.getItemView(idx)
            if (node.getAttributeValue("live_id") == mCurrentChannelId) {
                v?.addClass("category_checked")
                mChannelListWrapper.asView?.parent?.dynamicFocus = v
                xulGetRenderContext().layout.requestFocus(v)
            } else {
                v?.removeClass("category_checked")
            }
            v?.resetRender()
        }
    }

    private fun requestPlayUrl(channelId: String?) {
        if (channelId == mCurrentChannelId && !mFirst) {
            return
        }
        mCurrentChannelId = channelId
        val channelNum = getChannelNumById(channelId!!)
        val channelName = getChannelNameById(channelId)
        val channelUrl = getChannelUrlById(channelId)
        XulLog.i(NAME, "channelId = $channelId, channelNum = $channelNum, channelName = $channelName, playUrl = $channelUrl")
        xulGetRenderContext().findItemById("live_num")?.setAttr("text", channelNum)
        xulGetRenderContext().findItemById("live_num")?.resetRender()
        xulGetRenderContext().findItemById("live_name")?.setAttr("text", channelName)
        xulGetRenderContext().findItemById("live_name")?.resetRender()

        mMediaPlayer.setUp(channelUrl, true, "name")
        mMediaPlayer.startPlayLogic()
    }

    private fun requestPreviousPlayUrl() {
        var categoryNode: XulDataNode? = mChannelNode?.getChildNode("data")?.firstChild
        var liveListNode: XulDataNode? = null
        while (categoryNode != null) {
            if (mCurrentCategoryId == categoryNode.getAttributeValue("category_id")) {
                liveListNode = categoryNode.getChildNode("live_list")?.firstChild
                break
            }
            categoryNode = categoryNode.next
        }

        var previousLiveListNode: XulDataNode? = null
        while (liveListNode != null) {
            if (mCurrentChannelId == liveListNode.getAttributeValue("live_id")) {
                previousLiveListNode = if (liveListNode == categoryNode?.getChildNode("live_list")?.firstChild) {
                    categoryNode.getChildNode("live_list")?.lastChild
                } else {
                    liveListNode.prev
                }
                break
            }
            liveListNode = liveListNode.next
        }

        val previousChannelId: String? = previousLiveListNode?.getAttributeValue("live_id")
        requestPlayUrl(previousChannelId)
    }

    private fun requestNextPlayUrl() {
        var categoryNode: XulDataNode? = mChannelNode?.getChildNode("data")?.firstChild
        var liveListNode: XulDataNode? = null
        while (categoryNode != null) {
            if (mCurrentCategoryId == categoryNode.getAttributeValue("category_id")) {
                liveListNode = categoryNode.getChildNode("live_list")?.firstChild
                break
            }
            categoryNode = categoryNode.next
        }

        var nextLiveListNode: XulDataNode? = null
        while (liveListNode != null) {
            if (mCurrentChannelId == liveListNode.getAttributeValue("live_id")) {
                nextLiveListNode = if (liveListNode == categoryNode?.getChildNode("live_list")?.lastChild) {
                    categoryNode.getChildNode("live_list")?.firstChild
                } else {
                    liveListNode.next
                }
                break
            }
            liveListNode = liveListNode.next
        }

        val nextChannelId: String? = nextLiveListNode?.getAttributeValue("live_id")
        requestPlayUrl(nextChannelId)
    }

    private fun getChannelNumById(channelId: String): String {
        var liveListNode =
            mChannelNode?.getChildNode("data")?.firstChild?.getChildNode("live_list")?.firstChild
        while (liveListNode != null) {
            val liveId = liveListNode.getAttributeValue("live_id")
            if (liveId == channelId) {
                return liveListNode.getAttributeValue("live_number")?:""
            }
            liveListNode = liveListNode.next
        }
        return ""
    }

    private fun getChannelNameById(channelId: String): String {
        var liveListNode =
            mChannelNode?.getChildNode("data")?.firstChild?.getChildNode("live_list")?.firstChild
        while (liveListNode != null) {
            val liveId = liveListNode.getAttributeValue("live_id")
            if (liveId == channelId) {
                return liveListNode.getAttributeValue("live_name")?:""
            }
            liveListNode = liveListNode.next
        }
        return ""
    }

    private fun getChannelUrlById(channelId: String): String {
        var liveListNode =
            mChannelNode?.getChildNode("data")?.firstChild?.getChildNode("live_list")?.firstChild
        while (liveListNode != null) {
            val liveId = liveListNode.getAttributeValue("live_id")
            if (liveId == channelId) {
                return liveListNode.getAttributeValue("play_url")?:""
            }
            liveListNode = liveListNode.next
        }
        return ""
    }

    private fun showEmptyTips(show: Boolean) {
        val emptyView: XulView = xulGetRenderContext().findItemById("area_none_channel")
        emptyView.setStyle("display", if(show) "block" else "none")
        emptyView.resetRender()
    }

    override fun xulOnBackPressed(): Boolean {
        if (mIsChannelListShow) {
            showChannelList(false)
            return true
        }
        if (mIsControlFrameShow) {
            showControlFrame(false)
            return true
        }
        val tipView: XulView? = xulGetRenderContext().findItemById("operate-tip")
        if (tipView?.getStyleString("display") == "block") {
            tipView.setStyle("display", "none")
            tipView.resetRender()
            return true
        }
        return super.xulOnBackPressed()
    }

    override fun xulOnDestroy() {
        mMediaPlayer.release()
        super.xulOnDestroy()
    }

    override fun xulCreateExternalView(cls: String, x: Int, y: Int, width: Int, height: Int, view: XulView): IXulExternalView? {
        if ("GSYVideoPlayer" == cls) {
            val player = XulExt_GSYVideoPlayer(context)
            _presenter.xulGetRenderContextView().addView(player, width, height)
            return player
        }

        return null
    }

    @XulSubscriber(tag = CommonMessage.EVENT_FIVE_SECOND)
    private fun onFiveSecondPassed(dummy: Any) {
        XulLog.i(NAME, "5 seconds passed, dismiss operate tips.")
        xulGetRenderContext().findItemById("operate-tip").setStyle("display", "none")
        xulGetRenderContext().findItemById("operate-tip").resetRender()
    }

    @XulSubscriber(tag = CommonMessage.EVENT_HALF_SECOND)
    private fun onHalfSecondPassed(dummy: Any) {
        val currentTimeMillis = System.currentTimeMillis()
        if (currentTimeMillis / 1000 != currentDate.time / 1000) {
            currentDate.time = currentTimeMillis
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            mMediaTimeEndView.setAttr("text", dateFormat.format(currentDate))
            mMediaTimeEndView.resetRender()

            threeHoursAgoDate.time = currentTimeMillis - THREE_HOURS_IN_SECONDS * 1000
            mMediaTimeStartView.setAttr("text", dateFormat.format(threeHoursAgoDate))
            mMediaTimeStartView.resetRender()

            timeshiftDate.time = currentTimeMillis - ((1.0f - mSeekBarRender.seekBarPos) * THREE_HOURS_IN_SECONDS * 1000).toLong()
            mSeekBarRender.setSeekBarTips(if (mSeekBarRender.seekBarPos == 1.0f) "直播中" else dateFormat.format(timeshiftDate))
        }
    }

    @XulSubscriber(tag = CommonMessage.EVENT_HALF_HOUR)
    private fun onHalfHourPassed(dummy: Any) {
    }

    override fun xulDoAction(view: XulView?, action: String?, type: String?, command: String?, userdata: Any?) {
        XulLog.i(NAME, "action = $action, type = $type, command = $command, userdata = $userdata")
        when (action) {
            "switchCategory" -> {
                val data = JSONObject(command)
                switchCategory(data.optString("category_id"))
            }
            "switchChannel" -> {
                val data = JSONObject(command)
                mCurrentCategoryId = data.optString("category_id")
                requestPlayUrl(data.optString("live_id"))
            }
        }
        super.xulDoAction(view, action, type, command, userdata)
    }

    private var direction = 0
    override fun xulOnDispatchKeyEvent(event: KeyEvent?): Boolean {
        XulLog.i(NAME, "event = $event")
        if (mHandler.hasMessages(CommonMessage.EVENT_AUTO_HIDE_UI)) {
            mHandler.removeMessages(CommonMessage.EVENT_AUTO_HIDE_UI)
            mHandler.sendEmptyMessageDelayed(CommonMessage.EVENT_AUTO_HIDE_UI, 8 * 1000)
        }

        if (event?.keyCode != KeyEvent.KEYCODE_BACK) {
            xulGetRenderContext().findItemById("operate-tip").setStyle("display", "none")
            xulGetRenderContext().findItemById("operate-tip").resetRender()
        }

        if (event?.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (!mIsChannelListShow && !mIsControlFrameShow) {
                        showChannelList(true)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!mIsChannelListShow && !mIsControlFrameShow) {
                        showControlFrame(true)
                        return true
                    }
                    if (mIsControlFrameShow) {
                        val step = when(event.repeatCount) {
                            0 -> 10
                            in 1..10 -> 30
                            in 11..30 -> 60
                            else -> 90
                        }
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            if (direction - step < -THREE_HOURS_IN_SECONDS) {
                                direction = -THREE_HOURS_IN_SECONDS
                            } else {
                                direction -= step
                            }
                            showTimeshiftIndicator(-1)
                        } else {
                            if (direction + step > 0) {
                                direction = 0
                            } else {
                                direction += step
                            }
                            showTimeshiftIndicator(1)
                        }
                        mSeekBarRender.seekBarPos = (THREE_HOURS_IN_SECONDS + direction) / THREE_HOURS_IN_SECONDS.toFloat()
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!mIsChannelListShow && !mIsControlFrameShow) {
                        XulLog.i(NAME, "up pressed.")
                        requestPreviousPlayUrl()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!mIsChannelListShow && !mIsControlFrameShow) {
                        XulLog.i(NAME, "down pressed.")
                        requestNextPlayUrl()
                        return true
                    }
                }
            }
        }
        if (event?.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (mIsControlFrameShow) {
                        showTimeshiftIndicator(0)
//                        mMediaPlayer.seekTo(-10*1000)
                        //doTimeshift()
                        return true
                    }
                }
            }
        }

        return super.xulOnDispatchKeyEvent(event)
    }

    private fun showChannelList(show: Boolean) {
        if (show) {
            syncFocus()
            mHandler.sendEmptyMessageDelayed(CommonMessage.EVENT_AUTO_HIDE_UI, 8 * 1000)
        }
        mChannelArea.setAttr("x", if(show) "0" else "-1060")
        mChannelArea.resetRender()
        mIsChannelListShow = show

        mTitleArea.setStyle("display", "none")
        mTitleArea.resetRender()
        mControlArea.setAttr("y", "1080")
        mControlArea.resetRender()
    }

    private fun showControlFrame(show: Boolean) {
        if (show) {
//            mHandler.sendEmptyMessageDelayed(CommonMessage.EVENT_AUTO_HIDE_UI, 8 * 1000)
        }
        mTitleArea.setStyle("display", if(show) "block" else "none")
        mTitleArea.resetRender()
        mControlArea.setAttr("y", if(show) "0" else "1080")
        mControlArea.resetRender()
        mIsControlFrameShow = show

        mChannelArea.setAttr("x", "-1060")
        mChannelArea.resetRender()
    }

    private fun showTimeshiftIndicator(direction: Int) {
        //<0 rewind, >0 fast forward
        val playerState: XulView = xulGetRenderContext().findItemById("player-state")
        when (direction) {
            -1 -> playerState.setAttr("img.3.visible", "true")
            1 -> playerState.setAttr("img.2.visible", "true")
            0 -> {
                playerState.setAttr("img.2.visible", "false")
                playerState.setAttr("img.3.visible", "false")
            }
        }
        playerState.resetRender()
    }
}