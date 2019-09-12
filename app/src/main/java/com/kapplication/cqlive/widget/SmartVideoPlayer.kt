package com.kapplication.cqlive.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer

class SmartVideoPlayer: NoUiGSYPlayer {
    constructor(context: Context, fullFlag: Boolean?) : super(context, fullFlag!!)

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var mUrlList: List<String>? = null
    
    private var mTempManager: GSYVideoManager? = null

    private var mSourcePosition = 0
    private var mPreSourcePosition = 0

    public fun setUp(urls: List<String>) : Boolean {
        mUrlList = urls
        return setUp(urls[mSourcePosition], false, "")
    }

    override fun onAutoCompletion() {
        super.onAutoCompletion()
        releaseTempManager()
    }

    override fun onCompletion() {
        super.onCompletion()
        releaseTempManager()
    }
    
    private fun resolveChangeUrl(url: String) {
        if (mTempManager != null) {
            mOriginUrl = url
            mUrl = url
        }
    }

    public fun resolveStartChange(position: Int) {
        if (mSourcePosition != position) {
            if ((mCurrentState == GSYVideoPlayer.CURRENT_STATE_PLAYING || mCurrentState == GSYVideoPlayer.CURRENT_STATE_PAUSE)) {
                val url = mUrlList?.get(position)
                mPreSourcePosition = mSourcePosition
                mSourcePosition = position
                 mTempManager = GSYVideoManager.tmpInstance(mediaPlayerListener)
                if (url != null) {
                    resolveChangeUrl(url)
                }
                 mTempManager?.prepare(mUrl, null, false, 1.0f, false, null)
            }
        }
    }

    private fun resolveChangedResult() {
        mTempManager = null
        val url = mUrlList?.get(mSourcePosition)
        resolveChangeUrl(url!!)
    }

    private fun releaseTempManager() {
        mTempManager?.releaseMediaPlayer()
    }

    private val mediaPlayerListener = object : GSYMediaPlayerListener {
        override fun onPrepared() {
            mTempManager?.start()
            GSYVideoManager.instance().releaseMediaPlayer()
            GSYVideoManager.changeManager(mTempManager)
            mTempManager?.setLastListener(this)
            mTempManager?.setListener(this)
            mTempManager?.setDisplay(mSurface)
            resolveChangedResult()
        }

        override fun onAutoCompletion() {

        }

        override fun onCompletion() {

        }

        override fun onBufferingUpdate(percent: Int) {

        }

        override fun onSeekComplete() {
        }

        override fun onError(what: Int, extra: Int) {
            mSourcePosition = mPreSourcePosition
            mTempManager?.releaseMediaPlayer()
            post {
                resolveChangedResult()
                Toast.makeText(mContext, "change Fail", Toast.LENGTH_LONG).show()
            }
        }

        override fun onInfo(what: Int, extra: Int) {

        }

        override fun onVideoSizeChanged() {

        }

        override fun onBackFullscreen() {

        }

        override fun onVideoPause() {

        }

        override fun onVideoResume() {

        }

        override fun onVideoResume(seek: Boolean) {

        }
    }
}