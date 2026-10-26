/*
 * Copyright 2024 maulana-id (https://github.com/maulana-id)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author maulana-id
 */

package com.maulanaid.MFileManager;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import android.content.res.Configuration;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "file_path";

    private AutoRotateHelper autoRotateHelper;
    private VideoView videoView;
    private SeekBar seekBar;
    private TextView currentTimeView, durationView;
    private ImageButton playPauseButton;
    private ProgressBar progressBar;
    private Handler handler;
    private Runnable updateSeekBar;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        autoRotateHelper = new AutoRotateHelper(this);
        autoRotateHelper.updateOrientation();
        setContentView(R.layout.activity_video_player);

        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (filePath == null) {
            finish();
            return;
        }

        String fileName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;

        videoView = findViewById(R.id.videoView);
        seekBar = findViewById(R.id.videoSeekBar);
        currentTimeView = findViewById(R.id.videoCurrentTime);
        durationView = findViewById(R.id.videoDuration);
        playPauseButton = findViewById(R.id.videoPlayPause);
        progressBar = findViewById(R.id.videoProgress);
        ((TextView) findViewById(R.id.videoTitle)).setText(fileName);

        ImageButton backButton = findViewById(R.id.videoBackButton);
        backButton.setOnClickListener(v -> finish());

        handler = new Handler(Looper.getMainLooper());

        videoView.setVideoURI(Uri.parse(filePath));
        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            seekBar.setMax(mp.getDuration());
            durationView.setText(formatTime(mp.getDuration()));
            mp.setLooping(false);
            videoView.start();
            isPlaying = true;
            updatePlayPauseIcon();
            startSeekBarUpdate();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Cannot play this video", Toast.LENGTH_SHORT).show();
            finish();
            return true;
        });
        videoView.setOnCompletionListener(mp -> {
            isPlaying = false;
            updatePlayPauseIcon();
        });

        playPauseButton.setOnClickListener(v -> togglePlayPause());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    videoView.seekTo(progress);
                    currentTimeView.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (videoView.isPlaying()) {
                    int pos = videoView.getCurrentPosition();
                    seekBar.setProgress(pos);
                    currentTimeView.setText(formatTime(pos));
                }
                handler.postDelayed(this, 500);
            }
        };
    }

    private void togglePlayPause() {
        if (videoView.isPlaying()) {
            videoView.pause();
            isPlaying = false;
        } else {
            videoView.start();
            isPlaying = true;
            startSeekBarUpdate();
        }
        updatePlayPauseIcon();
    }

    private void updatePlayPauseIcon() {
        playPauseButton.setImageResource(isPlaying ? R.drawable.ic_action_stop : R.drawable.ic_action_open);
    }

    private void startSeekBarUpdate() {
        handler.removeCallbacks(updateSeekBar);
        handler.post(updateSeekBar);
    }

    private String formatTime(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds %= 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoRotateHelper != null) {
            autoRotateHelper.register();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (videoView != null) {
            videoView.requestLayout();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (autoRotateHelper != null) {
            autoRotateHelper.unregister();
        }
        if (videoView.isPlaying()) {
            videoView.pause();
            isPlaying = false;
            updatePlayPauseIcon();
        }
        handler.removeCallbacks(updateSeekBar);
    }

    @Override
    protected void onDestroy() {
        if (autoRotateHelper != null) {
            autoRotateHelper.unregister();
        }
        handler.removeCallbacks(updateSeekBar);
        videoView.stopPlayback();
        super.onDestroy();
    }
}
