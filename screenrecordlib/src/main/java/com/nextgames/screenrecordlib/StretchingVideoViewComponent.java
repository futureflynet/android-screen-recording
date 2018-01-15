package com.nextgames.screenrecordlib;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.VideoView;

/**
 * Created by gimulnautti on 15/01/2018.
 */

public class StretchingVideoViewComponent extends VideoView {

    private int mVideoWidth;
    private int mVideoHeight;


    public StretchingVideoViewComponent(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StretchingVideoViewComponent(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public StretchingVideoViewComponent(Context context) {
        super(context);
    }

    @Override
    public void setVideoURI(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this.getContext(), uri);
        mVideoWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        mVideoHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        super.setVideoURI(uri);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            int width = this.getDefaultSize(mVideoWidth, widthMeasureSpec);
            int height = this.getDefaultSize(mVideoHeight, heightMeasureSpec);
            Log.d("VideoViewComponent", "Defaults width "+width+" height "+height);
            if (mVideoWidth > 0 && mVideoHeight > 0) {
                if (mVideoWidth * height > width * mVideoHeight) {
                    height = width * mVideoHeight / mVideoWidth;
                } else if (mVideoWidth * height < width * mVideoHeight) {
                    width = height * mVideoWidth / mVideoHeight;
                } else {}
            }
            Log.d("VideoViewComponent", "Cropped width "+width+" height "+height);
            setMeasuredDimension(width, height);
        } catch (Exception e) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
