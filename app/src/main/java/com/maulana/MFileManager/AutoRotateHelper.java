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

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

public class AutoRotateHelper {

    private final Activity activity;
    private ContentObserver rotationObserver;

    public AutoRotateHelper(Activity activity) {
        this.activity = activity;
    }

    public boolean isAutoRotateEnabled() {
        try {
            return Settings.System.getInt(
                    activity.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0
            ) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public void updateOrientation() {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (isAutoRotateEnabled()) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    public void register() {
        updateOrientation();
        if (rotationObserver == null) {
            rotationObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    updateOrientation();
                }
            };
            try {
                activity.getContentResolver().registerContentObserver(
                        Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                        false,
                        rotationObserver
                );
            } catch (Exception ignored) {
            }
        }
    }

    public void unregister() {
        if (rotationObserver != null) {
            try {
                activity.getContentResolver().unregisterContentObserver(rotationObserver);
            } catch (Exception ignored) {
            }
            rotationObserver = null;
        }
    }
}
