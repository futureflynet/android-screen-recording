package com.nextgames.screenrecordlib;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.IOException;

import static android.app.Activity.RESULT_OK;
import static android.content.Intent.EXTRA_CHOSEN_COMPONENT;

/**
 * Created by gimulnautti on 08/01/2018.
 */

public class ScreenRecorder {

    public static final int REQUEST_CODE = 1000;

    public static final int ERROR_NONE = 0;
    public static final int ERROR_RESULTCODE = 1;
    public static final int ERROR_RECORDER_STOP = 2;
    public static final int ERROR_SCREENCAST = 3;
    public static final int ERROR_RECORDER_INIT = 4;

    private static final String TAG = "ScreenRecorder";
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
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
    private static int recordVideoWidth;
    private static int recordVideoHeight;
    private static Runnable errorCallback;
    private static int error = ERROR_NONE;
    private static Handler errorHandler;
    private static Runnable sharedCallback;
    private static String sharedActivity;

    public ScreenRecorder(Activity a) {
        sourceActivity = a;
        sInstance = this;
        recordVideoWidth = 0;
        recordVideoHeight = 0;
        errorHandler = new Handler();
    }

    //// Public static methods for unity interfacing

    public static void RequestPermissions() {
        if (!IsPermissionsGranted()) {
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

    public static boolean IsPermissionsGranted()
    {
        return ContextCompat.checkSelfPermission(sourceActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) + ContextCompat
                .checkSelfPermission(sourceActivity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void SetRecordVideoDimensions(int width, int height)
    {
        recordVideoWidth = width;
        recordVideoHeight = height;
        Log.d(TAG, "Set record video dimensions "+width+" x "+height);
    }

    public static void StartMediaRecording() {
        error = ERROR_NONE;
        sInstance.setup();
        sInstance.initRecorder();
        sInstance.shareScreen();
    }

    public static void StopMediaRecording() {
        Log.d(TAG, "Stopping media recording");
        try {
            sInstance.mMediaRecorder.stop();
            sInstance.mMediaRecorder.reset();
        } catch (RuntimeException ex) {
            Log.e(TAG,"Error stopping media recording "+ex.getLocalizedMessage().toString());
            error = ERROR_RECORDER_STOP;
            errorHandler.post(errorCallback);
        }
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

    public static void ShareVideo(@Nullable Activity activity)
    {
        sInstance.shareVideo(activity == null ? sourceActivity : activity, sInstance.outputFileName, GetVideoFileName());
    }

    public static void OpenVideoPlayer()
    {
        sourceActivity.startActivity(new Intent(sourceActivity, VideoViewActivity.class));
    }

    public static Activity GetSourceActivity() { return sourceActivity; }

    public static void SetErrorCallback(Runnable r) { errorCallback = r; }

    public static void SetSharedCallback(Runnable r) { sharedCallback = r; }

    public static int GetError() { return error; }

    public static String GetSharedActivity() { return sharedActivity; }

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
            error = ERROR_RESULTCODE;
            errorHandler.post(errorCallback);
            return;
        }
        if (resultCode != RESULT_OK) {
            Log.e(TAG, "Screen cast permission denied! " + resultCode);
            error = ERROR_SCREENCAST;
            errorHandler.post(errorCallback);
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

    /**
     * Receive chosen component from ACTION_SEND
     */
    private class Receiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            //sharedActivity = arg1.getExtras().getParcelable(EXTRA_CHOSEN_COMPONENT).toString();
            ComponentName chosenComponent = arg1.getParcelableExtra(EXTRA_CHOSEN_COMPONENT);
            sharedActivity = chosenComponent.flattenToString();
            Log.d(TAG, "Share activity result received: "+sharedActivity);
            final Handler handler = new Handler();
            handler.post(sharedCallback);
        }
    }

    private void shareVideo(final Activity activity, final String title, String path) {

        MediaScannerConnection.scanFile(activity, new String[] { path },
                null, new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        Intent shareIntent = new Intent(
                                android.content.Intent.ACTION_SEND);
                        shareIntent.setType("video/*");
                        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
                        shareIntent.putExtra(android.content.Intent.EXTRA_TITLE, title);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                        shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_NEW_TASK);

                        Intent receiver = new Intent("com.nextgames.screenrecordlib.ShareReceiver");
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(sourceActivity, 0, receiver, PendingIntent.FLAG_UPDATE_CURRENT);
                        sourceActivity.registerReceiver(new Receiver(), new IntentFilter("com.nextgames.screenrecordlib.ShareReceiver"));

                        activity.startActivity(Intent.createChooser(shareIntent, "Share Video", pendingIntent.getIntentSender()));
                    }
                });
    }

    private void setup()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        sourceActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        mMediaRecorder = new MediaRecorder();

        mProjectionManager = (MediaProjectionManager) sourceActivity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("ScreenRecorderUnityPlayerActivity",
                recordVideoWidth, recordVideoHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null
                /*Handler*/);
    }

    private void initRecorder() {
        try {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(Environment
                    .getExternalStoragePublicDirectory(Environment
                            .DIRECTORY_DOWNLOADS) + "/" + outputFileName);
            mMediaRecorder.setVideoSize(recordVideoWidth, recordVideoHeight);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setVideoEncodingBitRate(3 * 1024 * 1024); //3m video
            mMediaRecorder.setAudioEncodingBitRate(384 * 1024); //384k audio
            mMediaRecorder.setVideoFrameRate(30);
            int rotation = sourceActivity.getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mMediaRecorder.setOrientationHint(orientation);
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            error = ERROR_RECORDER_INIT;
            errorHandler.post(errorCallback);
        }
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