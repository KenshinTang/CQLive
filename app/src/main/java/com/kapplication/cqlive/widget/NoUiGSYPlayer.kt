package com.kapplication.cqlive.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Surface
import com.kapplication.cqlive.R
import com.kapplication.cqlive.behavior.MainBehavior

import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.starcor.xulapp.utils.XulLog

/**
 * 无任何控制ui的播放
 * Created by guoshuyu on 2017/8/6.
 */

open class NoUiGSYPlayer : StandardGSYVideoPlayer {

    constructor(context: Context, fullFlag: Boolean?) : super(context, fullFlag!!)

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun getLayoutId(): Int {
        return R.layout.empty_control_video
    }

    override fun touchSurfaceMoveFullLogic(absDeltaX: Float, absDeltaY: Float) {
        super.touchSurfaceMoveFullLogic(absDeltaX, absDeltaY)
        //不给触摸快进，如果需要，屏蔽下方代码即可
        mChangePosition = false

        //不给触摸音量，如果需要，屏蔽下方代码即可
        mChangeVolume = false

        //不给触摸亮度，如果需要，屏蔽下方代码即可
        mBrightness = false
    }

    override fun touchDoubleUp() {
        //super.touchDoubleUp();
        //不需要双击暂停
    }

    override fun seekTo(position: Long) {
        gsyVideoManager.seekTo(position)
    }

    fun getSurface() : Surface {
        return mSurface
    }


    private val TAG: String = "TAG"
    override fun onAutoCompletion() {
        XulLog.i(MainBehavior.NAME, "$TAG onAutoCompletion")
        super.onAutoCompletion()
    }

    override fun onPrepared() {
        XulLog.i(MainBehavior.NAME, "$TAG onPrepared")
        super.onPrepared()
    }

    override fun onCompletion() {
        XulLog.i(MainBehavior.NAME, "$TAG onCompletion")
        super.onCompletion()
    }

    override fun onVideoPause() {
        XulLog.i(MainBehavior.NAME, "$TAG onVideoPause")
        super.onVideoPause()
    }

    override fun onSeekComplete() {
        XulLog.i(MainBehavior.NAME, "$TAG onSeekComplete")
        super.onSeekComplete()
    }

    override fun onInfo(what: Int, extra: Int) {
        XulLog.i(MainBehavior.NAME, "$TAG onInfo(what:$what, extra:$extra)")
        super.onInfo(what, extra)
    }

    override fun onVideoSizeChanged() {
        XulLog.i(MainBehavior.NAME, "$TAG onVideoSizeChanged")
        super.onVideoSizeChanged()
    }

    override fun onBufferingUpdate(percent: Int) {
        XulLog.i(MainBehavior.NAME, "$TAG onBufferingUpdate(percent:$percent)")
        super.onBufferingUpdate(percent)
    }

    override fun onBackFullscreen() {
        XulLog.i(MainBehavior.NAME, "$TAG onBackFullscreen")
        super.onBackFullscreen()
    }

    override fun onError(what: Int, extra: Int) {
        XulLog.i(MainBehavior.NAME, "$TAG onError(what:$what, extra:$extra)")
        super.onError(what, extra)
    }

    override fun onVideoResume() {
        XulLog.i(MainBehavior.NAME, "$TAG onVideoResume")
        super.onVideoResume()
    }

    override fun onVideoResume(seek: Boolean) {
        XulLog.i(MainBehavior.NAME, "$TAG onVideoResume(seek:$seek)")
        super.onVideoResume(seek)
    }
}
