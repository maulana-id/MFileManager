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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.TreeMap;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.webkit.MimeTypeMap;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int INSTALL_PERMISSION_REQUEST_CODE = 102;
    private static final String PREFS_NAME = "file_manager_preferences";
    private static final String PREF_USER_AGREEMENT_VERSION = "user_agreement_version";
    private static final int USER_AGREEMENT_VERSION = 1;
    private static final String PREF_GROUPING = "grouping";
    private static final String PREF_SORTING = "sorting";
    private static final String PREF_FOLDERS_FIRST = "folders_first";
    private static final String PREF_DARK_THEME = "dark_theme";
    private static final String PREF_TREE_VIEW = "tree_view_enabled";
    private static final String PREF_ANIMATIONS = "animations_enabled";
    private static final String GROUP_NONE = "none";
    private static final String GROUP_TYPE = "type";
    private static final String GROUP_DATE = "date";
    private static final String SORT_NAME = "name";
    private static final String SORT_DATE = "date";
    private static final String SORT_EXTENSION = "extension";
    private static final int CONFLICT_ASK = 0;
    private static final int CONFLICT_REPLACE_ALL = 1;
    private static final int CONFLICT_SKIP_ALL = 2;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private static final long DOUBLE_BACK_EXIT_WINDOW_MS = 2000;
    private static final String PREF_HTTP_PASSWORD = "http_share_password";
    private static final String PREF_HTTP_USERNAME = "http_share_username";
    private static final String DEFAULT_HTTP_USERNAME = "MFileManager";
    private static final String PREF_HTTP_ACTIVE = "http_share_active";
    private static final String PREF_HTTP_PATH = "http_share_path";
    private static final String PREF_HTTP_URLS = "http_share_urls";
    private static final String PREF_HTTP_ERROR = "http_share_error";
    private static final String PREF_FTP_ACTIVE = "ftp_share_active";
    private static final String PREF_FTP_PATH = "ftp_share_path";
    private static final String PREF_FTP_URLS = "ftp_share_urls";
    private static final String PREF_FTP_ERROR = "ftp_share_error";

    private ListView fileList;
    private FileListAdapter adapter;
    private boolean treeViewEnabled = true;
    private boolean animationsEnabled = true;
    private final Set<String> expandedPaths = new LinkedHashSet<>();
    private File lastClickedDirectory;
    private final List<File> expansionStack = new ArrayList<>();
    private EditText pathInput;
    private EditText searchInput;
    private TextView httpShareStatus;
    private ImageButton stopHttpShareButton;
    private ImageButton copyHttpShareUrlButton;
    private File homeDirectory;
    private File currentDirectory;
    private final Set<File> selectedFiles = new LinkedHashSet<>();
    private final List<File> clipboardFiles = new ArrayList<>();
    private final List<File> allFiles = new ArrayList<>();
    private final List<File> rowFiles = new ArrayList<>();
    private final List<FileListAdapter.Row> displayRows = new ArrayList<>();
    private final ExecutorService archiveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicInteger renderGeneration = new java.util.concurrent.atomic.AtomicInteger(
            0);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private String grouping = GROUP_NONE;
    private String sorting = SORT_NAME;
    private String searchQuery = "";
    private boolean foldersFirst = true;
    private boolean darkTheme;
    private long lastBackPressedAt = -1;
    private String pendingHttpSharePath;
    private boolean pendingFtpShare;
    private File pendingInstallFile;
    private boolean shareStatusReceiverRegistered;
    private final BroadcastReceiver shareStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateHttpShareStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        darkTheme = preferences.getBoolean(PREF_DARK_THEME, false);
        AppCompatDelegate.setDefaultNightMode(darkTheme
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        grouping = preferences.getString(PREF_GROUPING, GROUP_NONE);
        sorting = preferences.getString(PREF_SORTING, SORT_NAME);
        foldersFirst = preferences.getBoolean(PREF_FOLDERS_FIRST, true);
        treeViewEnabled = preferences.getBoolean(PREF_TREE_VIEW, true);
        animationsEnabled = preferences.getBoolean(PREF_ANIMATIONS, true);
        fileList = findViewById(R.id.fileList);
        adapter = new FileListAdapter(this);
        adapter.setOnChevronClickListener(this::toggleExpandedFolder);
        adapter.setAnimationsEnabled(animationsEnabled);
        fileList.setAdapter(adapter);
        pathInput = findViewById(R.id.pathInput);
        searchInput = findViewById(R.id.searchInput);
        httpShareStatus = findViewById(R.id.httpShareStatus);
        stopHttpShareButton = findViewById(R.id.stopHttpShareButton);
        copyHttpShareUrlButton = findViewById(R.id.copyHttpShareUrlButton);
        homeDirectory = Environment.getExternalStorageDirectory();
        currentDirectory = homeDirectory;
        if (savedInstanceState != null) {
            String savedPath = savedInstanceState.getString("current_directory");
            if (savedPath != null) {
                File savedDirectory = new File(savedPath);
                if (savedDirectory.isDirectory()) {
                    currentDirectory = savedDirectory;
                }
            }
        }

        fileList.setOnItemClickListener((parent, view, position, id) -> {
            File clickedFile = rowFiles.get(position);
            if (clickedFile == null) {
                return;
            }
            if (!selectedFiles.isEmpty()) {
                toggleSelectedFile(clickedFile);
                return;
            }
            if (clickedFile.isDirectory()) {
                lastClickedDirectory = clickedFile;
                if (treeViewEnabled) {
                    toggleExpandedFolder(clickedFile);
                } else {
                    currentDirectory = clickedFile;
                    selectedFiles.clear();
                    showFiles();
                }
            } else {
                lastClickedDirectory = clickedFile.getParentFile();
                openFile(clickedFile);
            }
        });
        fileList.setOnItemLongClickListener((parent, view, position, id) -> {
            File clickedFile = rowFiles.get(position);
            if (clickedFile == null) {
                return false;
            }
            if (!selectedFiles.contains(clickedFile)) {
                selectedFiles.add(clickedFile);
                updateSelection();
                fileList.post(this::renderFiles);
                return true;
            }
            updateSelection();
            showContextMenu(clickedFile);
            return true;
        });

        findViewById(R.id.settingsButton).setOnClickListener(this::showSettingsMenu);
        findViewById(R.id.goButton).setOnClickListener(view -> goToPath());
        findViewById(R.id.homeButton).setOnClickListener(view -> goHome());
        findViewById(R.id.upButton).setOnClickListener(view -> goUp());
        findViewById(R.id.createButton).setOnClickListener(this::showCreateMenu);
        stopHttpShareButton.setOnClickListener(view -> stopHttpShare());
        copyHttpShareUrlButton.setOnClickListener(view -> copyHttpShareUrl());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (treeViewEnabled && !expandedPaths.isEmpty()) {
                    goUp();
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                if (lastBackPressedAt >= 0
                        && now - lastBackPressedAt <= DOUBLE_BACK_EXIT_WINDOW_MS) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                lastBackPressedAt = now;
                if (currentDirectory.getParentFile() != null) {
                    goUp();
                } else {
                    toast("Press Back again to exit");
                }
            }
        });
        pathInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                goToPath();
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                String newQuery = text.toString().trim().toLowerCase(Locale.ROOT);
                if (!newQuery.equals(searchQuery)) {
                    selectedFiles.clear();
                    searchQuery = newQuery;
                }
                renderFiles();
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });
        if (preferences.getInt(PREF_USER_AGREEMENT_VERSION, 0) >= USER_AGREEMENT_VERSION) {
            requestStoragePermissions();
        } else {
            showUserAgreement();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!shareStatusReceiverRegistered) {
            ContextCompat.registerReceiver(this, shareStatusReceiver,
                    new IntentFilter(HttpShareService.ACTION_STATUS_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            shareStatusReceiverRegistered = true;
        }
        updateHttpShareStatus();
    }

    @Override
    protected void onPause() {
        if (shareStatusReceiverRegistered) {
            unregisterReceiver(shareStatusReceiver);
            shareStatusReceiverRegistered = false;
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("current_directory", currentDirectory.getAbsolutePath());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (adapter != null) {
            adapter.close();
        }
        archiveExecutor.shutdownNow();
        super.onDestroy();
    }

    private void showUserAgreement() {
        ScrollView scrollView = new ScrollView(this);
        TextView agreementText = new TextView(this);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        agreementText.setPadding(padding, padding / 2, padding, padding / 2);
        agreementText.setText("Please read before using MFIle Manager.\n\n"
                + "- Storage access is used to browse and manage files you choose. Create, rename, copy, archive, extract, and delete actions are initiated by you; deletion and replacement may be difficult or impossible to undo. Keep your own backups.\n\n"
                + "- Files stay on this device unless you explicitly open/share them with another app or start HTTP/FTP sharing. When sharing is active, people on your local network with the share address and credentials may access the selected item.\n\n"
                + "- HTTP and FTP do not encrypt file contents or credentials in transit. Use sharing only on a trusted network and stop it when finished.\n\n"
                + "- You are responsible for having the right to access, copy, install, or share the files you choose, and for following applicable laws and third-party terms.\n\n"
                + "The app is provided as-is. This acknowledgement does not remove rights you have under applicable law.");
        scrollView.addView(agreementText);

        new AlertDialog.Builder(this)
                .setTitle("User agreement and responsibility")
                .setView(scrollView)
                .setPositiveButton("Agree and continue", (dialog, which) -> {
                    preferences.edit()
                            .putInt(PREF_USER_AGREEMENT_VERSION, USER_AGREEMENT_VERSION)
                            .apply();
                    requestStoragePermissions();
                })
                .setNegativeButton("Decline and exit", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            } else {
                showFiles();
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            showFiles();
        } else {
            requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INSTALL_PERMISSION_REQUEST_CODE) {
            File file = pendingInstallFile;
            pendingInstallFile = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && getPackageManager().canRequestPackageInstalls()) {
                openApkInstaller(file);
            } else {
                toast("Allow this app to install unknown apps, then try again");
            }
            return;
        }
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && Environment.isExternalStorageManager()) {
                showFiles();
            } else {
                showPermissionDeniedMessage();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPendingShare();
            } else {
                pendingHttpSharePath = null;
                toast("Notification permission is needed to share files in the background");
            }
            return;
        }
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showFiles();
            } else {
                showPermissionDeniedMessage();
            }
        }
    }

    private void showSettingsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        SubMenu groupingMenu = popup.getMenu().addSubMenu("Group by");
        addSettingChoice(groupingMenu, "None", GROUP_NONE, grouping, PREF_GROUPING, true);
        addSettingChoice(groupingMenu, "File type", GROUP_TYPE, grouping, PREF_GROUPING, true);
        addSettingChoice(groupingMenu, "Modified date", GROUP_DATE, grouping, PREF_GROUPING, true);
        groupingMenu.setGroupCheckable(Menu.NONE, true, true);

        SubMenu sortingMenu = popup.getMenu().addSubMenu("Sort by");
        addSettingChoice(sortingMenu, "Name", SORT_NAME, sorting, PREF_SORTING, false);
        addSettingChoice(sortingMenu, "Modified date", SORT_DATE, sorting, PREF_SORTING, false);
        addSettingChoice(sortingMenu, "Extension", SORT_EXTENSION, sorting, PREF_SORTING, false);
        sortingMenu.setGroupCheckable(Menu.NONE, true, true);

        MenuItem pasteItem = popup.getMenu().add("Paste into current folder");
        pasteItem.setEnabled(!clipboardFiles.isEmpty());
        pasteItem.setOnMenuItemClickListener(item -> {
            pasteClipboard(currentDirectory);
            return true;
        });

        MenuItem credentialsItem = popup.getMenu().add("File share login");
        credentialsItem.setOnMenuItemClickListener(item -> {
            showShareCredentialsDialog(null, false);
            return true;
        });

        MenuItem licensesItem = popup.getMenu().add("Open source licenses");
        licensesItem.setOnMenuItemClickListener(item -> {
            OssLicensesMenuActivity.setActivityTitle("Open source licenses");
            startActivity(new Intent(this, OssLicensesMenuActivity.class));
            return true;
        });

        MenuItem foldersItem = popup.getMenu().add("Folders first");
        foldersItem.setCheckable(true).setChecked(foldersFirst);
        foldersItem.setOnMenuItemClickListener(item -> {
            foldersFirst = !foldersFirst;
            preferences.edit().putBoolean(PREF_FOLDERS_FIRST, foldersFirst).apply();
            showFiles();
            return true;
        });

        MenuItem treeItem = popup.getMenu().add("Tree view");
        treeItem.setCheckable(true).setChecked(treeViewEnabled);
        treeItem.setOnMenuItemClickListener(item -> {
            treeViewEnabled = !treeViewEnabled;
            preferences.edit().putBoolean(PREF_TREE_VIEW, treeViewEnabled).apply();
            renderFiles();
            return true;
        });

        MenuItem animItem = popup.getMenu().add("Smooth animations");
        animItem.setCheckable(true).setChecked(animationsEnabled);
        animItem.setOnMenuItemClickListener(item -> {
            animationsEnabled = !animationsEnabled;
            preferences.edit().putBoolean(PREF_ANIMATIONS, animationsEnabled).apply();
            adapter.setAnimationsEnabled(animationsEnabled);
            return true;
        });

        MenuItem themeItem = popup.getMenu().add("Dark theme");
        themeItem.setCheckable(true).setChecked(darkTheme);
        themeItem.setOnMenuItemClickListener(item -> {
            darkTheme = !darkTheme;
            preferences.edit().putBoolean(PREF_DARK_THEME, darkTheme).apply();
            AppCompatDelegate.setDefaultNightMode(darkTheme
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
            return true;
        });
        popup.show();
    }

    private void addSettingChoice(SubMenu menu, String title, String value,
            String selectedValue, String preferenceKey, boolean isGrouping) {
        MenuItem item = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, title);
        item.setCheckable(true).setChecked(value.equals(selectedValue));
        item.setOnMenuItemClickListener(selected -> {
            if (isGrouping) {
                grouping = value;
            } else {
                sorting = value;
            }
            preferences.edit().putString(preferenceKey, value).apply();
            showFiles();
            return true;
        });
    }

    private void showContextMenu(File pressedFile) {
        List<ActionItem> actions = new ArrayList<>();
        if (selectedFiles.size() == 1 && pressedFile.isDirectory()) {
            actions.add(new ActionItem("Browse into this folder", R.drawable.ic_action_open, () -> {
                currentDirectory = pressedFile;
                selectedFiles.clear();
                showFiles();
            }));
            if (treeViewEnabled) {
                boolean isExpanded = expandedPaths.contains(pressedFile.getAbsolutePath());
                actions.add(new ActionItem(isExpanded ? "Collapse folder" : "Expand folder",
                        isExpanded ? R.drawable.ic_tree_collapse : R.drawable.ic_tree_expand,
                        () -> toggleExpandedFolder(pressedFile)));
            }
        }
        if (selectedFiles.size() == 1 && !pressedFile.isDirectory()) {
            actions.add(new ActionItem("Open with", R.drawable.ic_action_open,
                    () -> openFile(pressedFile)));
            actions.add(new ActionItem("Open as...", R.drawable.ic_action_open,
                    () -> showOpenAsDialog(pressedFile)));
        }
        if (selectedFiles.size() == 1) {
            if (pressedFile.getParentFile() != null) {
                actions.add(new ActionItem("Rename", R.drawable.ic_action_rename,
                        () -> showRenameDialog(pressedFile)));
            }
            actions.add(new ActionItem("Properties", R.drawable.ic_action_info,
                    () -> showPropertiesDialog(pressedFile)));
        }
        if (selectedFiles.size() == 1 && ArchiveManager.isSupportedArchive(pressedFile)) {
            actions.add(new ActionItem("Browse archive", R.drawable.ic_file_archive,
                    () -> showArchivePasswordDialog(pressedFile, false)));
            actions.add(new ActionItem("Extract archive...", R.drawable.ic_file_archive,
                    () -> showArchivePasswordDialog(pressedFile, true)));
        }
        if (!selectedFiles.isEmpty()) {
            actions.add(new ActionItem("Compress " + selectedFiles.size() + " item(s)...",
                    R.drawable.ic_file_archive, this::showArchiveFormatChooser));
        }
        actions.add(new ActionItem("Share this file/folder over HTTP",
                R.drawable.ic_action_share, () -> requestHttpShare(pressedFile)));
        if (pressedFile.isDirectory()) {
            actions.add(new ActionItem("Share folder over FTP (read-only)",
                    R.drawable.ic_action_share, () -> requestFtpShare(pressedFile)));
        }
        actions.add(new ActionItem("Copy " + selectedFiles.size() + " item(s)",
                R.drawable.ic_action_copy, this::copySelected));
        if (!clipboardFiles.isEmpty()) {
            File destination = pressedFile.isDirectory() ? pressedFile : currentDirectory;
            actions.add(new ActionItem("Paste here", R.drawable.ic_action_paste,
                    () -> pasteClipboard(destination)));
        }
        actions.add(new ActionItem("Delete " + selectedFiles.size() + " item(s)",
                R.drawable.ic_action_delete, this::deleteSelected));
        actions.add(new ActionItem("Select all visible", R.drawable.ic_action_check, () -> {
            for (File file : rowFiles) {
                if (file != null && (searchQuery.isEmpty()
                        || file.getName().toLowerCase(Locale.ROOT).contains(searchQuery))) {
                    selectedFiles.add(file);
                }
            }
            renderFiles();
        }));
        actions.add(new ActionItem("Clear selection", R.drawable.ic_action_check, () -> {
            selectedFiles.clear();
            renderFiles();
        }));

        ArrayAdapter<ActionItem> actionAdapter = new ArrayAdapter<ActionItem>(this, 0, actions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView == null
                        ? LayoutInflater.from(MainActivity.this)
                                .inflate(R.layout.action_menu_item, parent, false)
                        : convertView;
                ActionItem action = getItem(position);
                ((ImageView) row.findViewById(R.id.actionIcon)).setImageResource(action.icon);
                ((TextView) row.findViewById(R.id.actionTitle)).setText(action.title);
                return row;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(selectedFiles.size() + " selected")
                .setAdapter(actionAdapter, (dialog, position) -> actions.get(position).action.run())
                .show();
    }

    private void toggleSelectedFile(File file) {
        if (!selectedFiles.remove(file)) {
            selectedFiles.add(file);
        }
        renderFiles();
    }

    private void showFiles() {
        File[] files = currentDirectory.listFiles();
        allFiles.clear();
        if (files != null) {
            allFiles.addAll(Arrays.asList(files));
        }
        String path = currentDirectory.getAbsolutePath();
        if (!path.equals(pathInput.getText().toString())) {
            pathInput.setText(path);
        }
        renderFiles();
    }

    private void toggleExpandedFolder(File folder) {
        String path = folder.getAbsolutePath();
        if (expandedPaths.contains(path)) {
            expandedPaths.remove(path);
            expansionStack.remove(folder);
            lastClickedDirectory = folder.getParentFile();
        } else {
            expandedPaths.add(path);
            expansionStack.remove(folder);
            expansionStack.add(folder);
            lastClickedDirectory = folder;
        }
        renderFiles();
        // Do NOT call focusDirectory here — we restore scroll position
        // inside renderFiles() so the view stays put when expanding/collapsing.
    }

    private void focusDirectory(File dir) {
        if (dir == null) {
            return;
        }
        fileList.post(() -> {
            for (int i = 0; i < rowFiles.size(); i++) {
                File file = rowFiles.get(i);
                if (file != null && file.equals(dir)) {
                    fileList.setSelection(i);
                    break;
                }
            }
        });
    }

    // ──────────────────────────────────────────────────────────
    // Async render with per-pass listFiles cache
    // ──────────────────────────────────────────────────────────

    private void renderFiles() {
        // Snapshot mutable state on the main thread
        final int gen = renderGeneration.incrementAndGet();
        final boolean treeView = treeViewEnabled;
        final File dir = currentDirectory;
        final String query = searchQuery;
        final String grp = grouping;
        final Set<String> expanded = new LinkedHashSet<>(expandedPaths);
        final Set<File> selected = new LinkedHashSet<>(selectedFiles);
        final List<File> flatFiles = new ArrayList<>(allFiles);
        final Comparator<File> cmp = fileComparator();
        final SimpleDateFormat dateFmt = new SimpleDateFormat("MMM d", Locale.getDefault());
        // Capture current scroll position so we can restore it after the render
        final int savedScrollPos = fileList.getFirstVisiblePosition();
        final View savedTopChild = fileList.getChildAt(0);
        final int savedScrollOffset = (savedTopChild != null) ? savedTopChild.getTop() : 0;

        renderExecutor.execute(() -> {
            // Abort if a newer render has been requested
            if (renderGeneration.get() != gen)
                return;

            // Per-pass cache: directory path → its children array
            Map<String, File[]> dirCache = new HashMap<>();

            List<FileListAdapter.Row> rows = new ArrayList<>();
            List<File> rowFilesList = new ArrayList<>();

            if (treeView) {
                buildTreeRows(dir, 0, query, expanded, selected, cmp, rows, rowFilesList,
                        dirCache, dateFmt);
            } else {
                List<File> filtered = new ArrayList<>();
                for (File f : flatFiles) {
                    if (query.isEmpty()
                            || f.getName().toLowerCase(Locale.ROOT).contains(query)) {
                        filtered.add(f);
                    }
                }
                filtered.sort(cmp);

                if (GROUP_TYPE.equals(grp)) {
                    Map<String, List<File>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    for (File f : filtered) {
                        String key = f.isDirectory() ? "Folders"
                                : fileExtension(f).isEmpty() ? "Files without extension"
                                        : "Files ." + fileExtension(f);
                        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
                    }
                    for (Map.Entry<String, List<File>> e : groups.entrySet()) {
                        e.getValue().sort(cmp);
                        buildFlatRows(e.getKey(), e.getValue(), selected,
                                rows, rowFilesList, dirCache, dateFmt);
                    }
                } else if (GROUP_DATE.equals(grp)) {
                    Map<Long, List<File>> groups = new TreeMap<>(Collections.reverseOrder());
                    for (File f : filtered) {
                        long day = startOfDay(f.lastModified());
                        groups.computeIfAbsent(day, k -> new ArrayList<>()).add(f);
                    }
                    for (Map.Entry<Long, List<File>> e : groups.entrySet()) {
                        e.getValue().sort(cmp);
                        buildFlatRows(dateGroupName(e.getKey()), e.getValue(), selected,
                                rows, rowFilesList, dirCache, dateFmt);
                    }
                } else {
                    buildFlatRows(null, filtered, selected, rows, rowFilesList, dirCache, dateFmt);
                }
            }

            if (renderGeneration.get() != gen)
                return; // discard if superseded

            mainHandler.post(() -> {
                displayRows.clear();
                displayRows.addAll(rows);
                rowFiles.clear();
                rowFiles.addAll(rowFilesList);
                adapter.setRows(displayRows);
                updateSelection();
                // Restore scroll so the view doesn't jump to the top
                if (savedScrollPos >= 0) {
                    fileList.setSelectionFromTop(savedScrollPos, savedScrollOffset);
                }
            });
        });
    }

    /**
     * Recursively builds tree rows into the provided lists using a shared dirCache.
     */
    private void buildTreeRows(File directory, int depth,
            String query, Set<String> expanded, Set<File> selected,
            Comparator<File> cmp,
            List<FileListAdapter.Row> rows, List<File> rowFiles,
            Map<String, File[]> dirCache,
            SimpleDateFormat dateFmt) {
        File[] children = cachedListFiles(directory, dirCache);
        if (children == null)
            return;

        List<File> childList = new ArrayList<>(Arrays.asList(children));
        childList.sort(cmp);

        for (File file : childList) {
            boolean isDir = file.isDirectory();
            boolean matchesSearch = query.isEmpty()
                    || file.getName().toLowerCase(Locale.ROOT).contains(query);
            if (!query.isEmpty() && !matchesSearch && !isDir)
                continue;

            boolean isExpanded = isDir && expanded.contains(file.getAbsolutePath());
            File[] grandChildren = isDir ? cachedListFiles(file, dirCache) : null;
            boolean hasChildren = grandChildren != null && grandChildren.length > 0;

            rows.add(FileListAdapter.Row.treeNode(
                    file,
                    fileDetailsFromCache(file, grandChildren, dateFmt),
                    selected.contains(file),
                    depth, isDir, isExpanded, hasChildren));
            rowFiles.add(file);

            if (isDir && isExpanded) {
                buildTreeRows(file, depth + 1, query, expanded, selected,
                        cmp, rows, rowFiles, dirCache, dateFmt);
            }
        }
    }

    private void buildFlatRows(String groupName, List<File> files, Set<File> selected,
            List<FileListAdapter.Row> rows, List<File> rowFiles,
            Map<String, File[]> dirCache, SimpleDateFormat dateFmt) {
        if (groupName != null) {
            rows.add(FileListAdapter.Row.heading(groupName));
            rowFiles.add(null);
        }
        for (File file : files) {
            File[] children = file.isDirectory() ? cachedListFiles(file, dirCache) : null;
            rows.add(FileListAdapter.Row.file(
                    file, fileDetailsFromCache(file, children, dateFmt), selected.contains(file)));
            rowFiles.add(file);
        }
    }

    /** Returns cached listFiles result, or fetches and stores it. */
    private File[] cachedListFiles(File dir, Map<String, File[]> cache) {
        String key = dir.getAbsolutePath();
        if (cache.containsKey(key))
            return cache.get(key);
        File[] result = dir.listFiles();
        cache.put(key, result);
        return result;
    }

    /**
     * Builds the detail string without calling listFiles() again — uses
     * already-fetched array.
     */
    private String fileDetailsFromCache(File file, File[] cachedChildren,
            SimpleDateFormat dateFmt) {
        String modified = dateFmt.format(new Date(file.lastModified()));
        if (file.isDirectory()) {
            String count = cachedChildren != null
                    ? cachedChildren.length + " item" + (cachedChildren.length == 1 ? "" : "s")
                    : "Folder";
            return count + "  " + modified;
        }
        long size = file.length();
        String sizeStr;
        if (size < 1024) {
            sizeStr = size + " B";
        } else if (size < 1024L * 1024L) {
            sizeStr = String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        } else {
            sizeStr = String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
        }
        return sizeStr + "  " + modified;
    }

    /** Legacy fileDetails used outside of render (e.g. file info dialog). */
    private String fileDetails(File file) {
        return fileDetailsFromCache(file,
                file.isDirectory() ? file.listFiles() : null,
                new SimpleDateFormat("MMM d", Locale.getDefault()));
    }

    private Comparator<File> fileComparator() {
        return (first, second) -> {
            if (foldersFirst) {
                int folderOrder = Boolean.compare(first.isFile(), second.isFile());
                if (folderOrder != 0) {
                    return folderOrder;
                }
            }
            int result;
            if (SORT_DATE.equals(sorting)) {
                result = Long.compare(second.lastModified(), first.lastModified());
            } else if (SORT_EXTENSION.equals(sorting)) {
                result = fileExtension(first).compareToIgnoreCase(fileExtension(second));
            } else {
                result = 0;
            }
            if (result == 0) {
                result = first.getName().compareToIgnoreCase(second.getName());
            }
            return result;
        };
    }

    private String fileExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private long startOfDay(long timestamp) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(timestamp);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    private String dateGroupName(long dayTimestamp) {
        long today = startOfDay(System.currentTimeMillis());
        Calendar yesterday = Calendar.getInstance();
        yesterday.setTimeInMillis(today);
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (dayTimestamp == today) {
            return "Today";
        }
        if (dayTimestamp == yesterday.getTimeInMillis()) {
            return "Yesterday";
        }
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(new Date(dayTimestamp));
    }

    private void goToPath() {
        String enteredPath = pathInput.getText().toString().trim();
        if (enteredPath.isEmpty()) {
            toast("Enter a folder path");
            return;
        }
        File requested = new File(enteredPath);
        if (!requested.isAbsolute()) {
            requested = new File(currentDirectory, enteredPath);
        }
        try {
            requested = requested.getCanonicalFile();
        } catch (IOException exception) {
            toast("Invalid folder path");
            return;
        }
        if (!requested.isDirectory() || !requested.canRead()) {
            toast("Folder not found or cannot be read");
            return;
        }
        currentDirectory = requested;
        selectedFiles.clear();
        searchInput.setText("");
        showFiles();
    }

    private void goUp() {
        if (treeViewEnabled) {
            File targetToCollapse = null;
            if (lastClickedDirectory != null && expandedPaths.contains(lastClickedDirectory.getAbsolutePath())) {
                targetToCollapse = lastClickedDirectory;
            } else {
                for (int i = expansionStack.size() - 1; i >= 0; i--) {
                    File candidate = expansionStack.get(i);
                    if (expandedPaths.contains(candidate.getAbsolutePath())) {
                        targetToCollapse = candidate;
                        break;
                    }
                }
            }

            if (targetToCollapse != null) {
                expandedPaths.remove(targetToCollapse.getAbsolutePath());
                expansionStack.remove(targetToCollapse);
                lastClickedDirectory = targetToCollapse.getParentFile();
                renderFiles();
                focusDirectory(targetToCollapse);
                return;
            }
        }

        File previousDir = currentDirectory;
        File parent = currentDirectory.getParentFile();
        if (parent != null && parent.canRead()) {
            currentDirectory = parent;
            lastClickedDirectory = parent;
            selectedFiles.clear();
            showFiles();
            focusDirectory(previousDir);
        } else if (parent != null) {
            toast("Cannot read parent folder");
        }
    }

    private void goHome() {
        currentDirectory = homeDirectory;
        selectedFiles.clear();
        searchInput.setText("");
        showFiles();
    }

    private void showCreateMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("New folder").setOnMenuItemClickListener(item -> {
            showCreateDialog(true);
            return true;
        });
        popup.getMenu().add("New empty file").setOnMenuItemClickListener(item -> {
            showCreateDialog(false);
            return true;
        });
        popup.show();
    }

    private void showCreateDialog(boolean createDirectory) {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint(createDirectory ? "Folder name" : "File name");
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        nameInput.setPadding(padding, padding / 2, padding, padding / 2);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(createDirectory ? "New folder" : "New empty file")
                .setView(nameInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    if (!isValidLocalName(name)) {
                        nameInput.setError("Enter a valid name without path separators");
                        return;
                    }
                    File destination = new File(currentDirectory, name);
                    if (destination.exists()) {
                        nameInput.setError("An item with this name already exists");
                        return;
                    }
                    if (!currentDirectory.canWrite()) {
                        toast("Current folder cannot be changed");
                        return;
                    }
                    boolean created;
                    try {
                        created = createDirectory ? destination.mkdir() : destination.createNewFile();
                    } catch (IOException exception) {
                        created = false;
                    }
                    if (!created) {
                        toast(createDirectory ? "Could not create folder" : "Could not create file");
                        return;
                    }
                    dialog.dismiss();
                    showFiles();
                    toast(createDirectory ? "Folder created" : "File created");
                }));
        dialog.show();
    }

    private void showRenameDialog(File source) {
        File parent = source.getParentFile();
        if (parent == null || !parent.canWrite()) {
            toast("This item cannot be renamed");
            return;
        }
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(source.getName());
        nameInput.setSelectAllOnFocus(true);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        nameInput.setPadding(padding, padding / 2, padding, padding / 2);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Rename item")
                .setView(nameInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    if (!isValidLocalName(name)) {
                        nameInput.setError("Enter a valid name without path separators");
                        return;
                    }
                    if (source.getName().equals(name)) {
                        dialog.dismiss();
                        return;
                    }
                    File destination = new File(parent, name);
                    if (destination.exists()) {
                        nameInput.setError("An item with this name already exists");
                        return;
                    }
                    if (!source.renameTo(destination)) {
                        toast("Could not rename item");
                        return;
                    }
                    for (int index = 0; index < clipboardFiles.size(); index++) {
                        if (clipboardFiles.get(index).equals(source)) {
                            clipboardFiles.set(index, destination);
                        }
                    }
                    selectedFiles.clear();
                    dialog.dismiss();
                    showFiles();
                    toast("Item renamed");
                }));
        dialog.show();
    }

    private boolean isValidLocalName(String name) {
        return !name.isEmpty() && !".".equals(name) && !"..".equals(name)
                && !name.contains("/") && !name.contains("\\") && name.indexOf('\0') < 0;
    }

    private void showPropertiesDialog(File file) {
        String type = file.isDirectory() ? "Folder"
                : fileExtension(file).isEmpty() ? "File" : fileExtension(file).toUpperCase(Locale.ROOT);
        String size = file.isDirectory()
                ? file.listFiles() == null ? "Unavailable" : file.listFiles().length + " item(s)"
                : formatFileSize(file.length());
        String modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(file.lastModified()));
        String details = "Type: " + type
                + "\nSize: " + size
                + "\nModified: " + modified
                + "\nReadable: " + (file.canRead() ? "Yes" : "No")
                + "\nWritable: " + (file.canWrite() ? "Yes" : "No")
                + "\n\nPath: " + file.getAbsolutePath();
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void showArchiveFormatChooser() {
        List<File> sources = getNormalizedSelection();
        if (sources.isEmpty()) {
            toast("Select files or folders to compress");
            return;
        }
        List<ArchiveManager.Format> formats = new ArrayList<>(Arrays.asList(
                ArchiveManager.Format.ZIP, ArchiveManager.Format.SEVEN_ZIP,
                ArchiveManager.Format.TAR_GZ, ArchiveManager.Format.TAR));
        List<String> labels = new ArrayList<>(Arrays.asList("ZIP", "7z", "TAR.GZ", "TAR"));
        if (sources.size() == 1 && sources.get(0).isFile()) {
            formats.add(ArchiveManager.Format.GZIP);
            labels.add("GZIP (single file)");
        }
        new AlertDialog.Builder(this)
                .setTitle("Compress " + sources.size() + " item(s) as")
                .setItems(labels.toArray(new String[0]),
                        (dialog, which) -> showArchiveCreateDialog(sources, formats.get(which)))
                .show();
    }

    private String archiveBaseName(File source) {
        if (ArchiveManager.isSupportedArchive(source)) {
            return ArchiveManager.removeArchiveExtension(source);
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        return source.isFile() && dot > 0 ? name.substring(0, dot) : name;
    }

    private void showArchiveCreateDialog(List<File> sources, ArchiveManager.Format format) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);

        String baseName = sources.size() == 1
                ? archiveBaseName(sources.get(0))
                : currentDirectory.getName();
        if (baseName.isEmpty()) {
            baseName = "Archive";
        }
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(baseName);
        nameInput.setHint("Archive name");
        fields.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText passwordInput = null;
        if (format == ArchiveManager.Format.ZIP || format == ArchiveManager.Format.SEVEN_ZIP) {
            passwordInput = new EditText(this);
            passwordInput.setSingleLine(true);
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordInput.setHint("Optional archive password");
            fields.addView(passwordInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        EditText archivePasswordInput = passwordInput;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Create " + format.name().replace('_', '.'))
                .setMessage(format == ArchiveManager.Format.ZIP || format == ArchiveManager.Format.SEVEN_ZIP
                        ? "Password protection uses AES encryption. Leave blank for no password."
                        : "This format does not support password protection.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    if (!isValidLocalName(name)) {
                        nameInput.setError("Enter a valid archive name");
                        return;
                    }
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    String extension = format.extension();
                    String outputName = lowerName.endsWith(extension)
                            ? name
                            : name + extension;
                    File output = new File(currentDirectory, outputName);
                    if (output.exists()) {
                        nameInput.setError("An item with this name already exists");
                        return;
                    }
                    char[] password = archivePasswordInput == null ? new char[0]
                            : archivePasswordInput.getText().toString().toCharArray();
                    dialog.dismiss();
                    runArchiveOperation("Creating " + output.getName(), () -> {
                        try {
                            ArchiveManager.Result result = ArchiveManager.create(sources,
                                    output, format, password.length == 0 ? null : password);
                            double saved = result.savedPercent();
                            String ratio = saved >= 0
                                    ? String.format(Locale.getDefault(), "%.1f%% smaller", saved)
                                    : String.format(Locale.getDefault(), "%.1f%% larger", -saved);
                            return "Created " + output.getName() + "\n"
                                    + result.fileCount + " file(s)\nOriginal: "
                                    + formatFileSize(result.originalBytes) + "\nArchive: "
                                    + formatFileSize(result.archiveBytes) + "\n" + ratio;
                        } finally {
                            Arrays.fill(password, '\0');
                        }
                    }, true);
                }));
        dialog.show();
    }

    private void showArchivePasswordDialog(File archive, boolean extract) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);

        EditText destinationInput = null;
        if (extract) {
            destinationInput = new EditText(this);
            destinationInput.setSingleLine(true);
            destinationInput.setText(ArchiveManager.removeArchiveExtension(archive));
            destinationInput.setHint("Extract to folder");
            fields.addView(destinationInput, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("Password (leave blank if none)");
        fields.addView(passwordInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText targetInput = destinationInput;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(extract ? "Extract archive" : "Browse archive")
                .setMessage("RAR creation is not supported; RAR archives can be browsed and extracted.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(extract ? "Extract" : "Browse", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    File destination = null;
                    if (extract) {
                        String name = targetInput.getText().toString().trim();
                        if (!isValidLocalName(name)) {
                            targetInput.setError("Enter a valid folder name");
                            return;
                        }
                        destination = new File(currentDirectory, name);
                        if (destination.exists()) {
                            targetInput.setError("That folder already exists");
                            return;
                        }
                    }
                    File extractFolder = destination;
                    char[] password = passwordInput.getText().toString().toCharArray();
                    dialog.dismiss();
                    if (extract) {
                        runArchiveOperation("Extracting " + archive.getName(), () -> {
                            try {
                                int count = ArchiveManager.extract(archive, extractFolder,
                                        password.length == 0 ? null : password);
                                return "Extracted " + count + " file(s) to "
                                        + extractFolder.getAbsolutePath();
                            } finally {
                                Arrays.fill(password, '\0');
                            }
                        }, true);
                    } else {
                        runArchiveOperation("Reading " + archive.getName(), () -> {
                            try {
                                List<ArchiveManager.EntryInfo> entries = ArchiveManager
                                        .listEntries(archive, password.length == 0 ? null : password);
                                return describeArchive(archive, entries);
                            } finally {
                                Arrays.fill(password, '\0');
                            }
                        }, false);
                    }
                }));
        dialog.show();
    }

    private String describeArchive(File archive, List<ArchiveManager.EntryInfo> entries) {
        StringBuilder description = new StringBuilder();
        long unpackedBytes = 0;
        int shown = Math.min(entries.size(), 250);
        for (int index = 0; index < entries.size(); index++) {
            ArchiveManager.EntryInfo entry = entries.get(index);
            if (entry.unpackedSize >= 0) {
                unpackedBytes += entry.unpackedSize;
            }
            if (index < shown) {
                description.append(entry.directory ? "[Folder] " : "[File] ")
                        .append(entry.name);
                if (!entry.directory && entry.unpackedSize >= 0) {
                    description.append("  (").append(formatFileSize(entry.unpackedSize)).append(')');
                }
                description.append('\n');
            }
        }
        if (entries.size() > shown) {
            description.append("... and ").append(entries.size() - shown).append(" more entrie(s)\n");
        }
        description.append("\nEntries: ").append(entries.size())
                .append("\nUnpacked: ").append(formatFileSize(unpackedBytes))
                .append("\nArchive: ").append(formatFileSize(archive.length()));
        if (unpackedBytes > 0) {
            double ratio = (1.0 - (double) archive.length() / unpackedBytes) * 100.0;
            description.append("\nRatio: ").append(String.format(Locale.getDefault(), "%.1f%%", ratio));
        }
        return description.toString();
    }

    private void runArchiveOperation(String title, ArchiveOperation operation, boolean refresh) {
        ProgressBar progress = new ProgressBar(this);
        LinearLayout progressContainer = new LinearLayout(this);
        progressContainer.setPadding(32, 16, 32, 16);
        progressContainer.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(progressContainer)
                .setCancelable(false)
                .create();
        progressDialog.show();
        archiveExecutor.execute(() -> {
            String result = null;
            Exception failure = null;
            try {
                result = operation.run();
            } catch (Exception exception) {
                failure = exception;
            }
            String completedResult = result;
            Exception completedFailure = failure;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progressDialog.dismiss();
                if (completedFailure != null) {
                    String message = completedFailure.getMessage();
                    new AlertDialog.Builder(this)
                            .setTitle("Archive operation failed")
                            .setMessage(message == null ? "The archive could not be processed." : message)
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                if (refresh) {
                    showFiles();
                }
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(completedResult)
                        .setPositiveButton("OK", null)
                        .show();
            });
        });
    }

    private interface ArchiveOperation {
        String run() throws Exception;
    }

    private void copySelected() {
        List<File> sources = getNormalizedSelection();
        if (sources.isEmpty()) {
            toast("Select a file or folder first");
            return;
        }
        clipboardFiles.clear();
        clipboardFiles.addAll(sources);
        selectedFiles.clear();
        renderFiles();
        toast("Copied " + clipboardFiles.size() + " item(s)");
    }

    private List<File> getNormalizedSelection() {
        List<File> candidates = new ArrayList<>(selectedFiles);
        candidates.sort(Comparator.comparingInt(file -> file.getAbsolutePath().length()));
        List<File> normalized = new ArrayList<>();
        List<String> acceptedPaths = new ArrayList<>();
        for (File candidate : candidates) {
            try {
                String candidatePath = candidate.getCanonicalPath();
                boolean nested = false;
                for (String acceptedPath : acceptedPaths) {
                    if (candidatePath.equals(acceptedPath)
                            || candidatePath.startsWith(acceptedPath + File.separator)) {
                        nested = true;
                        break;
                    }
                }
                if (!nested) {
                    normalized.add(candidate);
                    acceptedPaths.add(candidatePath);
                }
            } catch (IOException exception) {
                normalized.add(candidate);
            }
        }
        return normalized;
    }

    private void pasteClipboard(File targetDirectory) {
        if (clipboardFiles.isEmpty()) {
            toast("Clipboard is empty");
            return;
        }
        if (!targetDirectory.isDirectory() || !targetDirectory.canWrite()) {
            toast("Destination folder cannot be written");
            return;
        }
        List<File> sources = new ArrayList<>(clipboardFiles);
        processPasteItem(sources, 0, targetDirectory, CONFLICT_ASK, new PasteProgress());
    }

    private void processPasteItem(List<File> sources, int index, File targetDirectory,
            int conflictPolicy, PasteProgress progress) {
        if (index >= sources.size()) {
            finishPaste(progress, false);
            return;
        }

        File source = sources.get(index);
        File destination = new File(targetDirectory, source.getName());
        try {
            String sourcePath = source.getCanonicalPath();
            String destinationPath = destination.getCanonicalPath();
            if (sourcePath.equals(destinationPath)
                    || (source.isDirectory()
                            && destinationPath.startsWith(sourcePath + File.separator))) {
                progress.failed++;
                processPasteItem(sources, index + 1, targetDirectory, conflictPolicy, progress);
                return;
            }
        } catch (IOException exception) {
            progress.failed++;
            processPasteItem(sources, index + 1, targetDirectory, conflictPolicy, progress);
            return;
        }

        if (destination.exists() && conflictPolicy == CONFLICT_ASK) {
            String[] choices = { "Replace", "Skip", "Replace all", "Skip all", "Cancel" };
            new AlertDialog.Builder(this)
                    .setTitle("Already exists: " + source.getName())
                    .setMessage("Replacing a folder merges it and overwrites matching files.")
                    .setItems(choices, (dialog, choice) -> {
                        switch (choice) {
                            case 0:
                                copyPasteItem(source, destination, progress);
                                processPasteItem(sources, index + 1, targetDirectory,
                                        CONFLICT_ASK, progress);
                                break;
                            case 1:
                                progress.skipped++;
                                processPasteItem(sources, index + 1, targetDirectory,
                                        CONFLICT_ASK, progress);
                                break;
                            case 2:
                                copyPasteItem(source, destination, progress);
                                processPasteItem(sources, index + 1, targetDirectory,
                                        CONFLICT_REPLACE_ALL, progress);
                                break;
                            case 3:
                                progress.skipped++;
                                processPasteItem(sources, index + 1, targetDirectory,
                                        CONFLICT_SKIP_ALL, progress);
                                break;
                            default:
                                finishPaste(progress, true);
                                break;
                        }
                    })
                    .setOnCancelListener(dialog -> finishPaste(progress, true))
                    .show();
            return;
        }

        if (destination.exists() && conflictPolicy == CONFLICT_SKIP_ALL) {
            progress.skipped++;
        } else {
            copyPasteItem(source, destination, progress);
        }
        processPasteItem(sources, index + 1, targetDirectory, conflictPolicy, progress);
    }

    private void copyPasteItem(File source, File destination, PasteProgress progress) {
        try {
            copyRecursively(source, destination);
            progress.copied++;
        } catch (IOException exception) {
            progress.failed++;
        }
    }

    private void finishPaste(PasteProgress progress, boolean cancelled) {
        showFiles();
        String result = (cancelled ? "Paste cancelled. " : "Paste complete. ")
                + progress.copied + " copied, " + progress.skipped + " skipped, "
                + progress.failed + " failed";
        toast(result);
    }

    private void deleteSelected() {
        List<File> files = getNormalizedSelection();
        if (files.isEmpty()) {
            toast("Select a file or folder first");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete " + files.size() + " item(s)?")
                .setMessage("Folders and their contents will be deleted recursively.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    int deleted = 0;
                    int failed = 0;
                    for (File file : files) {
                        if (deleteRecursively(file)) {
                            deleted++;
                        } else {
                            failed++;
                        }
                    }
                    selectedFiles.clear();
                    showFiles();
                    toast(deleted + " deleted, " + failed + " failed");
                })
                .show();
    }

    private void openFile(File file) {
        if (!file.isFile() || !file.canRead()) {
            toast("File cannot be opened");
            return;
        }
        String extension = fileExtension(file);
        String mimeType = "apk".equals(extension)
                ? "application/vnd.android.package-archive"
                : MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mimeType == null) {
            mimeType = "*/*";
        }
        // Try internal viewers first
        if (openFileInternally(file, mimeType))
            return;
        openFileWithMimeType(file, mimeType);
    }

    /** Returns true if an internal viewer handled the file. */
    private boolean openFileInternally(File file, String mimeType) {
        if (mimeType == null)
            return false;
        if (mimeType.startsWith("image/")) {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            return true;
        }
        if (mimeType.startsWith("video/")) {
            Intent intent = new Intent(this, VideoPlayerActivity.class);
            intent.putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            return true;
        }
        if (mimeType.startsWith("audio/")) {
            Intent intent = new Intent(this, AudioPlayerActivity.class);
            intent.putExtra(AudioPlayerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            return true;
        }
        if (mimeType.startsWith("text/") || isTextExtension(fileExtension(file))) {
            Intent intent = new Intent(this, TextEditorActivity.class);
            intent.putExtra(TextEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
            return true;
        }
        return false;
    }

    private boolean isTextExtension(String ext) {
        switch (ext) {
            case "txt":
            case "md":
            case "log":
            case "json":
            case "xml":
            case "yaml":
            case "yml":
            case "toml":
            case "ini":
            case "cfg":
            case "conf":
            case "sh":
            case "bash":
            case "py":
            case "js":
            case "ts":
            case "java":
            case "kt":
            case "c":
            case "cpp":
            case "h":
            case "cs":
            case "go":
            case "rs":
            case "rb":
            case "php":
            case "html":
            case "css":
            case "sql":
            case "csv":
            case "properties":
            case "gradle":
            case "bat":
            case "ps1":
                return true;
            default:
                return false;
        }
    }

    private void showOpenAsDialog(File file) {
        String[] labels = { "Automatic", "Text editor (built-in)", "Image viewer (built-in)",
                "Audio player (built-in)", "Video player (built-in)",
                "Text / source code", "Audio", "Video", "Image",
                "PDF", "APK installer", "Archive", "Any file type" };
        new AlertDialog.Builder(this)
                .setTitle("Open as")
                .setItems(labels, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            openFile(file);
                            break;
                        case 1: {
                            Intent i = new Intent(this, TextEditorActivity.class);
                            i.putExtra(TextEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case 2: {
                            Intent i = new Intent(this, ImageViewerActivity.class);
                            i.putExtra(ImageViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case 3: {
                            Intent i = new Intent(this, AudioPlayerActivity.class);
                            i.putExtra(AudioPlayerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case 4: {
                            Intent i = new Intent(this, VideoPlayerActivity.class);
                            i.putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case 5:
                            openFileWithMimeType(file, "text/plain");
                            break;
                        case 6:
                            openFileWithMimeType(file, "audio/*");
                            break;
                        case 7:
                            openFileWithMimeType(file, "video/*");
                            break;
                        case 8:
                            openFileWithMimeType(file, "image/*");
                            break;
                        case 9:
                            openFileWithMimeType(file, "application/pdf");
                            break;
                        case 10:
                            openFileWithMimeType(file, "application/vnd.android.package-archive");
                            break;
                        case 11:
                            openFileWithMimeType(file, "application/zip");
                            break;
                        case 12:
                            openFileWithMimeType(file, "*/*");
                            break;
                    }
                })
                .show();
    }

    private void openFileWithMimeType(File file, String mimeType) {
        if (!file.isFile() || !file.canRead()) {
            toast("File cannot be opened");
            return;
        }
        if ("application/vnd.android.package-archive".equals(mimeType)) {
            openApkInstaller(file);
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (ActivityNotFoundException exception) {
            toast("No app can open this file");
        } catch (IllegalArgumentException | SecurityException exception) {
            toast("Unable to share this file with another app");
        }
    }

    private void openApkInstaller(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            toast("APK file cannot be read");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstallFile = file;
            Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            permissionIntent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(permissionIntent, INSTALL_PERMISSION_REQUEST_CODE);
            } catch (ActivityNotFoundException exception) {
                pendingInstallFile = null;
                toast("Enable Install unknown apps for MFIle Manager in Android settings");
            }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(installIntent);
        } catch (ActivityNotFoundException exception) {
            toast("No package installer is available");
        } catch (IllegalArgumentException | SecurityException exception) {
            toast("Unable to open this APK with the package installer");
        }
    }

    private void copyRecursively(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (destination.exists() && !destination.isDirectory()
                    && !deleteRecursively(destination)) {
                throw new IOException("Cannot replace destination file");
            }
            if (!destination.mkdirs() && !destination.isDirectory()) {
                throw new IOException("Cannot create destination");
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Cannot read source folder");
            }
            for (File child : children) {
                copyRecursively(child, new File(destination, child.getName()));
            }
        } else {
            if (destination.exists() && destination.isDirectory()
                    && !deleteRecursively(destination)) {
                throw new IOException("Cannot replace destination folder");
            }
            copyFile(source, destination);
        }
    }

    private void copyFile(File source, File destination) throws IOException {
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(destination)) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }

    private boolean deleteRecursively(File file) {
        File[] children = file.isDirectory() ? file.listFiles() : null;
        if (children != null) {
            for (File child : children) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    private void updateSelection() {
        int count = selectedFiles.size();
        TextView selView = findViewById(R.id.selectionText);
        if (selView == null) {
            return;
        }
        if (count == 0 && clipboardFiles.isEmpty()) {
            selView.setVisibility(View.GONE);
            selView.setText("");
        } else {
            selView.setVisibility(View.VISIBLE);
            String text = count == 0 ? "Nothing selected" : count + " item(s) selected";
            if (!clipboardFiles.isEmpty()) {
                text += " | Clipboard: " + clipboardFiles.size() + " item(s)";
            }
            selView.setText(text);
        }
    }

    private static class PasteProgress {
        int copied;
        int skipped;
        int failed;
    }

    private static class ActionItem {
        final String title;
        final int icon;
        final Runnable action;

        ActionItem(String title, int icon, Runnable action) {
            this.title = title;
            this.icon = icon;
            this.action = action;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showPermissionDeniedMessage() {
        toast("Storage permission is required to browse files");
    }

    private void requestHttpShare(File target) {
        if (!target.exists() || !target.canRead()) {
            toast("This item cannot be shared");
            return;
        }
        String username = preferences.getString(PREF_HTTP_USERNAME, DEFAULT_HTTP_USERNAME);
        String password = preferences.getString(PREF_HTTP_PASSWORD, "");
        if (!isValidHttpUsername(username) || password.length() < 8) {
            showShareCredentialsDialog(target, false);
            return;
        }
        pendingHttpSharePath = target.getAbsolutePath();
        pendingFtpShare = false;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
            return;
        }
        launchPendingShare();
    }

    private void requestFtpShare(File target) {
        if (!target.isDirectory() || !target.canRead()) {
            toast("FTP sharing requires a readable folder");
            return;
        }
        String username = preferences.getString(PREF_HTTP_USERNAME, DEFAULT_HTTP_USERNAME);
        String password = preferences.getString(PREF_HTTP_PASSWORD, "");
        if (!isValidHttpUsername(username) || password.length() < 8) {
            showShareCredentialsDialog(target, true);
            return;
        }
        pendingHttpSharePath = target.getAbsolutePath();
        pendingFtpShare = true;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
            return;
        }
        launchPendingShare();
    }

    private void showShareCredentialsDialog(File target, boolean ftpShare) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);

        EditText usernameInput = new EditText(this);
        usernameInput.setSingleLine(true);
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        usernameInput.setHint("Username");
        usernameInput.setText(preferences.getString(PREF_HTTP_USERNAME, DEFAULT_HTTP_USERNAME));
        fields.addView(usernameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setSingleLine(true);
        passwordInput.setHint(preferences.getString(PREF_HTTP_PASSWORD, "").isEmpty()
                ? "Password (at least 8 characters)"
                : "New password, or blank to keep current");
        fields.addView(passwordInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("File share login")
                .setMessage(
                        "Username: 1-64 printable characters, no colon. Password: at least 8 characters. HTTP and FTP do not encrypt credentials or file data.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String username = usernameInput.getText().toString().trim();
                    if (!isValidHttpUsername(username)) {
                        usernameInput.setError("Use 1-64 printable characters, without a colon");
                        return;
                    }
                    String typedPassword = passwordInput.getText().toString();
                    String password = typedPassword.isEmpty()
                            ? preferences.getString(PREF_HTTP_PASSWORD, "")
                            : typedPassword;
                    if (password.length() < 8) {
                        passwordInput.setError("Use at least 8 characters");
                        return;
                    }
                    if (preferences.getBoolean(PREF_HTTP_ACTIVE, false)) {
                        stopHttpShare();
                    } else if (preferences.getBoolean(PREF_FTP_ACTIVE, false)) {
                        stopHttpShare();
                    }
                    preferences.edit()
                            .putString(PREF_HTTP_USERNAME, username)
                            .putString(PREF_HTTP_PASSWORD, password)
                            .apply();
                    dialog.dismiss();
                    toast("File share login saved");
                    if (target != null) {
                        if (ftpShare) {
                            requestFtpShare(target);
                        } else {
                            requestHttpShare(target);
                        }
                    }
                }));
        dialog.show();
    }

    private boolean isValidHttpUsername(String username) {
        if (username.isEmpty() || username.length() > 64) {
            return false;
        }
        for (int index = 0; index < username.length(); index++) {
            char character = username.charAt(index);
            if (character < 0x21 || character > 0x7e || character == ':') {
                return false;
            }
        }
        return true;
    }

    private void launchPendingShare() {
        String path = pendingHttpSharePath;
        if (path == null) {
            return;
        }
        pendingHttpSharePath = null;
        boolean ftpShare = pendingFtpShare;
        pendingFtpShare = false;
        if (ftpShare) {
            if (preferences.getBoolean(PREF_FTP_ACTIVE, false)) {
                String activePath = preferences.getString(PREF_FTP_PATH, "");
                if (!path.equals(activePath)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Replace active FTP share?")
                            .setMessage("Only one folder can be shared over FTP at a time.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Replace", (dialog, which) -> startFtpShareService(path))
                            .show();
                    return;
                }
            }
            startFtpShareService(path);
            return;
        }
        if (preferences.getBoolean(PREF_HTTP_ACTIVE, false)) {
            String activePath = preferences.getString(PREF_HTTP_PATH, "");
            if (!path.equals(activePath)) {
                new AlertDialog.Builder(this)
                        .setTitle("Replace active HTTP share?")
                        .setMessage("Only one file or folder can be shared at a time.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Replace", (dialog, which) -> startHttpShareService(path))
                        .show();
                return;
            }
        }
        startHttpShareService(path);
    }

    private void startHttpShareService(String path) {
        Intent intent = new Intent(this, HttpShareService.class)
                .setAction(HttpShareService.ACTION_START)
                .putExtra(HttpShareService.EXTRA_SHARED_PATH, path);
        try {
            ContextCompat.startForegroundService(this, intent);
        } catch (RuntimeException exception) {
            toast("Could not start HTTP sharing");
        }
    }

    private void startFtpShareService(String path) {
        Intent intent = new Intent(this, FtpShareService.class)
                .setAction(FtpShareService.ACTION_START)
                .putExtra(FtpShareService.EXTRA_SHARED_PATH, path);
        try {
            ContextCompat.startForegroundService(this, intent);
        } catch (RuntimeException exception) {
            toast("Could not start FTP sharing");
        }
    }

    private void stopHttpShare() {
        Intent intent = new Intent(this, HttpShareService.class)
                .setAction(HttpShareService.ACTION_STOP);
        startService(intent);
        Intent ftpIntent = new Intent(this, FtpShareService.class)
                .setAction(FtpShareService.ACTION_STOP);
        startService(ftpIntent);
    }

    private void updateHttpShareStatus() {
        if (httpShareStatus == null || stopHttpShareButton == null) {
            return;
        }
        boolean httpActive = preferences.getBoolean(PREF_HTTP_ACTIVE, false);
        boolean ftpActive = preferences.getBoolean(PREF_FTP_ACTIVE, false);
        if (!httpActive && !ftpActive) {
            String httpError = preferences.getString(PREF_HTTP_ERROR, "");
            String ftpError = preferences.getString(PREF_FTP_ERROR, "");
            String error = !ftpError.isEmpty() ? ftpError : httpError;
            httpShareStatus.setText(error.isEmpty() ? "File sharing is off" : error);
            stopHttpShareButton.setVisibility(View.GONE);
            copyHttpShareUrlButton.setVisibility(View.GONE);
            return;
        }
        StringBuilder status = new StringBuilder();
        if (httpActive) {
            String path = preferences.getString(PREF_HTTP_PATH, "Shared item");
            String urls = preferences.getString(PREF_HTTP_URLS, "http://<device-ip>:8080");
            status.append("HTTP: ").append(new File(path).getName())
                    .append(" | Port 8080\n").append(urls);
        }
        if (ftpActive) {
            String path = preferences.getString(PREF_FTP_PATH, "Shared folder");
            String urls = preferences.getString(PREF_FTP_URLS, "ftp://<device-ip>:2121");
            if (status.length() > 0) {
                status.append('\n');
            }
            status.append("FTP (read-only): ").append(new File(path).getName())
                    .append(" | Port 2121\n").append(urls)
                    .append("\nPassive ports 21210-21219");
        }
        stopHttpShareButton.setVisibility(View.VISIBLE);
        stopHttpShareButton.setContentDescription("Stop file sharing");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopHttpShareButton.setTooltipText("Stop file sharing");
        }
        httpShareStatus.setText(status.toString());
        copyHttpShareUrlButton.setVisibility(View.VISIBLE);
    }

    private void copyHttpShareUrl() {
        String urls = preferences.getBoolean(PREF_HTTP_ACTIVE, false)
                ? preferences.getString(PREF_HTTP_URLS, "")
                : preferences.getString(PREF_FTP_URLS, "");
        int lineBreak = urls.indexOf('\n');
        String firstUrl = lineBreak < 0 ? urls : urls.substring(0, lineBreak);
        if (firstUrl.isEmpty() || firstUrl.contains("<device-ip>")) {
            toast("No local network address is available");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("File share address", firstUrl));
        toast("Share address copied");
    }
}