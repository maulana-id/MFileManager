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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;

import androidx.core.app.NotificationCompat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import fi.iki.elonen.NanoHTTPD;

public final class HttpShareService extends Service {
    public static final String ACTION_START = "com.maulana.MFileManager.HTTP_SHARE_START";
    public static final String ACTION_STOP = "com.maulana.MFileManager.HTTP_SHARE_STOP";
    public static final String ACTION_STATUS_CHANGED = "com.maulana.MFileManager.HTTP_SHARE_STATUS";
    public static final String EXTRA_SHARED_PATH = "shared_path";
    public static final int PORT = 8080;

    private static final String PREFS_NAME = "file_manager_preferences";
    private static final String PREF_HTTP_PASSWORD = "http_share_password";
    private static final String PREF_HTTP_USERNAME = "http_share_username";
    private static final String DEFAULT_HTTP_USERNAME = "MFileManager";
    private static final String PREF_HTTP_ACTIVE = "http_share_active";
    private static final String PREF_HTTP_PATH = "http_share_path";
    private static final String PREF_HTTP_URLS = "http_share_urls";
    private static final String PREF_HTTP_ERROR = "http_share_error";
    private static final String PREF_DARK_THEME = "dark_theme";
    private static final String CHANNEL_ID = "http_file_share";
    private static final int NOTIFICATION_ID = 8080;
    private static final int SOCKET_READ_TIMEOUT = 10_000;

    private SharedFileServer server;
    private File sharedRoot;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSharing();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundCompat(buildNotification("Starting HTTP share..."));
        String path = intent.getStringExtra(EXTRA_SHARED_PATH);
        try {
            File candidate = path == null ? null : new File(path).getCanonicalFile();
            if (candidate == null || !candidate.exists() || !candidate.canRead()) {
                throw new IOException("The selected item is unavailable");
            }
            stopServer();
            sharedRoot = candidate;
            SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            preferences.edit().remove(PREF_HTTP_ERROR).apply();
            String username = preferences.getString(PREF_HTTP_USERNAME, DEFAULT_HTTP_USERNAME);
            String password = preferences.getString(PREF_HTTP_PASSWORD, "");
            if (!isValidUsername(username) || password.length() < 8) {
                throw new IOException("Set valid share credentials first");
            }
            server = new SharedFileServer(sharedRoot, username, password);
            server.start(SOCKET_READ_TIMEOUT, false);

            String urls = getLocalUrls();
            preferences.edit()
                    .putBoolean(PREF_HTTP_ACTIVE, true)
                    .putString(PREF_HTTP_PATH, sharedRoot.getAbsolutePath())
                    .putString(PREF_HTTP_URLS, urls)
                    .apply();
            startForegroundCompat(buildNotification("Sharing " + sharedRoot.getName()
                    + " on port " + PORT));
            notifyStatusChanged();
        } catch (IOException | RuntimeException exception) {
            stopServer();
            clearShareState();
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_HTTP_ERROR,
                    "Could not start sharing. Check that port 8080 is free and the item is readable.")
                .apply();
            stopForeground(true);
            stopSelf();
            notifyStatusChanged();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopServer();
        clearShareState();
        notifyStatusChanged();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopSharing() {
        stopServer();
        clearShareState();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(PREF_HTTP_ERROR).apply();
        stopForeground(true);
        notifyStatusChanged();
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        sharedRoot = null;
    }

