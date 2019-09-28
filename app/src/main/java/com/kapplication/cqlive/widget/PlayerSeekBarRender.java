package com.kapplication.cqlive.widget;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;

import com.kapplication.cqlive.KApplication;
import com.starcor.xul.Graphics.BitmapTools;
import com.starcor.xul.Graphics.XulDC;
import com.starcor.xul.Graphics.XulDrawable;
import com.starcor.xul.Render.Drawer.XulDrawer;
import com.starcor.xul.Render.XulImageRender;
import com.starcor.xul.Render.XulRenderFactory;
import com.starcor.xul.Render.XulViewRender;
import com.starcor.xul.XulItem;
import com.starcor.xul.XulManager;
import com.starcor.xul.XulRenderContext;
import com.starcor.xul.XulUtils;
import com.starcor.xul.XulView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by hy on 2015/11/19.
 */
public class PlayerSeekBarRender extends XulImageRender {
    public PlayerSeekBarRender(XulRenderContext ctx, XulView view) {
        super(ctx, (XulItem) view);
    }

    public interface OnProgressChangedListener {
        void onProgressChanged(double pos);
    }

    OnProgressChangedListener listener;
    Paint.FontMetrics _fontMetrics;
    int _textWidth;
    int _textHeight;
    Bitmap seekTipBg;
    Bitmap previewImg;
    int seekBgWidth;
    int seekBgHeight;
    int previewWidth;
    int previewHeight;
    int progressWidth;
    int tipOffset;
    int previewOffset;
    boolean showPreview = false;
    String seekMode = "";

    public static void register() {
        XulRenderFactory.registerBuilder("item", "seek_bar", new XulRenderFactory.RenderBuilder() {
            @Override
            protected XulViewRender createRender(XulRenderContext ctx, XulView view) {
                assert view instanceof XulItem;
                return new PlayerSeekBarRender(ctx, view);
            }
        });
    }

    double _seekBarPos = 0.0f;

    String _seekTips;

    @Override
    public void syncData() {
        super.syncData();
        seekBgWidth = (int) (150 * getXScalar());
        seekBgHeight = (int) (69 * getYScalar());
        previewWidth = (int) (XulUtils.tryParseInt(_view.getAttrString("preview.width")) * getXScalar());
        previewHeight = (int) (XulUtils.tryParseInt(_view.getAttrString("preview.height")) * getYScalar());
        progressWidth = (int) (XulUtils.tryParseInt(_view.getAttrString("img.4.width")) * getXScalar());
        tipOffset = (int) (XulUtils.tryParseInt(_view.getAttrString("tip.y")) * getXScalar());
        previewOffset = (int) (XulUtils.tryParseInt(_view.getAttrString("preview.y")) * getXScalar());
        seekMode = _view.getAttrString("seek-mode");
    }

    @Override
    protected void syncTextInfo(boolean recalAutoWrap) {
        super.syncTextInfo(recalAutoWrap);
    }

    @Override
    public void draw(XulDC dc, Rect rect, int xBase, int yBase) {
        super.draw(dc, rect, xBase, yBase);
        if (!TextUtils.isEmpty(_seekTips)) {
            Paint textPaint = getTextPaint();
            Rect padding = getPadding();
            RectF animRect = getAnimRect();
            int xOffset;
            if ("playback".equals(seekMode) || "live".equals(seekMode)) {
                if (showPreview) {
                    if (previewImg == null) {
                        previewImg = getImageFromAssetsFile("images/test.png");
                    }
                    int previewXOffset = (int) (padding.left + (animRect.width() - padding.left - padding.right - progressWidth) * _seekBarPos - (previewWidth - progressWidth) / 2);

                    // test border
                    Paint borderPaint = new Paint();
                    borderPaint.setColor(Color.RED);
                    dc.drawRect(animRect.left + previewXOffset + xBase + _screenX,
                            animRect.top + yBase + _screenY + previewOffset,
                            animRect.left + previewXOffset + xBase + _screenX + previewWidth,
                            animRect.top + yBase + _screenY + previewOffset + previewHeight, borderPaint);

                    // preview img
                    RectF des = new RectF(animRect.left + previewXOffset + xBase + _screenX,
                            animRect.top + yBase + _screenY + previewOffset,
                            animRect.left + previewXOffset + xBase + _screenX + previewWidth,
                            animRect.top + yBase + _screenY + previewOffset + previewHeight);
//                    dc.drawBitmap(previewImg, animRect.left + previewXOffset + xBase + _screenX, animRect.top + yBase + _screenY + previewOffset, null);
                    dc.drawBitmap(XulDrawable.fromBitmap(previewImg, "", ""), des, null);

                    // text, time.
                    xOffset = (int) (padding.left + (animRect.width() - padding.left - padding.right - progressWidth) * _seekBarPos - (_textWidth - progressWidth) / 2);
                    dc.drawText(_seekTips, 0, _seekTips.length(), animRect.left + xOffset + xBase + _screenX, animRect.top + yBase + _screenY + previewOffset + previewHeight - 5, textPaint);
                } else {
                    if (seekTipBg == null) {
                        seekTipBg = getImageFromAssetsFile("images/img_seek_tip_bg.png");
                    }
                    int bgXOffset = (int) (padding.left + (animRect.width() - padding.left - padding.right - progressWidth) * _seekBarPos - (seekBgWidth - progressWidth) / 2);
                    dc.drawBitmap(seekTipBg, animRect.left + bgXOffset + xBase + _screenX, animRect.top + yBase + _screenY, null);
                    xOffset = (int) (padding.left + (animRect.width() - padding.left - padding.right - progressWidth) * _seekBarPos - (_textWidth - progressWidth) / 2);
                    dc.drawText(_seekTips, 0, _seekTips.length(), animRect.left + xOffset + xBase + _screenX, animRect.top + yBase + _screenY - _fontMetrics.top + tipOffset, textPaint);
                }
            } else {
                xOffset = (int) (padding.left + (animRect.width() - padding.left - padding.right - _textWidth) * _seekBarPos);
                dc.drawText(_seekTips, 0, _seekTips.length(), animRect.left + xOffset + xBase + _screenX, animRect.top + yBase + _screenY - _fontMetrics.top, textPaint);
            }

        }
    }

