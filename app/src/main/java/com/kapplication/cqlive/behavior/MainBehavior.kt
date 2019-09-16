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
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.utils.GSYVideoType
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
import com.starcor.xulapp.cache.XulCacheCenter
import com.starcor.xulapp.cache.XulCacheDomain
import com.starcor.xulapp.message.XulSubscriber
import com.starcor.xulapp.utils.XulLog
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

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

    private var mMediaPlayer: NoUiGSYPlayer = NoUiGSYPlayer(context)

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

    private var mDataNode: XulDataNode? = null   //全量数据
    private var mCollectionNode: XulDataNode? = null
    private var mCurrentChannelNode: XulDataNode? = null
    private var mCurrentChannelId: String? = "429535885"
    private var mCurrentCategoryId: String? = ""
    private var mUpDownSwitchChannelNodes: ArrayList<XulDataNode> = ArrayList()
    private var mUpDownTmpSwitchChannelNodes: ArrayList<XulDataNode> = ArrayList()
    private var mCurrentChannelIndex = 0  // current channel index in current channel list
    private var mFirst: Boolean = true
    private var mPreloadSuccess: Boolean = false

    private var mCurrentVideoManager: GSYVideoManager? = GSYVideoManager.instance()
    private var mNextVideoManager: GSYVideoManager? = null
    private var mNextVideoManager2: GSYVideoManager? = null
    private var mUpVideoManager: GSYVideoManager? = null
    private var mDownVideoManager: GSYVideoManager? = null

    private var mLiveCollectionCache: XulCacheDomain? = null

    private val mMainBehavior = WeakReference<MainBehavior>(this)
    private val mHandler = HideUIHandler(mMainBehavior)

    class HideUIHandler(private val mainBehavior: WeakReference<MainBehavior>): Handler() {
        override fun handleMessage(msg: Message?) {
            val instance: MainBehavior? = mainBehavior.get()
            when (msg?.what) {
                CommonMessage.EVENT_AUTO_HIDE_UI -> {
                    if (instance != null) {
                        if (instance.mIsChannelListShow) {
                            instance.showChannelList(false)
                        }
                        if (instance.mIsControlFrameShow) {
                            instance.showControlFrame(false)
                        }
                    }
                }
                CommonMessage.EVENT_PRELOAD_PLAY_RES -> {
                    val view: XulView = msg.obj as XulView
                    if (instance != null) {
                        if (view == instance.xulGetFocus() && !view.hasClass("category_checked")) {
                            val liveNode: XulDataNode? = view.bindingData?.get(0)
                            instance.preloadPlayRes(liveNode)
                        }
                    }
                }
            }
        }
    }

    override fun xulOnRenderIsReady() {
        XulLog.i(NAME, "xulOnRenderIsReady")
        mLiveCollectionCache = XulCacheCenter.buildCacheDomain(1)
            .setDomainFlags(XulCacheCenter.CACHE_FLAG_VERSION_LOCAL
                        or XulCacheCenter.CACHE_FLAG_PERSISTENT
                        or XulCacheCenter.CACHE_FLAG_PROPERTY).build()
        initView()
        requestChannel()
        super.xulOnRenderIsReady()
    }

    override fun initRenderContextView(renderContextView: View): View {
        XulLog.i(NAME, "initRenderContextView")
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

        GSYVideoType.setShowType(GSYVideoType.SCREEN_MATCH_FULL)
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

                    mDataNode = XulDataNode.buildFromJson(result)
                    val dataNode: XulDataNode? = mDataNode

                    if (handleError(dataNode)) {
                        XulApplication.getAppInstance().postToMainLooper {
                            showEmptyTips(true)
                        }
                    } else {
                        XulApplication.getAppInstance().postToMainLooper {
                            if (dataNode?.getChildNode("data")?.size() == 0) {
                                showEmptyTips(true)
                            } else {
                                appendCollectionCategory()
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

    private fun switchCategory(categoryId: String?, categoryName: String?) {
        if (categoryId.isNullOrEmpty()) {
            return
        }

        mUpDownTmpSwitchChannelNodes.clear()
        mChannelListWrapper.clear()
        mChannelListWrapper.asView?.parent?.dynamicFocus = null
        XulSliderAreaWrapper.fromXulView(mChannelListWrapper.asView)?.scrollTo(0, false)

        xulGetRenderContext().findItemById("category_name_label").setAttr("text", categoryName)
        xulGetRenderContext().findItemById("category_name_label").resetRender()

        var categoryNode: XulDataNode? = mDataNode?.getChildNode("data")?.firstChild
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
                channelNode.setAttribute("category_id", categoryId)
                mChannelListWrapper.addItem(channelNode)
                mUpDownTmpSwitchChannelNodes.add(channelNode)
                channelNode = channelNode.next
            }

            mChannelListWrapper.syncContentView()

            mChannelListWrapper.eachItem { idx, node ->
                val v: XulView? = mChannelListWrapper.getItemView(idx)
                val liveId: String = node.getAttributeValue("live_id")
                if (liveId == mCurrentChannelId) {
                    v?.addClass("category_checked")
                    mChannelListWrapper.asView?.parent?.dynamicFocus = v
                    mCurrentChannelIndex = idx
                } else {
                    v?.removeClass("category_checked")
                }

                v?.findItemById("collectState")?.setAttr("img.0.visible", "false")
                v?.findItemById("collectState")?.resetRender()
                var collect: XulDataNode? = mCollectionNode?.firstChild
                while (collect != null) {
                    if (collect.getAttributeValue("live_id") == liveId) {
                        v?.findItemById("collectState")?.setAttr("img.0.visible", "true")
                        v?.findItemById("collectState")?.resetRender()
                        break
                    }
                    collect = collect.next
                }
            }

            if (mFirst) {
                xulGetRenderContext().layout.requestFocus(mChannelListWrapper.getItemView(mCurrentChannelIndex))
                mUpDownSwitchChannelNodes.addAll(mUpDownTmpSwitchChannelNodes)
                val url: String = requestPlayUrl(mCurrentChannelId)
                startToPlay(url, 0)
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

    private fun requestPlayUrl(channelId: String?): String {
        if (channelId == mCurrentChannelId && !mFirst) {
            return ""
        }
        mCurrentChannelId = channelId
        updateTitleArea(channelId!!)

        return mCurrentChannelNode?.getAttributeValue("play_url")?:""
    }

    private fun startToPlay(playUrl: String, upOrDown: Int) {
        //upOrDown -1 -> 按上键触发的播放
        //upOrDown =0 -> 非上下键触发的播放, 比如频道列表选择
        //upOrDown =1 -> 按下键触发的播放
        if (upOrDown == 0) {
            if (mFirst || !mPreloadSuccess) {
                XulLog.i("kenshin", "play!!!")
                mMediaPlayer.isReleaseWhenLossAudio = true
                GSYVideoManager.instance().stop()
                GSYVideoManager.instance().releaseMediaPlayer()
                mMediaPlayer.setUp(playUrl, false, "")
                mMediaPlayer.startPlayLogic()
            } else {
                XulLog.w("kenshin", "play preload!!!")
                GSYVideoManager.instance().stop()
                GSYVideoManager.instance().releaseMediaPlayer()
                GSYVideoManager.changeManager(mCurrentVideoManager)
                GSYVideoManager.instance().setDisplay(mMediaPlayer.getSurface())
                GSYVideoManager.instance().start()
                mPreloadSuccess = false
                return
            }

            updateTitleArea(mCurrentChannelId!!)

            // pre load both up and down source
            var upIndex: Int = mCurrentChannelIndex - 1
            if (upIndex < 0) upIndex = mUpDownSwitchChannelNodes.size - 1
            var downIndex: Int = mCurrentChannelIndex + 1
            if (downIndex == mUpDownSwitchChannelNodes.size) downIndex = 0
            XulLog.i("kenshin1", "CurrentIndex = $mCurrentChannelIndex, upIndex = $upIndex, downIndex = $downIndex")
            mUpVideoManager = GSYVideoManager.tmpInstance(null)
            mUpVideoManager?.prepare(mUpDownSwitchChannelNodes[upIndex].getAttributeValue("play_url"), null, false, 1f, false, null)
            mDownVideoManager = GSYVideoManager.tmpInstance(null)
            mDownVideoManager?.prepare(mUpDownSwitchChannelNodes[downIndex].getAttributeValue("play_url"), null, false, 1f, false, null)
            return
        }

        GSYVideoManager.instance().stop()
        GSYVideoManager.instance().releaseMediaPlayer()
        when (upOrDown) {
            -1 -> {
                GSYVideoManager.changeManager(mUpVideoManager)
                mDownVideoManager?.stop()
                mDownVideoManager?.releaseMediaPlayer()
            }
            1 -> {
                GSYVideoManager.changeManager(mDownVideoManager)
                mUpVideoManager?.stop()
                mUpVideoManager?.releaseMediaPlayer()
            }
            else -> return
        }
        GSYVideoManager.instance().setDisplay(mMediaPlayer.getSurface())
        GSYVideoManager.instance().start()

        mCurrentChannelIndex += upOrDown
        if (mCurrentChannelIndex < 0) mCurrentChannelIndex = mUpDownSwitchChannelNodes.size - 1
        if (mCurrentChannelIndex == mUpDownSwitchChannelNodes.size) mCurrentChannelIndex = 0
        mCurrentChannelId = mUpDownSwitchChannelNodes[mCurrentChannelIndex].getAttributeValue("live_id")
        updateTitleArea(mCurrentChannelId!!)

        var upIndex: Int = mCurrentChannelIndex - 1
        if (upIndex < 0) upIndex = mUpDownSwitchChannelNodes.size - 1
        var downIndex: Int = mCurrentChannelIndex + 1
        if (downIndex == mUpDownSwitchChannelNodes.size) downIndex = 0

        XulLog.e("kenshin1", "CurrentIndex = $mCurrentChannelIndex, upIndex = $upIndex, downIndex = $downIndex")
        val nextUpPlayUrl: String = mUpDownSwitchChannelNodes[upIndex].getAttributeValue("play_url")
        mUpVideoManager = GSYVideoManager.tmpInstance(null)
        mUpVideoManager?.prepare(nextUpPlayUrl, null, false, 1f, false, null)

        val nextDownPlayUrl: String = mUpDownSwitchChannelNodes[downIndex].getAttributeValue("play_url")
        mDownVideoManager = GSYVideoManager.tmpInstance(null)
        mDownVideoManager?.prepare(nextDownPlayUrl, null, false, 1f, false, null)
        mCurrentVideoManager = if (upOrDown == -1) mUpVideoManager else mDownVideoManager
    }

    private fun preloadPlayRes(channelNode: XulDataNode?) {
        if (channelNode == null) return

        val playUrl: String? = channelNode.getAttributeValue("play_url")
        XulLog.i("kenshin", "preload url: $playUrl")

        if (mNextVideoManager2 == null || (mNextVideoManager != null && mNextVideoManager!!.isPlaying)) {
            mNextVideoManager2?.stop()
            mNextVideoManager2?.releaseMediaPlayer()
            mNextVideoManager2 = GSYVideoManager.tmpInstance(null)
            mNextVideoManager2?.prepare(playUrl, null, false, 1f, false, null)
            mCurrentVideoManager = mNextVideoManager2
            mPreloadSuccess = true
        } else if (mNextVideoManager == null || (mNextVideoManager2 != null && mNextVideoManager2!!.isPlaying)) {
            mNextVideoManager?.stop()
            mNextVideoManager?.releaseMediaPlayer()
            mNextVideoManager = GSYVideoManager.tmpInstance(null)
            mNextVideoManager?.prepare(playUrl, null, false, 1f, false, null)
            mCurrentVideoManager = mNextVideoManager
            mPreloadSuccess = true
        }
    }

    private fun appendCollectionCategory() {
        val collectCategory: XulDataNode = XulDataNode.obtainDataNode("collection")
        collectCategory.setAttribute("category_id", "collection")
        collectCategory.setAttribute("category_name", "收藏频道")
        collectCategory.setAttribute("default_icon_img_url", "file:///.assets/images/img_collect.png")

        val cache: Any? = mLiveCollectionCache?.getAsObject("live_list")
        if (cache != null) {
            mCollectionNode = cache as XulDataNode
        }

        if (mCollectionNode == null) {
            mCollectionNode = XulDataNode.obtainDataNode("live_list")
        }

        collectCategory.appendChild(mCollectionNode)

        mDataNode?.getChildNode("data")?.appendChild(collectCategory)
    }

    private fun addToCollection(node: XulDataNode?) {
        if (node != null) {
            mCollectionNode?.appendChild(node)
            mLiveCollectionCache?.put("live_list", mCollectionNode)
        }
    }

    private fun addToCollection(liveId: String?, liveName: String?, liveNumber: String?, liveIcon: String?, playUrl: String?, categoryId: String?) {
        val liveNode: XulDataNode = XulDataNode.obtainDataNode("live")
        liveNode.setAttribute("live_id", liveId)
        liveNode.setAttribute("live_name", liveName)
        liveNode.setAttribute("live_number", liveNumber)
        liveNode.setAttribute("category_id", categoryId)
        liveNode.setAttribute("icon_img_url", liveIcon)
        liveNode.setAttribute("play_url", playUrl)

        mCollectionNode?.appendChild(liveNode)
        mLiveCollectionCache?.put("live_list", mCollectionNode)
    }

    private fun removeFromCollection(node: XulDataNode?) {
        var liveNode: XulDataNode? = mCollectionNode?.firstChild
        while (liveNode != null) {
            if (liveNode.getAttributeValue("live_id") == node?.getAttributeValue("live_id")) {
                mCollectionNode?.removeChild(liveNode)
                break
            }

            liveNode = liveNode.next
        }

        mLiveCollectionCache?.put("live_list", mCollectionNode)
    }

    private fun updateTitleArea(channelId: String) {
        mCurrentChannelNode = mDataNode?.getChildNode("data")?.firstChild?.getChildNode("live_list")?.firstChild
        while (mCurrentChannelNode != null) {
            val liveId: String? = mCurrentChannelNode?.getAttributeValue("live_id")
            if (liveId == channelId) {
                break
            }
            mCurrentChannelNode = mCurrentChannelNode?.next
        }

        val channelNum: String = mCurrentChannelNode?.getAttributeValue("live_number")?:""
        val channelName: String = mCurrentChannelNode?.getAttributeValue("live_name")?:""
        XulLog.i(NAME, "channelId = $channelId, channelNum = $channelNum, channelName = $channelName")
        xulGetRenderContext().findItemById("live_num")?.setAttr("text", channelNum)
        xulGetRenderContext().findItemById("live_num")?.resetRender()
        xulGetRenderContext().findItemById("live_name")?.setAttr("text", channelName)
        xulGetRenderContext().findItemById("live_name")?.resetRender()
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

        GSYVideoManager.instance().releaseMediaPlayer()
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

    private val dateFormat = SimpleDateFormat("HH:mm:ss")
    private val currentDate = Date()
    private val timeshiftDate = Date()
    private val threeHoursAgoDate = Date()
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
                switchCategory(data.optString("category_id"), data.optString("category_name"))
            }
            "switchChannel" -> {
                val data = JSONObject(command)
                mCurrentCategoryId = data.optString("category_id")
                val liveId = data.optString("live_id")
                val url: String = requestPlayUrl(liveId)
                mUpDownSwitchChannelNodes.clear()
                mUpDownSwitchChannelNodes.addAll(mUpDownTmpSwitchChannelNodes)
                for (node: XulDataNode in mUpDownSwitchChannelNodes) {
                    if (node.getAttributeValue("live_id") == liveId) {
                        mCurrentChannelIndex = mUpDownSwitchChannelNodes.indexOf(node)
                        break
                    }
                }
                startToPlay(url, 0)
            }
            "preloadPlayRes" -> {
                if (mIsChannelListShow) {
                    val focusView: XulView? = xulGetFocus()
                    val preloadMessage: Message = Message().apply {
                        what = CommonMessage.EVENT_PRELOAD_PLAY_RES
                        obj = focusView
                    }
                    mHandler.sendMessageDelayed(preloadMessage, 50)
                }
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
            }
        }
        if (event?.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (mIsControlFrameShow) {
                        showTimeshiftIndicator(0)
                        mMediaPlayer.seekTo(-10)
                        //doTimeshift()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!mIsChannelListShow && !mIsControlFrameShow) {
                        XulLog.i(NAME, "up pressed.")
                        startToPlay("", -1)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!mIsChannelListShow && !mIsControlFrameShow) {
                        XulLog.i(NAME, "down pressed.")
                        startToPlay("", 1)
                        return true
                    }
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_1 -> {
                    if (mIsChannelListShow) {
                        val focusView: XulView? = xulGetFocus()
                        val collectState: XulView? = focusView?.findItemById("collectState")
                        when (collectState?.getAttrString("img.0.visible")) {
                            "true" -> {
                                removeFromCollection(focusView.bindingData?.get(0))
                                collectState.setAttr("img.0.visible", "false")
                                collectState.resetRender()
                            }
                            "false" -> {
                                val bingDataNode: XulDataNode? = focusView.bindingData?.get(0)
                                addToCollection(bingDataNode?.getAttributeValue("live_id"), bingDataNode?.getAttributeValue("live_name"),
                                    bingDataNode?.getAttributeValue("live_number"), bingDataNode?.getAttributeValue("icon_img_url"),
                                    bingDataNode?.getAttributeValue("play_url"), bingDataNode?.getAttributeValue("category_id"))
                                collectState.setAttr("img.0.visible", "true")
                                collectState.resetRender()
                            }
                            else -> return super.xulOnDispatchKeyEvent(event)
                        }
                        collectState.resetRender()
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
            mHandler.sendEmptyMessageDelayed(CommonMessage.EVENT_AUTO_HIDE_UI, 8 * 1000)
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