    private void clearShareState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_HTTP_ACTIVE, false)
                .remove(PREF_HTTP_PATH)
                .remove(PREF_HTTP_URLS)
                .apply();
    }

    private void notifyStatusChanged() {
        Intent update = new Intent(ACTION_STATUS_CHANGED).setPackage(getPackageName());
        sendBroadcast(update);
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "HTTP file sharing", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows when a folder or file is being shared on the local network");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String status) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, HttpShareService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_share)
                .setContentTitle("MFIle Manager HTTP share")
                .setContentText(status)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, "Stop sharing", stopPendingIntent)
                .build();
    }

    private String getLocalUrls() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<java.net.InetAddress> found = networkInterface.getInetAddresses();
                while (found.hasMoreElements()) {
                    java.net.InetAddress address = found.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        addresses.add("http://" + address.getHostAddress() + ":" + PORT);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        Collections.sort(addresses);
        if (addresses.isEmpty()) {
            return "http://<device-ip>:" + PORT;
        }
        StringBuilder result = new StringBuilder();
        for (String address : addresses) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(address);
        }
        return result.toString();
    }

    private boolean isValidUsername(String username) {
        if (username == null || username.isEmpty() || username.length() > 64) {
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

    private final class SharedFileServer extends NanoHTTPD {
        private final File root;
        private final String expectedAuthorization;
        private final String canonicalRoot;
        private final boolean rootIsDirectory;

        SharedFileServer(File root, String username, String password) throws IOException {
            super(PORT);
            this.root = root;
            this.canonicalRoot = root.getCanonicalPath();
            this.rootIsDirectory = root.isDirectory();
            String credentials = username + ":" + password;
            this.expectedAuthorization = "Basic " + Base64.encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (!authorized(session)) {
                Response response = text(Response.Status.UNAUTHORIZED, "Authentication required");
                response.addHeader("WWW-Authenticate",
                    "Basic realm=\"MFIle Manager HTTP share\", charset=\"UTF-8\"");
                return response;
            }
            try {
                String uri = session.getUri();
                if (Method.PUT.equals(session.getMethod()) && "/upload".equals(uri)) {
                    return upload(session);
                }
                if (Method.DELETE.equals(session.getMethod()) && "/delete".equals(uri)) {
                    return deleteSelected(session);
                }
                if (Method.GET.equals(session.getMethod()) && "/theme".equals(uri)) {
                    boolean darkTheme = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .getBoolean(PREF_DARK_THEME, false);
                    return text(Response.Status.OK, darkTheme ? "dark" : "light");
                }
                if (Method.GET.equals(session.getMethod()) && "/download.zip".equals(uri)) {
                    return downloadZip(session);
                }
                if (Method.GET.equals(session.getMethod()) && "/download".equals(uri)) {
                    return download(session);
                }
                if (Method.GET.equals(session.getMethod())
                        && ("/".equals(uri) || "/index.html".equals(uri))) {
                    return listing(session);
                }
                return text(Response.Status.NOT_FOUND, "Not found");
            } catch (IOException | RuntimeException exception) {
                return text(Response.Status.INTERNAL_ERROR, "Request failed");
            }
        }

        private boolean authorized(IHTTPSession session) {
            String authorization = session.getHeaders().get("authorization");
            if (authorization == null) {
                authorization = session.getHeaders().get("Authorization");
            }
            if (authorization == null) {
                return false;
            }
            return MessageDigest.isEqual(expectedAuthorization.getBytes(StandardCharsets.UTF_8),
                    authorization.getBytes(StandardCharsets.UTF_8));
        }

        private Response listing(IHTTPSession session) throws IOException {
            String relative = parameter(session, "path");
            File directory = resolve(relative);
            if (directory == null || (!directory.isDirectory() && directory != root)) {
                return text(Response.Status.NOT_FOUND, "Folder not found");
            }
            List<File> children = new ArrayList<>();
            if (directory.isDirectory()) {
                File[] files = directory.listFiles();
                if (files == null) {
                    return text(Response.Status.FORBIDDEN, "Folder cannot be listed");
                }
                Collections.addAll(children, files);
                children.sort(Comparator.comparing(File::isFile)
                        .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            } else {
                children.add(directory);
            }
            return html(Response.Status.OK, buildListing(directory, relative, children));
        }

        private String buildListing(File directory, String relative, List<File> children)
                throws IOException {
            String title = rootIsDirectory ? (relative.isEmpty() ? root.getName() : directory.getName())
                    : root.getName();
            boolean darkTheme = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_DARK_THEME, false);
            SimpleDateFormat modifiedDateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm", Locale.getDefault());
            StringBuilder page = new StringBuilder(4096);
                page.append("<!doctype html><html lang=\"en\" class=\"")
                    .append(darkTheme ? "dark" : "light")
                    .append("\"><meta charset=\"utf-8\">")
                    .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                    .append("<title>").append(escapeHtml(title)).append(" - MFIle Manager</title>")
                    .append("<style>")
                    .append(":root{color-scheme:light;--page:#f3f6f4;--text:#17211e;--muted:#65716c;--line:#d7dfdb;--panel:#fff;--accent:#00695c;--accent-text:#fff}")
                    .append(":root.dark{color-scheme:dark;--page:#111815;--text:#e6eee9;--muted:#a8b7ae;--line:#34443c;--panel:#1b2721;--accent:#69c7a5;--accent-text:#102019}")
                    .append("*{box-sizing:border-box}body{margin:0;background:var(--page);color:var(--text);font:16px system-ui,sans-serif}")
                    .append("header{background:var(--accent);color:var(--accent-text);padding:18px max(16px,calc((100% - 900px)/2))}")
                    .append("main{max-width:900px;margin:auto;padding:20px 16px}h1{font-size:22px;margin:0 0 8px}")
                    .append(".path{font-size:13px;opacity:.85;overflow-wrap:anywhere}.toolbar{display:flex;gap:10px;align-items:center;margin:12px 0}")
                    .append("button,.upload{border:0;border-radius:6px;background:var(--accent);color:var(--accent-text);padding:10px 14px;font:inherit;cursor:pointer}")
                    .append(".upload input{display:none}.list{background:var(--panel);border:1px solid var(--line);border-radius:8px;overflow:hidden}")
                    .append(".row{display:flex;align-items:center;gap:12px;padding:13px 14px;border-bottom:1px solid var(--line)}")
                    .append(".row:last-child{border:0}.name{flex:1;min-width:0;overflow-wrap:anywhere}.meta{color:var(--muted);font-size:13px}")
                    .append(".status{min-height:22px;color:var(--accent);font-size:14px;margin:10px 0}.parent{display:block;margin-bottom:12px}")
                    .append("dialog{max-width:min(92vw,460px);width:100%;border:1px solid var(--line);border-radius:8px;background:var(--panel);color:var(--text);padding:20px}")
                    .append("dialog::backdrop{background:#0008}.conflict-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}.conflict-actions button:last-child{grid-column:1/-1}")
                    .append("a{color:var(--accent);text-decoration:none}input[type=checkbox]{width:20px;height:20px}")
                    .append("@media(max-width:500px){main{padding:14px 10px}.row{gap:8px;padding:11px 8px}.toolbar{flex-wrap:wrap}}")
                    .append("</style><body data-directory=\"").append(escapeHtml(relative))
                    .append("\"><header><h1>MFIle Manager HTTP share</h1><div class=\"path\">")
                    .append(escapeHtml(root.getAbsolutePath()))
                    .append(relative.isEmpty() ? "" : " / " + escapeHtml(relative))
                    .append("</div></header><main>");

            if (rootIsDirectory && !relative.isEmpty()) {
                String parent = parentPath(relative);
                page.append("<a class=\"parent\" href=\"/?path=")
                        .append(urlEncode(parent)).append("\">&#8592; Parent folder</a>");
            }
            page.append("<div class=\"toolbar\"><button id=\"download\">Download selected as ZIP</button>");
            if (rootIsDirectory) {
                page.append("<button id=\"delete\">Delete selected</button>")
                        .append("<label class=\"upload\">Upload files<input id=\"files\" type=\"file\" multiple></label>");
            }
            page.append("</div><div id=\"status\" class=\"status\"></div><div class=\"list\">");
            for (File child : children) {
                String childRelative = rootIsDirectory
                        ? (relative.isEmpty() ? child.getName() : relative + "/" + child.getName())
                        : "";
                String encoded = urlEncode(childRelative);
                String name = escapeHtml(child.getName());
                page.append("<div class=\"row\">");
                if (rootIsDirectory) {
                    page.append("<input type=\"checkbox\" class=\"pick\" data-path=\"")
                        .append(escapeHtml(childRelative)).append("\">");
                }
                page.append("<span class=\"name\">");
                if (child.isDirectory()) {
                    page.append("<a href=\"/?path=").append(encoded).append("\">&#128193; ")
                            .append(name).append("/</a>");
                } else {
                    page.append("<a href=\"/download?path=").append(encoded)
                            .append("\">&#128196; ").append(name).append("</a>");
                }
                page.append("</span><span class=\"meta\">");
                String modified = modifiedDateFormat.format(new Date(child.lastModified()));
                if (child.isFile()) {
                    page.append(formatSize(child.length())).append(" &middot; ").append(modified);
                } else {
                    page.append("Folder &middot; ").append(modified);
                }
                page.append("</span></div>");
            }
            if (children.isEmpty()) {
                page.append("<div class=\"row\"><span class=\"meta\">This folder is empty.</span></div>");
            }
            page.append("</div>");
            if (rootIsDirectory) {
                page.append("<dialog id=\"upload-conflict\"><h2>File already exists</h2>")
                        .append("<p id=\"conflict-name\"></p><div class=\"conflict-actions\">")
                        .append("<button data-choice=\"replace\">Replace</button>")
                        .append("<button data-choice=\"skip\">Skip</button>")
                        .append("<button data-choice=\"replace-all\">Replace all</button>")
                        .append("<button data-choice=\"skip-all\">Skip all</button>")
                        .append("<button data-choice=\"cancel\">Cancel remaining</button>")
                        .append("</div></dialog>");
            }
            page.append("<script>")
                    .append("const statusBox=document.getElementById('status');")
                    .append("document.getElementById('download').onclick=()=>{const p=new URLSearchParams();")
                    .append("document.querySelectorAll('.pick:checked').forEach(x=>p.append('path',x.dataset.path));")
                    .append("if(!p.has('path')){statusBox.textContent='Select at least one item.';return;}")
                    .append("location.href='/download.zip?'+p.toString();};");
            page.append("const currentTheme=document.documentElement.classList.contains('dark')?'dark':'light';")
                        .append("setInterval(async()=>{try{const r=await fetch('/theme',{cache:'no-store'});if(r.ok&&(await r.text())!==currentTheme)location.reload();}catch(error){}},3000);");
            if (rootIsDirectory) {
                page.append("const conflictDialog=document.getElementById('upload-conflict');let resolveConflict=null;")
                        .append("conflictDialog.addEventListener('click',e=>{const button=e.target.closest('[data-choice]');if(button&&resolveConflict){const resolve=resolveConflict;resolveConflict=null;conflictDialog.close();resolve(button.dataset.choice);}});")
                        .append("conflictDialog.addEventListener('cancel',e=>{e.preventDefault();if(resolveConflict){const resolve=resolveConflict;resolveConflict=null;resolve('cancel');}conflictDialog.close();});")
                        .append("function askUploadConflict(name){document.getElementById('conflict-name').textContent=name;return new Promise(resolve=>{resolveConflict=resolve;conflictDialog.showModal();});}");
                page.append("document.getElementById('delete').onclick=async()=>{const p=new URLSearchParams();")
                        .append("document.querySelectorAll('.pick:checked').forEach(x=>p.append('path',x.dataset.path));")
                        .append("const count=document.querySelectorAll('.pick:checked').length;")
                        .append("if(!count){statusBox.textContent='Select items to delete.';return;}")
                        .append("if(!confirm(`Permanently delete ${count} selected item(s)?`))return;")
                        .append("statusBox.textContent='Deleting selected items...';try{const r=await fetch('/delete?'+p.toString(),{method:'DELETE'});")
                        .append("statusBox.textContent=await r.text();if(r.ok)setTimeout(()=>location.reload(),700);")
                        .append("}catch(error){statusBox.textContent='Delete request failed.';}};");
                page.append("document.getElementById('files').onchange=async e=>{const files=[...e.target.files];")
                    .append("let uploaded=0,skipped=0,failed=0,cancelled=false,replaceAll=false,skipAll=false;")
                        .append("for(let i=0;i<files.length;i++){const f=files[i];statusBox.textContent=`Uploading ${i+1}/${files.length}: ${f.name}`;")
                    .append("try{let u='/upload?directory='+encodeURIComponent(document.body.dataset.directory)+'&name='+encodeURIComponent(f.name);let r=await fetch(u+(replaceAll?'&overwrite=1':''),{method:'PUT',body:f});")
                    .append("if(r.status===409){let choice=skipAll?'skip':replaceAll?'replace':await askUploadConflict(f.name);if(choice==='cancel'){cancelled=true;break;}if(choice==='skip'){skipped++;continue;}if(choice==='skip-all'){skipAll=true;skipped++;continue;}if(choice==='replace-all')replaceAll=true;r=await fetch(u+'&overwrite=1',{method:'PUT',body:f});}")
                    .append("if(!r.ok){failed++;statusBox.textContent=`${f.name}: ${await r.text()}`;}else{uploaded++;statusBox.textContent=`Uploaded ${uploaded}/${files.length}`;}")
                    .append("}catch(error){failed++;statusBox.textContent=`Upload failed: ${f.name}`;}}")
                    .append("e.target.value='';statusBox.textContent=`Upload finished: ${uploaded} uploaded, ${skipped} skipped, ${failed} failed${cancelled?' (remaining files cancelled)':''}. Refreshing...`;")
                        .append("setTimeout(()=>location.reload(),1000);};");
            }
            page.append("</script></main></body></html>");
            return page.toString();
        }

        private Response download(IHTTPSession session) throws IOException {
            String relative = parameter(session, "path");
            File file = resolve(relative);
            if (file == null || !file.isFile() || !file.canRead()) {
                return text(Response.Status.NOT_FOUND, "File not found");
            }
            Response response = newFixedLengthResponse(Response.Status.OK,
                    mimeType(file), new BufferedInputStream(new FileInputStream(file)), file.length());
            response.addHeader("Content-Disposition", "attachment; filename=\""
                    + safeHeaderFilename(file.getName()) + "\"");
            addSecurityHeaders(response);
            return response;
        }

        private Response downloadZip(IHTTPSession session) throws IOException {
            List<String> paths = session.getParameters().get("path");
            if (paths == null || paths.isEmpty()) {
                return text(Response.Status.BAD_REQUEST, "No files selected");
            }
            List<File> files = new ArrayList<>();
            for (String path : paths) {
                File file = resolve(path);
                if (file == null || !file.exists() || !file.canRead()) {
                    return text(Response.Status.NOT_FOUND, "Selected item is unavailable");
                }
                files.add(file);
            }
            PipedInputStream input = new PipedInputStream(64 * 1024);
            PipedOutputStream output = new PipedOutputStream(input);
            Thread writer = new Thread(() -> {
                try (ZipOutputStream zip = new ZipOutputStream(
                        new BufferedOutputStream(output))) {
                    Set<String> visited = new HashSet<>();
                    for (File file : files) {
                        String entryName = rootIsDirectory
                                ? relativePath(file) : file.getName();
                        addToZip(zip, file, entryName, visited);
                    }
                } catch (IOException ignored) {
                    try {
                        output.close();
                    } catch (IOException ignoredClose) {
                    }
                }
            }, "http-share-zip");
            writer.setDaemon(true);
            writer.start();
            Response response = newChunkedResponse(Response.Status.OK, "application/zip", input);
            response.addHeader("Content-Disposition", "attachment; filename=\"MFIle-Manager-download.zip\"");
            addSecurityHeaders(response);
            return response;
        }

        private void addToZip(ZipOutputStream zip, File file, String entryName,
                              Set<String> visited) throws IOException {
            File safe = file.getCanonicalFile();
            if (!isInsideRoot(safe) || !visited.add(safe.getPath())) {
                return;
            }
            if (safe.isDirectory()) {
                String directoryName = entryName.endsWith("/") ? entryName : entryName + "/";
                zip.putNextEntry(new ZipEntry(directoryName));
                zip.closeEntry();
                File[] children = safe.listFiles();
                if (children == null) {
                    return;
                }
                Arrays.sort(children, Comparator.comparing(File::getName,
                        String.CASE_INSENSITIVE_ORDER));
                for (File child : children) {
                    addToZip(zip, child, directoryName + child.getName(), visited);
                }
            } else if (safe.isFile()) {
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream stream = new BufferedInputStream(new FileInputStream(safe))) {
                    byte[] buffer = new byte[32 * 1024];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        zip.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }

        private Response deleteSelected(IHTTPSession session) throws IOException {
            if (!rootIsDirectory) {
                return text(Response.Status.FORBIDDEN, "A shared single file cannot be deleted here");
            }
            List<String> paths = session.getParameters().get("path");
            if (paths == null || paths.isEmpty()) {
                return text(Response.Status.BAD_REQUEST, "No items selected");
            }

            List<File> requested = new ArrayList<>();
            for (String path : paths) {
                File target = resolve(path);
                if (target == null || !target.exists()) {
                    return text(Response.Status.NOT_FOUND, "A selected item is unavailable");
                }
                if (target.getCanonicalPath().equals(canonicalRoot)) {
                    return text(Response.Status.FORBIDDEN, "The shared root cannot be deleted");
                }
                if (!target.canWrite()) {
                    return text(Response.Status.FORBIDDEN, "A selected item cannot be deleted");
                }
                requested.add(target);
            }

            requested.sort(Comparator.comparingInt(file -> file.getAbsolutePath().length()));
            List<File> targets = new ArrayList<>();
            List<String> targetPaths = new ArrayList<>();
            for (File candidate : requested) {
                String candidatePath = candidate.getCanonicalPath();
                boolean coveredByParent = false;
                for (String acceptedPath : targetPaths) {
                    if (candidatePath.equals(acceptedPath)
                            || candidatePath.startsWith(acceptedPath + File.separator)) {
                        coveredByParent = true;
                        break;
                    }
                }
                if (!coveredByParent) {
                    targets.add(candidate);
                    targetPaths.add(candidatePath);
                }
            }

            int deleted = 0;
            int failed = 0;
            for (File target : targets) {
                if (deleteRecursively(target)) {
                    deleted++;
                } else {
                    failed++;
                }
            }
            if (failed > 0) {
                return text(Response.Status.INTERNAL_ERROR,
                        "Deleted " + deleted + " item(s); " + failed + " could not be deleted");
            }
            return text(Response.Status.OK, "Deleted " + deleted + " item(s)");
        }

        private boolean deleteRecursively(File file) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children == null) {
                    return false;
                }
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
            return file.delete();
        }

        private Response upload(IHTTPSession session) throws IOException {
            String directoryPath = parameter(session, "directory");
            String name = parameter(session, "name");
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)
                    || name.contains("/") || name.contains("\\") || name.indexOf('\0') >= 0) {
                return text(Response.Status.BAD_REQUEST, "Invalid file name");
            }
            File directory = resolve(directoryPath);
            if (directory == null || !directory.isDirectory() || !directory.canWrite()) {
                return text(Response.Status.FORBIDDEN, "Upload folder is unavailable");
            }
            long contentLength;
            try {
                contentLength = Long.parseLong(session.getHeaders().get("content-length"));
            } catch (RuntimeException exception) {
                return text(Response.Status.LENGTH_REQUIRED, "Content-Length is required");
            }
            if (contentLength < 0 || contentLength > 4L * 1024L * 1024L * 1024L) {
                return text(Response.Status.PAYLOAD_TOO_LARGE, "Upload is too large");
            }

            File destination = new File(directory, name).getCanonicalFile();
            if (!isInsideRoot(destination)) {
                return text(Response.Status.FORBIDDEN, "Upload path is outside the shared item");
            }
            boolean overwrite = "1".equals(parameter(session, "overwrite"));

            File temporary = new File(directory, ".upload-" + UUID.randomUUID());
            boolean written = false;
            try (InputStream input = session.getInputStream();
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
                byte[] buffer = new byte[32 * 1024];
                long remaining = contentLength;
                while (remaining > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) {
                        throw new IOException("Upload ended before Content-Length");
                    }
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
                written = true;
            } finally {
                if (!written) {
                    temporary.delete();
                }
            }

            int saveResult = replaceAtomically(temporary, destination, overwrite);
            if (saveResult == 1) {
                temporary.delete();
                return text(Response.Status.CONFLICT, "A file with this name already exists");
            }
            if (saveResult != 0) {
                temporary.delete();
                return text(Response.Status.INTERNAL_ERROR, "Could not save uploaded file");
            }
            return text(Response.Status.CREATED, "Uploaded " + escapeHtml(name));
        }

        private synchronized int replaceAtomically(File temporary, File destination, boolean overwrite) {
            if (destination.isDirectory() || (destination.exists() && !overwrite)) {
                return 1;
            }
            if (!destination.exists()) {
                return temporary.renameTo(destination) ? 0 : 2;
            }
            if (!overwrite || !destination.isFile()) {
                return 1;
            }
            File backup = new File(destination.getParentFile(), ".backup-" + UUID.randomUUID());
            if (!destination.renameTo(backup)) {
                return 2;
            }
            if (temporary.renameTo(destination)) {
                backup.delete();
                return 0;
            }
            backup.renameTo(destination);
            return 2;
        }

        private File resolve(String relative) throws IOException {
            if (!rootIsDirectory) {
                if (relative == null || relative.isEmpty() || root.getName().equals(relative)) {
                    return root;
                }
                return null;
            }
            String normalized = relative == null ? "" : relative.replace('\\', '/');
            File candidate = normalized.isEmpty() ? root : new File(root, normalized);
            File canonical = candidate.getCanonicalFile();
            return isInsideRoot(canonical) ? canonical : null;
        }

        private boolean isInsideRoot(File file) throws IOException {
            String path = file.getCanonicalPath();
            return path.equals(canonicalRoot)
                    || (rootIsDirectory && path.startsWith(canonicalRoot + File.separator));
        }

        private String relativePath(File file) throws IOException {
            String path = file.getCanonicalPath();
            if (path.equals(canonicalRoot)) {
                return file.getName();
            }
            return path.substring(canonicalRoot.length() + 1).replace(File.separatorChar, '/');
        }

        private String parameter(IHTTPSession session, String name) {
            List<String> values = session.getParameters().get(name);
            return values == null || values.isEmpty() ? "" : values.get(0);
        }

        private Response html(Response.IStatus status, String content) {
            Response response = newFixedLengthResponse(status, "text/html; charset=utf-8", content);
            addSecurityHeaders(response);
            return response;
        }

        private Response text(Response.IStatus status, String content) {
            Response response = newFixedLengthResponse(status, "text/plain; charset=utf-8", content);
            addSecurityHeaders(response);
            return response;
        }

        private void addSecurityHeaders(Response response) {
            response.addHeader("X-Content-Type-Options", "nosniff");
            response.addHeader("X-Frame-Options", "DENY");
            response.addHeader("Cache-Control", "no-store");
            response.addHeader("Content-Security-Policy",
                    "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:");
        }

        private String mimeType(File file) {
            String guessed = java.net.URLConnection.guessContentTypeFromName(file.getName());
            return guessed == null ? "application/octet-stream" : guessed;
        }

        private String formatSize(long size) {
            if (size < 1024) {
                return size + " B";
            }
            if (size < 1024L * 1024L) {
                return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
            }
            return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0));
        }

        private String parentPath(String relative) {
            int separator = relative.lastIndexOf('/');
            return separator < 0 ? "" : relative.substring(0, separator);
        }

        private String urlEncode(String value) {
            try {
                return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
            } catch (java.io.UnsupportedEncodingException exception) {
                return "";
            }
        }

        private String escapeHtml(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;").replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }

        private String safeHeaderFilename(String name) {
            return name.replace("\\", "_").replace("\"", "_")
                    .replace("\r", "_").replace("\n", "_");
        }
    }
}
