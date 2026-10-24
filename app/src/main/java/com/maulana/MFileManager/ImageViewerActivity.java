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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "file_path";

    private ImageView imageView;
    private ScaleGestureDetector scaleDetector;
    private final Matrix matrix = new Matrix();
    private float scaleFactor = 1f;
    private float lastTouchX, lastTouchY;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 8f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        imageView = findViewById(R.id.imageView);
        TextView title = findViewById(R.id.imageTitle);
        ImageButton backButton = findViewById(R.id.imageBackButton);

        if (filePath == null) {
            finish();
            return;
        }

        String fileName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
        title.setText(fileName);

        backButton.setOnClickListener(v -> finish());

        // Load bitmap efficiently
        loadBitmap(filePath);

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float newScale = scaleFactor * factor;
                if (newScale < MIN_SCALE) factor = MIN_SCALE / scaleFactor;
                if (newScale > MAX_SCALE) factor = MAX_SCALE / scaleFactor;
                scaleFactor = Math.max(MIN_SCALE, Math.min(scaleFactor * factor, MAX_SCALE));
                matrix.postScale(factor, factor,
                        detector.getFocusX(), detector.getFocusY());
                imageView.setImageMatrix(matrix);
                return true;
            }
        });

        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            if (!scaleDetector.isInProgress()) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - lastTouchX;
                        float dy = event.getY() - lastTouchY;
                        matrix.postTranslate(dx, dy);
                        imageView.setImageMatrix(matrix);
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;
                }
            }
            return true;
        });

        // Double-tap to reset zoom
        imageView.setOnClickListener(null);
    }

    private void loadBitmap(String filePath) {
        new Thread(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(filePath, bounds);

                int targetW = getResources().getDisplayMetrics().widthPixels;
                int targetH = getResources().getDisplayMetrics().heightPixels;
                int sampleSize = 1;
                if (bounds.outWidth > targetW * 2 || bounds.outHeight > targetH * 2) {
                    sampleSize = Math.max(bounds.outWidth / targetW, bounds.outHeight / targetH);
                }

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sampleSize;
                Bitmap bitmap = BitmapFactory.decodeFile(filePath, opts);

                if (bitmap != null) {
                    runOnUiThread(() -> {
                        imageView.setImageBitmap(bitmap);
                        // Center the image via matrix after layout
                        imageView.post(() -> {
                            float viewW = imageView.getWidth();
                            float viewH = imageView.getHeight();
                            float bmpW = bitmap.getWidth();
                            float bmpH = bitmap.getHeight();
                            float scale = Math.min(viewW / bmpW, viewH / bmpH);
                            scaleFactor = scale;
                            matrix.reset();
                            matrix.postScale(scale, scale);
                            matrix.postTranslate((viewW - bmpW * scale) / 2f,
                                    (viewH - bmpH * scale) / 2f);
                            imageView.setImageMatrix(matrix);
                        });
                    });
                } else {
                    runOnUiThread(() -> finish());
                }
            } catch (Exception e) {
                runOnUiThread(() -> finish());
            }
        }).start();
    }
}
