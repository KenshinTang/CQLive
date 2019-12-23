package com.kapplication.cqlive.behavior

import android.app.AlertDialog
import android.app.Dialog
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Message
import android.text.TextUtils
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.ui.TrackNameProvider
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.kapplication.cqlive.R
import com.kapplication.cqlive.message.CommonMessage
import com.kapplication.cqlive.utils.KeyEventListener
import com.kapplication.cqlive.utils.Utils
import com.kapplication.cqlive.widget.PlayerSeekBarRender
import com.starcor.xul.Prop.XulPropNameCache
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
import com.tencent.bugly.crashreport.BuglyLog
import com.tencent.bugly.crashreport.CrashReport
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainBehavior(xulPresenter: XulPresenter) : BaseBehavior(xulPresenter), PlayerSeekBarRender.OnProgressChangedListener {

    companion object {
        const val NAME = "MainBehavior"
        const val THREE_HOURS_IN_SECONDS = 3600 * 3

        val keys:CharArray = charArrayOf(
            KeyEventListener.KEY.KEY_MENU, KeyEventListener.KEY.KEY_MENU,
            KeyEventListener.KEY.KEY_MENU, KeyEventListener.KEY.KEY_MENU,
            KeyEventListener.KEY.KEY_MENU, KeyEventListener.KEY.KEY_RIGHT)

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

    private val trackSelector: DefaultTrackSelector = DefaultTrackSelector()
    private var mMediaPlayer: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)
    private var mUpMediaPlayer: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)
    private var mDownMediaPlayer: SimpleExoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)
    private val mDataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(context, Util.getUserAgent(context, "CQLive"))
    private lateinit var mPlayerListener: Player.EventListener
    private lateinit var mAnalyticsListener: AnalyticsListener
    private lateinit var mPlayerView: SurfaceView

    private lateinit var mCategoryListWrapper: XulMassiveAreaWrapper
    private lateinit var mChannelListWrapper: XulMassiveAreaWrapper
    private lateinit var mDateListWrapper: XulMassiveAreaWrapper
    private lateinit var mProgramListWrapper: XulMassiveAreaWrapper
    private lateinit var mTitleArea: XulArea
    private lateinit var mChannelArea: XulArea
    private lateinit var mControlArea: XulArea
    private lateinit var mMediaTimeStartView: XulView
    private lateinit var mMediaTimeEndView: XulView
    private lateinit var mTimeshiftLogoView: XulView
    private lateinit var mCategoryLayer: XulView
    private lateinit var mChannelLayer: XulView
    private lateinit var mDateLayer: XulView
    private lateinit var mProgramLayer: XulView
    private lateinit var mSeekBarRender: PlayerSeekBarRender
    private lateinit var mCurrentProgramView: XulView

    private var mIsChannelListShow: Boolean = false
    private var mIsControlFrameShow: Boolean = false
    private var mIsDebugInfoShow: Boolean = false

    private var mLiveDataNode: XulDataNode? = null   //全量直播数据
    private var mPlaybackDataNode: XulDataNode? = null // 全量回看数据
    private var mCollectionNode: XulDataNode? = null
    private var mCurrentChannelNode: XulDataNode? = null
    private var mCurrentChannelId: String? = ""
    private var mCurrentCategoryId: String? = ""
    private var mUpDownSwitchChannelNodes: ArrayList<XulDataNode> = ArrayList()
    private var mUpDownTmpSwitchChannelNodes: ArrayList<XulDataNode> = ArrayList()
    private var mCurrentChannelIndex = 0  // current channel index in current channel list
    private var mFirst: Boolean = true
    private var mIsLiveMode: Boolean = true  // true: live, false: playback
    private var mErrorDialog: AlertDialog? = null

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
                        if (view == instance.xulGetFocus() && (view.findItemById("playing_indicator").getAttrString("img.0.visible") == "false")) {
                            val liveNode: XulDataNode? = view.bindingData?.get(0)
                            instance.preloadProgram(liveNode)
                        }
                    }
                }
            }
        }
    }

    override fun xulOnRenderIsReady() {
        XulLog.i(NAME, "xulOnRenderIsReady")
        if (!Utils.getVersionName(context).contains(Build.MODEL) && !Utils.getVersionName(context).contains("test")) {
            XulLog.e("CQLive", "Device adaptation failed. This version(${Utils.getVersionName(context)}) is not for this device(${Build.MODEL}).")
            showAdaptationError()
            return
        }
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
        mPlayerView = createPlayerView()
        viewRoot.addView(mPlayerView, matchParent, matchParent)
        viewRoot.addView(renderContextView, matchParent, matchParent)
        return viewRoot
    }

    private fun createPlayerView(): SurfaceView {
        val surfaceView = SurfaceView(context)
        surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                mMediaPlayer.setVideoSurfaceHolder(holder)
            }
        })
        return surfaceView
    }

    private fun initView() {
        mCategoryListWrapper = XulMassiveAreaWrapper.fromXulView(xulGetRenderContext().findItemById("category"))
        mChannelListWrapper = XulMassiveAreaWrapper.fromXulView(xulGetRenderContext().findItemById("channel"))
        mDateListWrapper = XulMassiveAreaWrapper.fromXulView(xulGetRenderContext().findItemById("date"))
        mProgramListWrapper = XulMassiveAreaWrapper.fromXulView(xulGetRenderContext().findItemById("program"))

        mTitleArea = xulGetRenderContext().findItemById("title-frame") as XulArea
        mChannelArea = xulGetRenderContext().findItemById("category-list") as XulArea
        mControlArea = xulGetRenderContext().findItemById("control-frame") as XulArea

        mCategoryLayer = xulGetRenderContext().findItemById("category-layer")
        mChannelLayer = xulGetRenderContext().findItemById("channel-layer")
        mDateLayer = xulGetRenderContext().findItemById("date-layer")
        mProgramLayer = xulGetRenderContext().findItemById("program-layer")
        mCurrentProgramView = xulGetRenderContext().findItemById("current-program")

        mTimeshiftLogoView = xulGetRenderContext().findItemById("timeshift_logo")
        mMediaTimeStartView = xulGetRenderContext().findItemById("player-time-begin")
        mMediaTimeEndView = xulGetRenderContext().findItemById("player-time-end")
        mSeekBarRender = xulGetRenderContext().findItemById("player-pos").render as PlayerSeekBarRender
        mSeekBarRender.setOnProgressChangedListener(this)
        initSeekBar()

        mPlayerListener = object: Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        if (!mIsLiveMode) {
                            XulLog.i(NAME, "onAutoCompletion() 回看播放完成, 2秒后回到直播, liveId = $mCurrentChannelId ")
                            Toast.makeText(context, "回看播放完成,2秒后回到直播", Toast.LENGTH_SHORT).show()
                            xulDoAction(null, "switchChannel", "usr_cmd", "{\"live_id\":\"$mCurrentChannelId\",\"category_id\":\"$mCurrentCategoryId\"}", null)
                        }
                    }
                }
            }

            override fun onPlayerError(error: ExoPlaybackException?) {
                BuglyLog.e(NAME, "onPlayError.", error)
                CrashReport.postCatchedException(error)
                showPlayError()
            }

            override fun onSeekProcessed() {
                mIsPlaybackSeeking = false
                mIsLiveSeeking = false
            }
        }

        mAnalyticsListener = object :AnalyticsListener {
            override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime?, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
                xulGetRenderContext().findItemById("speed").setAttr("text", Utils.getBytesPerSecond(totalLoadTimeMs, totalBytesLoaded))
                xulGetRenderContext().findItemById("speed").resetRender()
            }
        }

        mMediaPlayer.addListener(mPlayerListener)
        mMediaPlayer.addAnalyticsListener(mAnalyticsListener)

        KeyEventListener.register(keys) {
            XulLog.d(NAME, "key pressed.")
            xulGetRenderContext().findItemById("debug_info").setStyle("display", if (mIsDebugInfoShow) "none" else "block")
            xulGetRenderContext().findItemById("debug_info").resetRender()
            mIsDebugInfoShow = !mIsDebugInfoShow
        }

        xulGetRenderContext().findItemById("version").setAttr("text", "版本号: ${Utils.getVersionName(context)}")
        xulGetRenderContext().findItemById("version").resetRender()
    }

    private fun initSeekBar() {
        if (mIsLiveMode) {
            mSeekBarRender.setSeekBarTips("直播中")
            mSeekBarRender.seekBarPos = 1.0
        } else {
            mSeekBarRender.seekBarPos = 0.0
            mMediaTimeStartView.setAttr(XulPropNameCache.TagId.TEXT, "00:00:00")
            mMediaTimeStartView.resetRender()
        }
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

                    mLiveDataNode = XulDataNode.buildFromJson(result)
                    val dataNode: XulDataNode? = mLiveDataNode

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
        mChannelListWrapper.asView?.findParentByType("layer")?.dynamicFocus = null
        XulSliderAreaWrapper.fromXulView(mChannelListWrapper.asView)?.scrollTo(0, false)

        xulGetRenderContext().findItemById("category_name_label").setAttr("text", categoryName)
        xulGetRenderContext().findItemById("category_name_label").resetRender()

        var categoryNode: XulDataNode? = mLiveDataNode?.getChildNode("data")?.firstChild
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

            var channelIndexInCurrentCategory = 0
            mChannelListWrapper.eachItem { idx, node ->
                val v: XulView? = mChannelListWrapper.getItemView(idx)
                val liveId: String = node.getAttributeValue("live_id")
                if (liveId == mCurrentChannelId) {
                    v?.findItemById("playing_indicator")?.setAttr("img.0.visible", "true")
                    v?.findItemById("playing_indicator")?.resetRender()
                    mChannelListWrapper.asView?.findParentByType("layer")?.dynamicFocus = v
                    channelIndexInCurrentCategory = idx
                } else {
                    v?.findItemById("playing_indicator")?.setAttr("img.0.visible", "false")
                    v?.findItemById("playing_indicator")?.resetRender()
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
                xulGetRenderContext().layout.requestFocus(mChannelListWrapper.getItemView(channelIndexInCurrentCategory))
                mUpDownSwitchChannelNodes.addAll(mUpDownTmpSwitchChannelNodes)
                if (mCurrentChannelId == "") {
                    mCurrentChannelId = channelList.firstChild.getAttributeValue("live_id")
                }
                val url: String = requestPlayUrl(mCurrentChannelId)
                startToPlayLive(url, UpOrDown.OTHER)
                mFirst = false
            }
        } else {
            showEmptyTips(true)
        }
    }

    private fun switchPlaybackProgram(programData: XulDataNode?, liveId: String?) {
        if (programData == null) {
//            showProgramEmpty(true)
            return
        }

        mProgramListWrapper.clear()
        mProgramListWrapper.asView?.findParentByType("layer")?.dynamicFocus = null
        XulSliderAreaWrapper.fromXulView(mProgramListWrapper.asView.parent)?.scrollTo(0, false)
        var program = programData.getChildNode("playbill_list").firstChild
        while (program != null) {
            val programTime: String = program.getAttributeValue("begin_time") + " - " + program.getAttributeValue("end_time")
            program.setAttribute("program_time", programTime)
            program.setAttribute("live_id", liveId)
            mProgramListWrapper.addItem(program)
            program = program.next
        }
        mProgramListWrapper.syncContentView()
    }

    private fun syncFocus() {
        if (mIsLiveMode) {
            mChannelListWrapper.eachItem { idx, node ->
                val v: XulView? = mChannelListWrapper.getItemView(idx)
                if (node.getAttributeValue("live_id") == mCurrentChannelId) {
                    v?.findItemById("playing_indicator")?.setAttr("img.0.visible", "true")
                    v?.findItemById("playing_indicator")?.resetRender()
                    mChannelListWrapper.asView?.findParentByType("layer")?.dynamicFocus = v
                    xulGetRenderContext().layout.requestFocus(v)
                } else {
                    v?.findItemById("playing_indicator")?.setAttr("img.0.visible", "false")
                    v?.findItemById("playing_indicator")?.resetRender()
                }
            }

            mProgramListWrapper.eachItem { idx, _ ->
                val v: XulView? = mProgramListWrapper.getItemView(idx)
                v?.findItemById("playing_indicator")?.setAttr("img.0.visible", "false")
                v?.findItemById("playing_indicator")?.resetRender()
            }
        }
    }

    private fun resetUI() {
        mSeekBarRender.seekBarPos = 1.0
        direction = 0
        mTimeshiftLogoView.setStyle(XulPropNameCache.TagId.DISPLAY, "none")
        mTimeshiftLogoView.resetRender()
    }

    private fun requestPlayUrl(channelId: String?): String {
        if (channelId == "" && !mFirst) {
            return ""
        }
        XulLog.i(NAME, "requestPlayUrl, liveId = $channelId ")
        mCurrentChannelId = channelId
        updateTitleArea(channelId!!, "")
        loadPreviewBitmaps()

        return mCurrentChannelNode?.getAttributeValue("play_url")?:""
    }

    private var mPreparedReadyTime: Long = 0
    private fun startToPlayLive(playUrl: String, upOrDown: UpOrDown) {
        XulLog.i(NAME, "startToPlayLive, playUrl = $playUrl,  upOrDown=$upOrDown, mCurrentChannelIndex=$mCurrentChannelIndex")
        if (mUpDownSwitchChannelNodes.size <=0) {
            XulLog.e(NAME, "channel list error, mUpDownSwitchChannelNodes.size = ${mUpDownSwitchChannelNodes.size}")
            showPlayError()
            return
        }
        //upOrDown -1 -> 按上键触发的播放
        //upOrDown =0 -> 非上下键触发的播放, 比如频道列表选择
        //upOrDown =1 -> 按下键触发的播放

        mIsLiveMode = true
        resetUI()

        // play current channel
        if (upOrDown == UpOrDown.OTHER) {
            mMediaPlayer.stop()
            val url = mUpDownSwitchChannelNodes[mCurrentChannelIndex].getAttributeValue("play_url")
            val videoSource: MediaSource = HlsMediaSource.Factory(mDataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(Uri.parse(url))
            mMediaPlayer.prepare(videoSource)
            mMediaPlayer.playWhenReady = true

            mUpMediaPlayer.stop()
            mUpMediaPlayer.release()
            mDownMediaPlayer.stop()
            mDownMediaPlayer.release()
        } else {
            mMediaPlayer.stop()
            mMediaPlayer.release()
            if (upOrDown == UpOrDown.UP) {
                mCurrentChannelIndex++
                mMediaPlayer = mUpMediaPlayer
                mDownMediaPlayer.stop()
                mDownMediaPlayer.release()
            } else {
                mCurrentChannelIndex--
                mMediaPlayer = mDownMediaPlayer
                mUpMediaPlayer.stop()
                mUpMediaPlayer.release()
            }
            mMediaPlayer.setVideoSurfaceHolder(mPlayerView.holder)
            mMediaPlayer.addListener(mPlayerListener)
            mMediaPlayer.addAnalyticsListener(mAnalyticsListener)
            mMediaPlayer.playWhenReady = true

            if (mCurrentChannelIndex < 0) mCurrentChannelIndex = mUpDownSwitchChannelNodes.size - 1
            if (mCurrentChannelIndex == mUpDownSwitchChannelNodes.size) mCurrentChannelIndex = 0

            // fix me: preload, but current time is changed, return to live.
            // 仅仅为了解决预加载时间小于当前时间, 重新播一下流可以到当前时间. 本应该直接seek到当前时间, 但是流的问题, seek会卡5秒, 特殊处理.
            // 如果预加载的时间和当前时间小于60秒, 忽略这个时间差(因为重新播, 预加载相当于没用了, 不如直接切台), 如果大于10秒, 重新播一下
            if (System.currentTimeMillis() - mPreparedReadyTime > (1000 * 60)) {
                mMediaPlayer.stop()
                val url = mUpDownSwitchChannelNodes[mCurrentChannelIndex].getAttributeValue("play_url")
                val videoSource: MediaSource = HlsMediaSource.Factory(mDataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(Uri.parse(url))
                mMediaPlayer.prepare(videoSource)
                mMediaPlayer.playWhenReady = true
            }
        }

        // preload up channel
        var upIndex = mCurrentChannelIndex + 1
        if (upIndex == mUpDownSwitchChannelNodes.size) upIndex = 0
        val upUrl = mUpDownSwitchChannelNodes[upIndex].getAttributeValue("play_url")
        val upVideoSource: MediaSource = HlsMediaSource.Factory(mDataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(Uri.parse(upUrl))
        mUpMediaPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)
        mUpMediaPlayer.prepare(upVideoSource)
        mUpMediaPlayer.playWhenReady = false

        // preload down channel
        var downIndex = mCurrentChannelIndex - 1
        if (downIndex < 0) downIndex = mUpDownSwitchChannelNodes.size - 1
        val downUrl = mUpDownSwitchChannelNodes[downIndex].getAttributeValue("play_url")
        val downVideoSource: MediaSource = HlsMediaSource.Factory(mDataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(Uri.parse(downUrl))
        mDownMediaPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector)
        mDownMediaPlayer.prepare(downVideoSource)
        mDownMediaPlayer.playWhenReady = false

        mPreparedReadyTime = System.currentTimeMillis()

        mCurrentChannelId = mUpDownSwitchChannelNodes[mCurrentChannelIndex].getAttributeValue("live_id")
        updateTitleArea(mCurrentChannelId!!, "")
        loadPreviewBitmaps()
    }

    private fun startToPlayPlayback(playUrl: String) {
        mIsLiveMode = false
        showControlFrame(true)
        mMediaPlayer.stop()
        XulLog.i(NAME, "play Playback!!!")
//        val testUrl = "http://7xjmzj.com1.z0.glb.clouddn.com/20171026175005_JObCxCE2.mp4"
//        val testUrl = "http://9890.vod.myqcloud.com/9890_4e292f9a3dd011e6b4078980237cc3d3.f20.mp4"

//        val videoSource: MediaSource = ProgressiveMediaSource.Factory(mDataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(Uri.parse(testUrl))
        val videoSource: MediaSource = HlsMediaSource.Factory(mDataSourceFactory).setAllowChunklessPreparation(true).createMediaSource(Uri.parse(playUrl))
        mMediaPlayer.prepare(videoSource)
        mMediaPlayer.playWhenReady = true
        initSeekBar()
    }

    private fun preloadProgram(channelNode: XulDataNode?) {
        if (channelNode == null) return

        val urlBuilder = HttpUrl.parse(Utils.HOST)!!.newBuilder()
            .addQueryParameter("m", "Live")
            .addQueryParameter("c", "LivePlayBill")
            .addQueryParameter("a", "getPlayBillList")
            .addQueryParameter("live_id", channelNode.getAttributeValue("live_id"))

        XulLog.i(NAME, "Request url: ${urlBuilder.build()}")

        val request: Request = Request.Builder().cacheControl(cacheControl).url(urlBuilder.build()).build()
        okHttpClient.newCall(request).enqueue(object : Callback{
            override fun onFailure(call: Call, e: IOException) {
            }

            override fun onResponse(call: Call, response: Response) {
            }

        })
    }

    private fun loadPreviewBitmaps() {
        val playUrl: String? = mCurrentChannelNode?.getAttributeValue("play_url")

//        for (i in 1..10) {
//            val time: Long = (i * mMediaPlayer.duration / 100).toLong()
            val time: Long = 20
            XulLog.i(NAME, "loadPreviewBitmaps time = $time")
            val width: Int = (xulGetRenderContext().xScalar * 250).toInt()
            val height: Int = (xulGetRenderContext().yScalar * 140).toInt()

//        object : AsyncTask<Void, Void, Bitmap>() {
//            override fun doInBackground(vararg p0: Void?): Bitmap? {
//                val mmr = FFmpegMediaMetadataRetriever()
//                mmr.setDataSource(playUrl, HashMap())
//                val b = mmr.getScaledFrameAtTime(2*1000*1000, FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
//                XulLog.e("kenshin", "bitmap = $b")
//                return b
//            }
//
//            override fun onPostExecute(result: Bitmap?) {
//                imageView?.setImageBitmap(result)
//            }
//        }.execute()

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

        mLiveDataNode?.getChildNode("data")?.appendChild(collectCategory)
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

    private fun updateTitleArea(channelId: String, playbackProgramName: String?) {
        mCurrentChannelNode = mLiveDataNode?.getChildNode("data")?.firstChild?.getChildNode("live_list")?.firstChild
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

        if (mIsLiveMode) {
            val urlBuilder = HttpUrl.parse(Utils.HOST)!!.newBuilder()
                .addQueryParameter("m", "Live")
                .addQueryParameter("c", "LivePlayBill")
                .addQueryParameter("a", "getPlayBillList")
                .addQueryParameter("live_id", mCurrentChannelId)

            XulLog.i(NAME, "Request url: ${urlBuilder.build()}")

            val request: Request = Request.Builder().cacheControl(cacheControl).url(urlBuilder.build()).build()
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call?, response: Response?) {
                    response!!.body().use { responseBody ->
                        if (!response.isSuccessful) {
                            XulLog.e(NAME, "getPlayBillList onResponse, but is not Successful")
                            throw IOException("Unexpected code $response")
                        }

                        XulLog.i(NAME, "getPlayBillList onResponse")

                        val result: String = responseBody!!.string()
                        val dataNode: XulDataNode? = XulDataNode.buildFromJson(result)

                        val code: String? = dataNode?.getAttributeValue("ret")

                        XulApplication.getAppInstance().postToMainLooper {
                            if (code != "0") {
                                mCurrentProgramView.setStyle("display", "block")
                                mCurrentProgramView.setAttr("text", "正在播放: ")
                                mCurrentProgramView.resetRender()
                                return@postToMainLooper
                            }

                            var programNode: XulDataNode? = dataNode?.getChildNode("data")?.lastChild?.getChildNode("playbill_list")?.firstChild
                            while (programNode != null) {
                                if (programNode.getAttributeValue("play_status") == "2") {
                                    mCurrentProgramView.setStyle("display", "block")
                                    mCurrentProgramView.setAttr("text", "正在播放: ${programNode.getAttributeValue("name")}")
                                    mCurrentProgramView.resetRender()
                                    break
                                }
                                programNode = programNode.next
                            }
                        }
                    }
                }

                override fun onFailure(call: Call?, e: IOException?) {
                    XulLog.e(NAME, "getPlayBillList onFailure")
                }
            })
        } else {
            mCurrentProgramView.setStyle("display", "block")
            mCurrentProgramView.setAttr("text", "正在播放: $playbackProgramName")
            mCurrentProgramView.resetRender()
        }
    }

    private fun showEmptyTips(show: Boolean) {
        val emptyView: XulView = xulGetRenderContext().findItemById("area_none_channel")
        emptyView.setStyle("display", if(show) "block" else "none")
        emptyView.resetRender()
    }

    private fun showPlaybackList(liveId: String, show: Boolean) {
        if (!show) {
            mCategoryLayer.setStyle("translate-x", "0")
            mChannelLayer.setStyle("translate-x", "0")
            mDateLayer.setStyle("display", "none")
            mDateLayer.setStyle("translate-x", "0")
            mProgramLayer.setStyle("display", "none")
            mProgramLayer.setStyle("translate-x", "0")

            mCategoryLayer.resetRender()
            mChannelLayer.resetRender()
            mDateLayer.resetRender()
            mProgramLayer.resetRender()
            return
        }

        mCategoryLayer.setStyle("translate-x", "-420")
        mChannelLayer.setStyle("translate-x", "-420")
        mDateLayer.setStyle("display", "block")
        mDateLayer.setStyle("translate-x", "-420")
        mProgramLayer.setStyle("display", "block")
        mProgramLayer.setStyle("translate-x", "-420")

        mCategoryLayer.resetRender()
        mChannelLayer.resetRender()
        mDateLayer.resetRender()
        mProgramLayer.resetRender()

        val urlBuilder = HttpUrl.parse(Utils.HOST)!!.newBuilder()
            .addQueryParameter("m", "Live")
            .addQueryParameter("c", "LivePlayBill")
            .addQueryParameter("a", "getPlayBillList")
            .addQueryParameter("live_id", liveId)

        XulLog.i(NAME, "Request url: ${urlBuilder.build()}")

        val request: Request = Request.Builder().cacheControl(cacheControl).url(urlBuilder.build()).build()
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call?, response: Response?) {
                response!!.body().use { responseBody ->
                    if (!response.isSuccessful) {
                        XulLog.e(NAME, "getPlayBillList onResponse, but is not Successful")
                        throw IOException("Unexpected code $response")
                    }

                    XulLog.i(NAME, "getPlayBillList onResponse")

                    val result : String = responseBody!!.string()
                    XulLog.json(NAME, result)

                    mPlaybackDataNode = XulDataNode.buildFromJson(result)
                    val dataNode: XulDataNode? = mPlaybackDataNode

                    if (handleError(dataNode)) {
//                        XulApplication.getAppInstance().postToMainLooper {
//                            showEmptyTips(true)
//                        }
                    } else {
                        XulApplication.getAppInstance().postToMainLooper {
                            if (dataNode?.getChildNode("data")?.size() == 0) {
//                                showEmptyTips(true)
                            } else {
                                mDateListWrapper.clear()
                                mDateListWrapper.asView?.findParentByType("layer")?.dynamicFocus = null
                                XulSliderAreaWrapper.fromXulView(mDateListWrapper.asView)?.scrollTo(0, false)
                                var programNode: XulDataNode? = dataNode?.getChildNode("data")?.firstChild
                                while (programNode != null) {
                                    mDateListWrapper.addItem(programNode)
                                    programNode = programNode.next
                                }

                                mDateListWrapper.syncContentView()

                                val todayView = mDateListWrapper.getItemView(mDateListWrapper.itemNum() - 1)
                                xulGetRenderContext().layout.requestFocus(todayView)
                                switchPlaybackProgram(todayView.getBindingData(0), liveId)
                            }
                        }
                    }
                }
            }

            override fun onFailure(call: Call?, e: IOException?) {
                XulLog.e(NAME, "getPlayBillList onFailure")
//                XulApplication.getAppInstance().postToMainLooper {
//
//                }
            }
        })
    }

    override fun xulOnBackPressed(): Boolean {
        if (mIsChannelListShow) {
            showChannelList(false)
            return true
        }
        if (mIsLiveMode) {
            val seekBarPos: Double = mSeekBarRender.seekBarPos
            if (seekBarPos < 1.0) {
                startToPlayLive("", UpOrDown.OTHER)
                showControlFrame(true)
                return true
            }
        } else {
            XulLog.i(NAME, "back key press in playback mode, back to live, liveId = $mCurrentChannelId")
            xulDoAction(null, "switchChannel", "usr_cmd", "{\"live_id\":\"$mCurrentChannelId\",\"category_id\":\"$mCurrentCategoryId\"}", null)
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

        mMediaPlayer.release()
        android.os.Process.killProcess(android.os.Process.myPid())
        return super.xulOnBackPressed()
    }

    override fun xulOnStop() {
        XulLog.i(NAME, "xulOnStop")
        mMediaPlayer.stop()
        mMediaPlayer.release()
        android.os.Process.killProcess(android.os.Process.myPid())
        super.xulOnStop()
    }

    @XulSubscriber(tag = CommonMessage.EVENT_FIVE_SECOND)
    private fun onFiveSecondPassed(dummy: Any) {
        XulLog.i(NAME, "5 seconds passed, dismiss operate tips.")
        val tipsView = xulGetRenderContext().findItemById("operate-tip")
        tipsView.setStyle("display", "none")
        tipsView.resetRender()
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss")
    private val currentDate = Date()
    private val liveDate = Date()
    private val timeshiftDate = Date()
    private val threeHoursAgoDate = Date()
    @XulSubscriber(tag = CommonMessage.EVENT_HALF_SECOND)
    private fun onHalfSecondPassed(dummy: Any) {
        if (mIsDebugInfoShow) {
            val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(context.resources)
            val trackName = trackNameProvider.getTrackName(mMediaPlayer.videoFormat)
            xulGetRenderContext().findItemById("bitrate").setAttr("text", trackName)
            xulGetRenderContext().findItemById("bitrate").resetRender()

            val clockLabel = xulGetRenderContext().findItemById("clock")
            val currentTimeMillis = System.currentTimeMillis()
            if (currentTimeMillis / 1000 != currentDate.time / 1000) {
                currentDate.time = currentTimeMillis
                dateFormat.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                val curTimeStr = dateFormat.format(currentDate)
                val clockTimeStr = clockLabel?.getAttrString(XulPropNameCache.TagId.TEXT)
                if (clockTimeStr != curTimeStr) {
                    clockLabel?.setAttr(XulPropNameCache.TagId.TEXT, curTimeStr)
                    clockLabel?.resetRender()
                }
            }
        }

        if (mIsLiveMode) {
            val currentTimeMillis = System.currentTimeMillis()
            if (currentTimeMillis / 1000 != liveDate.time / 1000) {
                liveDate.time = currentTimeMillis
                dateFormat.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                mMediaTimeEndView.setAttr("text", dateFormat.format(liveDate))
                mMediaTimeEndView.resetRender()

                threeHoursAgoDate.time = currentTimeMillis - THREE_HOURS_IN_SECONDS * 1000
                mMediaTimeStartView.setAttr("text", dateFormat.format(threeHoursAgoDate))
                mMediaTimeStartView.resetRender()

                if (!mIsLiveSeeking) {
                    timeshiftDate.time = currentTimeMillis - ((1.0f - mSeekBarRender.seekBarPos) * THREE_HOURS_IN_SECONDS * 1000).toLong()
                    mSeekBarRender.setSeekBarTips(if (mSeekBarRender.seekBarPos == 1.0) "直播中" else dateFormat.format(timeshiftDate))
                    showPlayerStateIndicator(PlayerIndicator.NORMAL)
                }
            }
        } else {
            if (!mIsPlaybackSeeking) {
                if (mMediaPlayer.currentPosition.toInt() != 0) {
                    mSeekBarRender.seekBarPos = (mMediaPlayer.currentPosition.toDouble() / mMediaPlayer.duration.toDouble())
                    mSeekBarRender.setSeekBarTips(dateFormat.format(mMediaPlayer.currentPosition - TimeZone.getDefault().rawOffset))
                }
                mMediaTimeStartView.setAttr("text", dateFormat.format(mMediaPlayer.currentPosition - TimeZone.getDefault().rawOffset))
                mMediaTimeStartView.resetRender()
                mMediaTimeEndView.setAttr("text", dateFormat.format(mMediaPlayer.duration - TimeZone.getDefault().rawOffset))
                mMediaTimeEndView.resetRender()
            }
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
                startToPlayLive(url, UpOrDown.OTHER)
                syncFocus()
            }
            "switchPlaybackProgram" -> {
                val programData = view?.bindingData?.get(0)
                switchPlaybackProgram(programData, programData?.getAttributeValue("live_id"))
            }
            "doPlayback" -> {
                showChannelList(false)
                val data = JSONObject(command)
                val playbackUrl: String = data.optString("play_url")
                val channelId: String = data.optString("live_id")
                val playbackName: String = data.optString("name")

                val indicators = xulGetRenderContext().findItemsByClass("playing_indicator")
                for (i in indicators) {
                    i.setAttr("img.0.visible", "false")
                    i.resetRender()
                }
                view?.findItemById("playing_indicator")?.setAttr("img.0.visible", "true")
                view?.findItemById("playing_indicator")?.resetRender()
                startToPlayPlayback(playbackUrl)
                updateTitleArea(channelId, playbackName)
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
    private var mIsPlaybackSeeking = false
    private var mIsLiveSeeking = false
    override fun xulOnDispatchKeyEvent(event: KeyEvent?): Boolean {
//        XulLog.i(NAME, "event = $event")
        if (event?.action == KeyEvent.ACTION_DOWN) {
            KeyEventListener.listenKeyInput(event.keyCode)
        }

        if (mHandler.hasMessages(CommonMessage.EVENT_AUTO_HIDE_UI)) {
            mHandler.removeMessages(CommonMessage.EVENT_AUTO_HIDE_UI)
            mHandler.sendEmptyMessageDelayed(CommonMessage.EVENT_AUTO_HIDE_UI, 8 * 1000)
        }

        if (event?.keyCode != KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
            xulGetRenderContext().findItemById("operate-tip").setStyle("display", "none")
            xulGetRenderContext().findItemById("operate-tip").resetRender()
        }

        if (mErrorDialog != null && mErrorDialog?.isShowing!!) {
            mErrorDialog?.dismiss()
        }

        if (event?.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!mIsChannelListShow && !mIsControlFrameShow) {
                        showControlFrame(true)
                        return true
                    }
                    if (mIsControlFrameShow) {
                        if (mIsLiveMode) {
                            if (!mIsLiveSeeking && mSeekBarRender.seekBarPos < 1.0) {
                                mIsLiveSeeking = true
                            }
                            val step = when (event.repeatCount) {
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
                                showPlayerStateIndicator(PlayerIndicator.REWIND)
                            } else {
                                if (direction == 0) {
                                    return true
                                }
                                if (direction + step > 0) {
                                    direction = 0
                                } else {
                                    direction += step
                                }
                                showPlayerStateIndicator(PlayerIndicator.FAST_FORWARD)
                            }
                            mSeekBarRender.seekBarPos = (THREE_HOURS_IN_SECONDS + direction) / THREE_HOURS_IN_SECONDS.toDouble()
                        } else {
                            if (!mIsPlaybackSeeking) {
                                direction = mMediaPlayer.currentPosition.toInt()
                                mIsPlaybackSeeking = true
                            }
                            val step = 3000
                            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                showPlayerStateIndicator(PlayerIndicator.REWIND)
                                direction -= step
                                if (direction < 0) {
                                    direction = 0
                                }
                            } else {
                                showPlayerStateIndicator(PlayerIndicator.FAST_FORWARD)
                                direction += step
                                if (direction > mMediaPlayer.duration) {
                                    direction = mMediaPlayer.duration.toInt()
                                }
                            }
                            if (mMediaPlayer.duration.toInt() != 0) {
                                mSeekBarRender.seekBarPos = direction / mMediaPlayer.duration.toDouble()
                                mSeekBarRender.setSeekBarTips(dateFormat.format(direction - TimeZone.getDefault().rawOffset))
                            }
                        }
                    }
                }
            }
        }
        if (event?.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (!mIsChannelListShow) {
                        if (!mIsLiveMode) {
                            if (mMediaPlayer.isPlaying) {
                                mMediaPlayer.playWhenReady = false
                                showPlayerStateIndicator(PlayerIndicator.PAUSE)
                            } else {
                                mMediaPlayer.playWhenReady = true
                                showPlayerStateIndicator(PlayerIndicator.NORMAL)
                            }
                        }
                        showControlFrame(true)
                        return true
                    }
                }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_1 -> {
                    if (!mIsChannelListShow) {
                        showControlFrame(false)
                        showChannelList(true)
                        return true
                    } else {
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
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (mIsControlFrameShow) {
                        if (mIsLiveMode) {
                            if (mIsLiveSeeking) {
                                showPlayerStateIndicator(PlayerIndicator.NORMAL)
                                val seekBarPos: Double = mSeekBarRender.seekBarPos
                                val duration: Int = mMediaPlayer.duration.toInt()
                                var seekPos: Long = (seekBarPos * duration).toLong()
                                if (seekPos < 0) {
                                    seekPos = 3000
                                }
                                mTimeshiftLogoView.setStyle("display", "block")
                                if (seekPos >= duration) {
                                    seekPos = duration.toLong()
                                    mTimeshiftLogoView.setStyle("display", "none")
                                }
                                mTimeshiftLogoView.resetRender()
                                XulLog.i(NAME, "seekBarPos = $seekBarPos, duration = $duration. seekBarPos * duration = $seekPos"
                                )
                                mMediaPlayer.seekTo(seekPos)
                                mIsLiveSeeking = false
                            }
                        } else {
                            if (mIsPlaybackSeeking) {
                                showPlayerStateIndicator(PlayerIndicator.NORMAL)
                                mMediaPlayer.seekTo(direction.toLong())
                            }
                        }
                        return true
                    }

                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (super.xulOnDispatchKeyEvent(event)) {
                            return true
                        }
                        val channelView: XulView = xulGetFocus()
                        val liveId: String = channelView.getDataString("live_id")
                        if (TextUtils.isEmpty(liveId)) {
                            XulLog.d(NAME, "right on not channel")
                            return true
                        }
                        XulLog.i(NAME, "right on channel, show playback list.")
                        mChannelListWrapper.eachItem { idx, _ ->
                            val v: XulView? = mChannelListWrapper.getItemView(idx)
                            v?.removeClass("category_checked")
                            v?.resetRender()
                        }
                        channelView.addClass("category_checked")
                        channelView.resetRender()
                        showPlaybackList(liveId, true)
                        return true
                    }

                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        val channelView: XulView = xulGetFocus()
                        val liveId: String = channelView.getDataString("live_id")
                        if (TextUtils.isEmpty(liveId)) {
                            XulLog.d(NAME, "left on not channel")
                            return true
                        }
                        XulLog.i(NAME, "left on channel, hide playback list.")
                        channelView.removeClass("category_checked")
                        channelView.resetRender()
                        showPlaybackList(liveId, false)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!mIsChannelListShow && mIsLiveMode) {
                        XulLog.i(NAME, "up pressed in live mode.")
                        showControlFrame(true)
                        showPlayerStateIndicator(PlayerIndicator.NORMAL)
                        startToPlayLive("", UpOrDown.UP)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!mIsChannelListShow && mIsLiveMode) {
                        XulLog.i(NAME, "down pressed in live mode.")
                        showControlFrame(true)
                        showPlayerStateIndicator(PlayerIndicator.NORMAL)
                        startToPlayLive("", UpOrDown.DOWN)
                        return true
                    }
                }
            }
        }

        return super.xulOnDispatchKeyEvent(event)
    }

    override fun onProgressChanged(pos: Double) {
        XulLog.i(NAME, "onProgressChanged.  pos = $pos, duration = ${mMediaPlayer.duration} time = ${(mMediaPlayer.duration * pos).toInt()})")
        if (mIsLiveMode) {
            val currentTimeMillis = System.currentTimeMillis()
            timeshiftDate.time = currentTimeMillis - ((1.0f - mSeekBarRender.seekBarPos) * THREE_HOURS_IN_SECONDS * 1000).toLong()
            mSeekBarRender.setSeekBarTips(if (mSeekBarRender.seekBarPos == 1.0) "直播中" else dateFormat.format(timeshiftDate))
        }
    }

    private fun showChannelList(show: Boolean) {
        if (show) {
            if (mIsLiveMode) {
                showPlaybackList("", false)
                mChannelListWrapper.eachItem { idx, _ ->
                    val v: XulView? = mChannelListWrapper.getItemView(idx)
                    v?.removeClass("category_checked")
                    v?.resetRender()
                }
            }
            syncFocus()
            mHandler.sendEmptyMessageDelayed(CommonMessage.EVENT_AUTO_HIDE_UI, 8 * 1000)
        }
        mChannelArea.setAttr("x", if(show) "0" else "-1870")
        mChannelArea.resetRender()
        mIsChannelListShow = show

        mTitleArea.setStyle("display", "none")
        mTitleArea.resetRender()
        mControlArea.setAttr("y", "1080")
        mControlArea.resetRender()
    }

    private fun showControlFrame(show: Boolean) {
        if (show) {
            if (mIsLiveMode) {
                val seekBarPos: Double = mSeekBarRender.seekBarPos
                if (seekBarPos < 1.0) {
                    mTimeshiftLogoView.setStyle("display", "block")
                    mTimeshiftLogoView.setAttr("text", "时移")
                    mTimeshiftLogoView.resetRender()
                } else {
                    mTimeshiftLogoView.setStyle("display", "none")
                    mTimeshiftLogoView.resetRender()
                }

                mHandler.sendEmptyMessageDelayed(CommonMessage.EVENT_AUTO_HIDE_UI, 8 * 1000)
            } else {
                mTimeshiftLogoView.setStyle("display", "block")
                mTimeshiftLogoView.setAttr("text", "回看")
                mTimeshiftLogoView.resetRender()

                if (!mMediaPlayer.isPlaying) {
                    mHandler.sendEmptyMessageDelayed(CommonMessage.EVENT_AUTO_HIDE_UI, 8 * 1000)
                }
            }
        }
        mTitleArea.setStyle("display", if(show) "block" else "none")
        mTitleArea.resetRender()
        mControlArea.setAttr("y", if(show) "0" else "1080")
        mControlArea.resetRender()
        mIsControlFrameShow = show

        mChannelArea.setAttr("x", "-1870")
        mChannelArea.resetRender()
    }

    private fun showPlayerStateIndicator(direction: PlayerIndicator) {
        val playerState: XulView = xulGetRenderContext().findItemById("player-state")
        when (direction) {
            PlayerIndicator.REWIND -> {
                changePlayerStates(playerState,"false","false","true")
            }
            PlayerIndicator.FAST_FORWARD -> {
                changePlayerStates(playerState,"false","true","false")
            }
            PlayerIndicator.PAUSE -> {
                changePlayerStates(playerState,"true","false","false")
            }
            PlayerIndicator.NORMAL -> {
                changePlayerStates(playerState,"false","false","false")
            }
        }
    }

    private fun changePlayerStates(playerState: XulView, img1: String,img2:String, img3:String) {
        playerState.setAttr("img.1.visible", img1) // pause
        playerState.setAttr("img.2.visible", img2) // forward
        playerState.setAttr("img.3.visible", img3) // rewind
        playerState.resetRender()
    }

    private fun showPlayError() {
        val builder = AlertDialog.Builder(context)
        val inflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.dialog_error, null)

        mErrorDialog = builder.create()
        mErrorDialog?.show()
        mErrorDialog?.window?.setContentView(view)
        mErrorDialog?.setOnKeyListener { dialogInterface, keyCode, keyEvent ->
            if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        dialogInterface.dismiss()
                        return@setOnKeyListener false
                    }
                }
            }

            return@setOnKeyListener false
        }
    }

    private fun showAdaptationError() {
        val dialog = AlertDialog.Builder(context).setTitle("温馨提示").setMessage("当前版本未适配此设备,请安装对应适配版本.").setCancelable(false).create()
        dialog.setButton(Dialog.BUTTON_NEGATIVE, "确定") { d, _ ->
            d?.dismiss()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        dialog.show()
    }

    enum class UpOrDown{
        UP, DOWN,
        OTHER // 非上下键触发的播放, 比如频道列表选择
    }

    enum class PlayerIndicator{
        REWIND, FAST_FORWARD, PAUSE, NORMAL
    }
}