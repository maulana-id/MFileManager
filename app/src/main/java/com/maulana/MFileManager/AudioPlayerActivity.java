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

package com.maulana.MFileManager;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Locale;

public class AudioPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "file_path";

    private MediaPlayer mediaPlayer;
    private SeekBar seekBar;
    private TextView currentTimeView, durationView;
    private ImageButton playPauseButton;
    private Handler handler;
    private Runnable updateSeekBar;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_player);

        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (filePath == null) { finish(); return; }

        String fileName = filePath.contains("/")
                ? filePath.substring(filePath.lastIndexOf('/') + 1)
                : filePath;

        ((TextView) findViewById(R.id.audioTitle)).setText(fileName);
        seekBar = findViewById(R.id.audioSeekBar);
        currentTimeView = findViewById(R.id.audioCurrentTime);
        durationView = findViewById(R.id.audioDuration);
        playPauseButton = findViewById(R.id.audioPlayPause);
        ImageButton backButton = findViewById(R.id.audioBackButton);
        ImageButton rewindButton = findViewById(R.id.audioRewind);
        ImageButton forwardButton = findViewById(R.id.audioForward);

        handler = new Handler(Looper.getMainLooper());

        backButton.setOnClickListener(v -> finish());

        // Prepare MediaPlayer
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            Toast.makeText(this, "Cannot open audio file", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mediaPlayer.setOnPreparedListener(mp -> {
            seekBar.setMax(mp.getDuration());
            durationView.setText(formatTime(mp.getDuration()));
            mp.start();
            isPlaying = true;
            updatePlayPauseIcon();
            startSeekBarUpdate();
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Cannot play this audio", Toast.LENGTH_SHORT).show();
            finish();
            return true;
        });

        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            updatePlayPauseIcon();
        });

        playPauseButton.setOnClickListener(v -> togglePlayPause());

        rewindButton.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int pos = Math.max(0, mediaPlayer.getCurrentPosition() - 10000);
                mediaPlayer.seekTo(pos);
            }
        });

        forwardButton.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int pos = Math.min(mediaPlayer.getDuration(),
                        mediaPlayer.getCurrentPosition() + 10000);
                mediaPlayer.seekTo(pos);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    currentTimeView.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        updateSeekBar = new Runnable() {
            @Override public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int pos = mediaPlayer.getCurrentPosition();
                    seekBar.setProgress(pos);
                    currentTimeView.setText(formatTime(pos));
                }
                handler.postDelayed(this, 500);
            }
        };
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
        } else {
            mediaPlayer.start();
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
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            updatePlayPauseIcon();
        }
        handler.removeCallbacks(updateSeekBar);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateSeekBar);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
