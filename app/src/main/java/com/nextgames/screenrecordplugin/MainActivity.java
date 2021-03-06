package com.nextgames.screenrecordplugin;

import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.nextgames.screenrecordlib.ScreenRecorder;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    ScreenRecorder mScreenRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Started screen recording", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                if (!ScreenRecorder.IsRecording()) {
                    ScreenRecorder.RequestPermissions();
                    while (!ScreenRecorder.IsPermissionsGranted()) {}
                    ScreenRecorder.StartMediaRecording();
                } else {
                    ScreenRecorder.StopMediaRecording();
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (ScreenRecorder.GetError() != ScreenRecorder.ERROR_NONE) {
                                Log.e(TAG, "Error in screen recording " + ScreenRecorder.GetError());
                            } else {
                                ScreenRecorder.OpenVideoPlayer();
                            }
                        }
                    }, 1000);

                }
            }
        });

        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(displaySize);

        mScreenRecorder = new ScreenRecorder(this);

        ScreenRecorder.RequestPermissions();
        ScreenRecorder.SetOutputFileName("video5.mp4");
        ScreenRecorder.SetRecordVideoDimensions(displaySize.x, displaySize.y); // HACK just assume portrait layout here
        Log.d(TAG,"Output file name is " + ScreenRecorder.GetVideoFileName());

        ScreenRecorder.SetErrorCallback(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "Caught error from Screen Recorder! "+ScreenRecorder.GetError());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mScreenRecorder.onActivityResult(requestCode, resultCode, data);
    }
}
