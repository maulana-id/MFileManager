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

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TextEditorActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "file_path";
    private static final int MAX_FILE_SIZE_BYTES = 1024 * 1024; // 1 MB safety cap

    private EditText editor;
    private TextView statusLine;
    private File file;
    private boolean hasUnsavedChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_editor);

        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (filePath == null) {
            finish();
            return;
        }

        file = new File(filePath);
        String fileName = file.getName();

        editor = findViewById(R.id.textEditor);
        statusLine = findViewById(R.id.textStatusLine);
        TextView titleView = findViewById(R.id.textTitle);
        ImageButton backButton = findViewById(R.id.textBackButton);
        ImageButton saveButton = findViewById(R.id.textSaveButton);

        titleView.setText(fileName);

        backButton.setOnClickListener(v -> {
            if (hasUnsavedChanges) {
                confirmDiscard();
            } else {
                finish();
            }
        });

        saveButton.setOnClickListener(v -> saveFile());

        loadFile();

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!hasUnsavedChanges) {
                    hasUnsavedChanges = true;
                    showUnsavedStatus();
                }
            }
        });
    }

    private void loadFile() {
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(this, "Cannot read file", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (file.length() > MAX_FILE_SIZE_BYTES) {
            Toast.makeText(this, "File too large to edit (> 1 MB)", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            try (FileReader reader = new FileReader(file)) {
                char[] buf = new char[4096];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                }
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error reading file", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            String content = sb.toString();
            runOnUiThread(() -> {
                editor.setText(content);
                editor.setSelection(0);
                hasUnsavedChanges = false;
                statusLine.setVisibility(android.view.View.GONE);
            });
        }).start();
    }

    private void saveFile() {
        if (!file.canWrite()) {
            Toast.makeText(this, "File is not writable", Toast.LENGTH_SHORT).show();
            return;
        }
        String content = editor.getText().toString();
        new Thread(() -> {
            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(content);
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> {
                hasUnsavedChanges = false;
                statusLine.setVisibility(android.view.View.GONE);
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void showUnsavedStatus() {
        statusLine.setVisibility(android.view.View.VISIBLE);
        statusLine.setText("Unsaved changes");
        statusLine.setTextColor(0xFFFF9800); // orange
    }

    private void confirmDiscard() {
        new AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("Discard changes and go back?")
                .setPositiveButton("Discard", (d, w) -> finish())
                .setNegativeButton("Keep editing", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (hasUnsavedChanges) {
            confirmDiscard();
        } else {
            super.onBackPressed();
        }
    }
}
