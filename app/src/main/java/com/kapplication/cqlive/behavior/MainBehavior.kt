package com.kapplication.cqlive.behavior

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.kapplication.cqlive.message.CommonMessage
import com.kapplication.cqlive.utils.Utils
import com.kapplication.cqlive.widget.NoUiGSYPlayer
import com.kapplication.cqlive.widget.XulExt_GSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.starcor.xul.IXulExternalView
import com.starcor.xul.Wrapper.XulMassiveAreaWrapper
import com.starcor.xul.Wrapper.XulSliderAreaWrapper
import com.starcor.xul.XulDataNode
import com.starcor.xul.XulView
import com.starcor.xulapp.XulApplication
import com.starcor.xulapp.XulPresenter
import com.starcor.xulapp.behavior.XulBehaviorManager
import com.starcor.xulapp.behavior.XulUiBehavior
import com.starcor.xulapp.message.XulSubscriber
import com.starcor.xulapp.utils.XulLog
import okhttp3.*
import java.io.IOException

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
    private var mChannelNode: XulDataNode? = null
    private var mCurrentChannelID: String? = "429535885"
    private var mFirst: Boolean = true

    override fun xulOnRenderIsReady() {
        XulLog.i("CQLive", "xulOnRenderIsReady")
        initView()
        requestChannel()
//        requestPlayUrl("")
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
                                while (categoryNode != null) {
                                    mCategoryListWrapper?.addItem(categoryNode)
                                    categoryNode = categoryNode.next
                                }

                                mCategoryListWrapper?.syncContentView()

                                xulGetRenderContext().layout.requestFocus(mCategoryListWrapper?.getItemView(0))
                                mCategoryListWrapper?.getItemView(0)?.resetRender()
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

    private fun switchCategory(categoryID: String?) {
        if (categoryID.isNullOrEmpty()) {
            return
        }
        mChannelListWrapper?.clear()
        mChannelListWrapper?.asView?.parent?.dynamicFocus = null
        XulSliderAreaWrapper.fromXulView(mChannelListWrapper?.asView)?.scrollTo(0, false)

        var categoryNode: XulDataNode? = mChannelNode?.getChildNode("data")?.firstChild
        var channelList: XulDataNode? = null
        while (categoryNode != null) {
            val id: String? = categoryNode.getAttributeValue("category_id")
            if (id == categoryID) {
                channelList = categoryNode.getChildNode("live_list")
                break
            }

            categoryNode = categoryNode.next
        }

        if (channelList != null && channelList.size() > 0) {
            showEmptyTips(false)
            var channelNode: XulDataNode? = channelList.firstChild
            while (channelNode != null) {
                mChannelListWrapper?.addItem(channelNode)
                channelNode = channelNode.next
            }

            mChannelListWrapper?.syncContentView()

            var index = 0
            mChannelListWrapper?.eachItem { idx, node ->
                val v: XulView? = mChannelListWrapper?.getItemView(idx)
                if (node.getAttributeValue("live_id") == mCurrentChannelID) {
                    v?.addClass("category_checked")
                    mChannelListWrapper?.asView?.parent?.dynamicFocus = v
                    index = idx
                } else {
                    v?.removeClass("category_checked")
                }
                v?.resetRender()
            }

            if (mFirst) {
                xulGetRenderContext().layout.requestFocus(mChannelListWrapper?.getItemView(index))
                requestPlayUrl(mCurrentChannelID)
                mFirst = false
            }
        } else {
            showEmptyTips(true)
        }
    }

    private fun requestPlayUrl(channelID: String?) {
        mCurrentChannelID = channelID
        mMediaPlayer.setUp("http://129.28.160.49/__cl/cg:ingest01/__c/cctv5/__op/default/__f/index.m3u8", true, "name")
        mMediaPlayer.startPlayLogic()
    }

    private fun syncCheckedChannel() {
    }
    
    private fun showEmptyTips(show: Boolean) {
        val emptyView: XulView = xulGetRenderContext().findItemById("area_none_channel")
        emptyView.setStyle("display", if(show) "block" else "none")
        emptyView.resetRender()
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

    @XulSubscriber(tag = CommonMessage.EVENT_HALF_SECOND)
    private fun onHalfSecondPassed(dummy: Any) {
    }

    @XulSubscriber(tag = CommonMessage.EVENT_HALF_HOUR)
    private fun onHalfHourPassed(dummy: Any) {
    }

    override fun xulDoAction(view: XulView?, action: String?, type: String?, command: String?, userdata: Any?) {
        XulLog.i(NAME, "action = $action, type = $type, command = $command, userdata = $userdata")
        when (command) {
            "switchCategory" -> switchCategory(userdata as String)
            "switchChannel" -> requestPlayUrl(userdata as String)
            "syncCheckedChannel" -> syncCheckedChannel()
        }
        super.xulDoAction(view, action, type, command, userdata)
    }

    override fun xulOnDispatchKeyEvent(event: KeyEvent?): Boolean {
        return super.xulOnDispatchKeyEvent(event)
    }
}