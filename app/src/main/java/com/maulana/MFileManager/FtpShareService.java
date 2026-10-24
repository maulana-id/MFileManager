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
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.filesystem.nativefs.NativeFileSystemFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.Md5PasswordEncryptor;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class FtpShareService extends Service {
    public static final String ACTION_START = "com.maulana.MFileManager.FTP_SHARE_START";
    public static final String ACTION_STOP = "com.maulana.MFileManager.FTP_SHARE_STOP";
    public static final int PORT = 2121;
    public static final String PASSIVE_PORTS = "21210-21219";
    public static final String EXTRA_SHARED_PATH = "shared_path";
    public static final String ACTION_STATUS_CHANGED = HttpShareService.ACTION_STATUS_CHANGED;

    private static final String PREFS_NAME = "file_manager_preferences";
    private static final String PREF_USERNAME = "http_share_username";
    private static final String PREF_PASSWORD = "http_share_password";
    private static final String DEFAULT_USERNAME = "MFileManager";
    private static final String PREF_ACTIVE = "ftp_share_active";
    private static final String PREF_PATH = "ftp_share_path";
    private static final String PREF_URLS = "ftp_share_urls";
    private static final String PREF_ERROR = "ftp_share_error";
    private static final String CHANNEL_ID = "ftp_file_share";
    private static final int NOTIFICATION_ID = 2121;

    private FtpServer ftpServer;
    private File sharedRoot;
    private File userFile;

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

        startForegroundCompat(buildNotification("Starting FTP share..."));
        try {
            String path = intent.getStringExtra(EXTRA_SHARED_PATH);
            File candidate = path == null ? null : new File(path).getCanonicalFile();
            if (candidate == null || !candidate.isDirectory() || !candidate.canRead()) {
                throw new IOException("FTP sharing requires a readable folder");
            }
            stopServer();
            sharedRoot = candidate;
            SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            preferences.edit().remove(PREF_ERROR).apply();
            String username = preferences.getString(PREF_USERNAME, DEFAULT_USERNAME);
            String password = preferences.getString(PREF_PASSWORD, "");
            if (!isValidUsername(username) || password.length() < 8) {
                throw new IOException("Set valid file-share credentials first");
            }

            PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
            userFile = new File(getCacheDir(), "ftp-users.properties");
            if (!userFile.exists() && !userFile.createNewFile()) {
                throw new IOException("Could not create the FTP user file");
            }
            userManagerFactory.setFile(userFile);
            userManagerFactory.setAdminName("ftp-admin-disabled");
            userManagerFactory.setPasswordEncryptor(new Md5PasswordEncryptor());
            UserManager userManager = userManagerFactory.createUserManager();
            BaseUser user = new BaseUser();
            user.setName(username);
            user.setPassword(password);
            user.setHomeDirectory(sharedRoot.getCanonicalPath());
            user.setMaxIdleTime(300);
            user.setAuthorities(Collections.<Authority>emptyList());
            userManager.save(user);

            DataConnectionConfigurationFactory dataConfiguration =
                    new DataConnectionConfigurationFactory();
            dataConfiguration.setPassivePorts(PASSIVE_PORTS);
            dataConfiguration.setActiveEnabled(false);

            ListenerFactory listener = new ListenerFactory();
            listener.setPort(PORT);
            listener.setIdleTimeout(300);
            listener.setDataConnectionConfiguration(
                    dataConfiguration.createDataConnectionConfiguration());

            NativeFileSystemFactory fileSystem = new NativeFileSystemFactory();
            fileSystem.setCreateHome(false);

            FtpServerFactory serverFactory = new FtpServerFactory();
            serverFactory.setUserManager(userManager);
            serverFactory.setFileSystem(fileSystem);
            serverFactory.addListener("default", listener.createListener());
            ftpServer = serverFactory.createServer();
            ftpServer.start();

            String urls = getLocalUrls();
            preferences.edit()
                    .putBoolean(PREF_ACTIVE, true)
                    .putString(PREF_PATH, sharedRoot.getAbsolutePath())
                    .putString(PREF_URLS, urls)
                    .apply();
            startForegroundCompat(buildNotification("Read-only share: " + sharedRoot.getName()
                    + " on port " + PORT));
            notifyStatusChanged();
        } catch (Exception exception) {
            stopServer();
            clearShareState();
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(PREF_ERROR,
                            "Could not start FTP sharing. Check that ports 2121 and 21210-21219 are free.")
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
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(PREF_ERROR).apply();
        stopForeground(true);
        notifyStatusChanged();
    }

    private void stopServer() {
        if (ftpServer != null) {
            ftpServer.stop();
            ftpServer = null;
        }
        sharedRoot = null;
        if (userFile != null) {
            userFile.delete();
            userFile = null;
        }
    }

    private void clearShareState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_ACTIVE, false)
                .remove(PREF_PATH)
                .remove(PREF_URLS)
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
                    "FTP file sharing", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows when a folder is shared read-only over FTP");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String status) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 3, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, FtpShareService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 4, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_share)
                .setContentTitle("MFIle Manager FTP share")
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
                        addresses.add("ftp://" + address.getHostAddress() + ":" + PORT);
                    }
                }
            }
        } catch (IOException ignored) {
        }
        Collections.sort(addresses);
        if (addresses.isEmpty()) {
            return "ftp://<device-ip>:" + PORT;
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
}