    @Override
    protected void drawImage(XulDC dc, Paint paint, DrawableInfo imgInfo, XulDrawable bmp, XulDrawer drawer, int xBase, int yBase) {
        int idx = imgInfo.getIdx();
        if (idx == 4) {
            imgInfo.setAlignX((float)_seekBarPos);
        } else if (idx == 3) {
            RectF dstRc = getAnimRect();
            float scalarY = _scalarY;
            float scalarX = _scalarX;

            int paddingLeft = XulUtils.roundToInt(imgInfo.getPaddingLeft() * scalarX);
            int paddingRight = XulUtils.roundToInt(imgInfo.getPaddingRight() * scalarX);
            int paddingTop = XulUtils.roundToInt(imgInfo.getPaddingTop() * scalarY);
            int paddingBottom = XulUtils.roundToInt(imgInfo.getPaddingBottom() * scalarY);
            dstRc.left += paddingLeft;
            dstRc.top += paddingTop;
            dstRc.right -= paddingRight;
            dstRc.bottom -= paddingBottom;
            XulUtils.offsetRect(dstRc, xBase, yBase);
            dstRc.right = (float) (dstRc.right * _seekBarPos + dstRc.left * (1.0f - _seekBarPos));

            dc.save();
            dc.clipRect(dstRc);
            super.drawImage(dc, paint, imgInfo, bmp, drawer, xBase, yBase);
            dc.restore();
            return;
        }
        super.drawImage(dc, paint, imgInfo, bmp, drawer, xBase, yBase);
    }

    public void setSeekBarPos(double percent) {
        _seekBarPos = Math.max(0, Math.min(percent, 1.0f));
        markDirtyView();
        if (listener != null) {
            listener.onProgressChanged(percent);
        }
    }

    public double getSeekBarPos() {
        return _seekBarPos;
    }

    private int _tipLength;

    public void setSeekBarTips(String tips) {
        _seekTips = tips;
        if (_tipLength != tips.length()) {
            Paint textPaint = getTextPaint();
            _fontMetrics = textPaint.getFontMetrics();
            Rect rect = new Rect();
            textPaint.getTextBounds("00:00:00", 0, 8, rect);
            String mTips = "直播中";
            if (TextUtils.equals(mTips, _seekTips)) {
                textPaint.getTextBounds(mTips, 0, 3, rect);
            }
            _textWidth = rect.width();
            _textHeight = rect.height();
            _tipLength = tips.length();
        }
        markDirtyView();
    }

    private Bitmap getImageFromAssetsFile(String fileName) {
        Bitmap image = null;
        AssetManager am = KApplication.getAppInstance().getResources().getAssets();
        try {
            InputStream is = am.open(fileName);
            image = BitmapTools.decodeStream(is, XulManager.DEF_PIXEL_FMT, seekBgWidth, seekBgHeight);
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return image;
    }

    public void setShowPreview(boolean show) {
        showPreview = show;
    }

    public boolean getShowPreview() {
        return showPreview;
    }

    public void setOnProgressChangedListener(OnProgressChangedListener listener) {
        this.listener = listener;
    }
}
