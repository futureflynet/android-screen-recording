package com.nextgames.screenrecordlib;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.IOException;

import static android.app.Activity.RESULT_OK;

/**
 * Created by gimulnautti on 08/01/2018.
 */

public class ScreenRecorder {

    public static final int REQUEST_CODE = 1000;

    private static final String TAG = "ScreenRecorder";
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private static final int DISPLAY_WIDTH = 720;
    private static final int DISPLAY_HEIGHT = 1280;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_PERMISSIONS = 10;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static Activity sourceActivity;
    private static boolean isRecording;
    private static String outputFileName;
    private static ScreenRecorder sInstance;

    public ScreenRecorder(Activity a) {
        sourceActivity = a;
        sInstance = this;
    }

    //// Public static methods for unity interfacing

    public static void RequestPermissions() {
        if (ContextCompat.checkSelfPermission(sourceActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat
                .checkSelfPermission(sourceActivity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale
                    (sourceActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale
                            (sourceActivity, Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(sourceActivity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSIONS);
            } else {
                ActivityCompat.requestPermissions(sourceActivity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSIONS);
            }
        } else {
            // All permissions have been granted
        }
    }

    public static void StartMediaRecording() {
        sInstance.setup();
        sInstance.initRecorder();
        sInstance.shareScreen();
    }

    public static void StopMediaRecording() {
        Log.d(TAG, "Stopping media recording");
        sInstance.mMediaRecorder.stop();
        sInstance.mMediaRecorder.reset();
        sInstance.stopScreenSharing();
    }

    public static void SetOutputFileName(String f)
    {
        outputFileName = f;
    }

    public static boolean IsRecording() {
        return isRecording;
    }

    public static String GetVideoFileName() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                + "/" + outputFileName;
    }

    public static void PlayVideoWithIntent()
    {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GetVideoFileName()));
        intent.setDataAndType(Uri.parse(GetVideoFileName()), "video/mp4");
        sourceActivity.startActivity(intent);
    }

    public static void ShareVideo()
    {
        sInstance.shareVideo(sInstance.outputFileName, GetVideoFileName());
    }
    //// Android glue

    /**
     * Needs to be called from the requesting activity!
     * @param requestCode
     * @param resultCode
     * @param data
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Log.e(TAG, "Screen cast permission denied! " + resultCode);
            return;
        }
        Log.d(TAG, "Starting media recording");
        mMediaProjectionCallback = new MediaProjectionCallback();
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
        isRecording = true;
    }

    //// Internal methods

    private void shareVideo(final String title, String path) {

        MediaScannerConnection.scanFile(sourceActivity, new String[] { path },
                null, new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Intent shareIntent = new Intent(
                                android.content.Intent.ACTION_SEND);
                        shareIntent.setType("video/*");
                        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
                        shareIntent.putExtra(android.content.Intent.EXTRA_TITLE, title);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                        sourceActivity.getApplicationContext().startActivity(Intent.createChooser(shareIntent,
                                "Share Video"));
                    }
                });
    }

    private void setup()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        sourceActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        mMediaRecorder = new MediaRecorder();

        mProjectionManager = (MediaProjectionManager)
                sourceActivity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("ScreenRecorderUnityPlayerActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null
                /*Handler*/);
    }

    private void shareScreen() {
        if (mMediaProjection == null) {
            sourceActivity.startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
        isRecording = true;
        Log.d(TAG, "Media recording started");
    }

    private void initRecorder() {
        try {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(Environment
                    .getExternalStoragePublicDirectory(Environment
                            .DIRECTORY_DOWNLOADS) + "/" + outputFileName);
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(6 * 1024 * 1024); //6m video
            mMediaRecorder.setAudioEncodingBitRate(384 * 1024); //384k audio
            mMediaRecorder.setVideoFrameRate(30);
            int rotation = sourceActivity.getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            //mMediaRecorder.stop();
            //mMediaRecorder.reset();
            Log.d(TAG, "Recording Stopped");
            mMediaProjection = null;
            stopScreenSharing();
        }

    }

    private void stopScreenSharing() {
        isRecording = false;

        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        //mMediaRecorder.release(); //If used: mMediaRecorder object cannot
        // be reused again
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mMediaProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
            Log.d(TAG, "MediaProjection Stopped");
        }
    }
}