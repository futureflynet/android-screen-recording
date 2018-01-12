package com.nextgames.screenrecordplugin;

import android.content.Intent;
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
                    ScreenRecorder.StartMediaRecording();
                } else {
                    ScreenRecorder.StopMediaRecording();
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ScreenRecorder.OpenVideoPlayer();
                        }
                    }, 1000);

                }
            }
        });

        mScreenRecorder = new ScreenRecorder(this);

        ScreenRecorder.RequestPermissions();
        ScreenRecorder.SetOutputFileName("video4.mp4");
        Log.d(TAG,"Output file name is " + ScreenRecorder.GetVideoFileName());
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
