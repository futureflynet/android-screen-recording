package com.nextgames.screenrecordlib;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import  com.unity3d.player.UnityPlayerActivity;

/**
 * Created by gimulnautti on 09/01/2018.
 */

public class ScreenRecorderUnityPlayerActivity extends UnityPlayerActivity {

    ScreenRecorder mScreenRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // call UnityPlayerActivity.onCreate()
        super.onCreate(savedInstanceState);
        // print debug message to logcat
        mScreenRecorder = new ScreenRecorder(this);
        Log.d("UnityPlayerActivityOvr", "Created screen recorder instance");
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mScreenRecorder.onActivityResult(requestCode, resultCode, data);
    }
}
