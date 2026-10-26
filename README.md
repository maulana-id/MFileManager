# MFIle Manager

Android application ID: `com.maulanaid.MFileManager`.

A simple Android file manager for browsing shared storage and performing basic file operations.

## Features

- Browse folders and files, with folders listed first.
- Use icon-only Home and Up controls to return to the starting storage folder or its parent.
- Enter an absolute or current-folder-relative path to jump directly to a directory.
- Search names in the current folder.
- Group results by file type or modified date, or show one ungrouped list.
- Sort by name, modified date, or extension, with an optional folders-first setting.
- Select multiple files and folders to copy, paste, or delete them together.
- Create folders and empty files, rename single items, and inspect item properties.
- Choose Replace, Skip, Replace all, Skip all, or Cancel for paste conflicts. Replacing a folder overwrites matching items and keeps unrelated destination files.
- Browse, create, and extract common archives, including password-protected ZIP and 7z files.
- Open files with a compatible installed app through Android's app chooser.
- Identify folders and common file types by icon; image, video, and PDF files show small generated previews when supported.
- Confirm before recursively deleting selected files or folders.
- Switch between light and dark themes.
- Request storage access according to the Android version.

## First-Run Acknowledgement

On first launch, users must acknowledge how storage access and user-initiated file operations work before the app requests storage permission. The notice warns that deletion/replacement may be hard to undo, sharing exposes selected files to authenticated devices on the local network, and HTTP/FTP do not encrypt traffic. Declining exits without requesting storage access. The acknowledgement is versioned so a future material change can require it again.

## Requirements

- JDK 17
- Android SDK Platform 34
- Android SDK Build-Tools 34.0.0 (installed automatically by Android Studio if needed)
- Android Studio, or a terminal with the Android SDK configured

The Gradle Wrapper downloads the project's Gradle version (9.7.1) the first time it is run. The Android Gradle Plugin version is declared in the root `build.gradle`.

## Set Up the Workspace

### Android Studio

1. Clone or download this repository.
2. Open the project root, the directory containing `settings.gradle`.
3. Install JDK 17 and Android SDK Platform 34 if Android Studio prompts you.
4. Wait for Gradle sync to complete.
5. Select an emulator or connected device and run the `app` configuration.

### Command line or VS Code

Install JDK 17 and Android SDK Platform 34, then open the project root in your editor. Configure the Android SDK using `ANDROID_HOME`/`ANDROID_SDK_ROOT` or create a local `local.properties` file in the project root:

```properties
sdk.dir=/path/to/Android/Sdk
```

On Windows, use an escaped path, for example `sdk.dir=C\\:\\Users\\name\\AppData\\Local\\Android\\Sdk`, or set the SDK environment variable instead. `local.properties` is ignored by Git because SDK paths are specific to each workstation. Do not commit it.

The project can be built from VS Code's terminal with the Gradle Wrapper; Android Studio is recommended for emulator management and visual resource editing.

## Build

From the project root, use the Gradle Wrapper. On Linux or macOS:

```sh
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

To build only the installable debug APK:

```sh
./gradlew :app:assembleDebug
```

Debug APKs are written under `app/build/outputs/apk/debug/`:

- `MFIle-Manager-universal-debug.apk` works across the ABIs packaged in the app and is generally the easiest one to install.
- `MFIle-Manager-arm64-v8a-debug.apk` and `MFIle-Manager-x86_64-debug.apk` target their named CPU architectures.

Debug APKs are signed with the standard debug key and can be installed for local testing. For example, with Android Platform-Tools installed and a device connected with USB debugging enabled:

```sh
adb install -r app/build/outputs/apk/debug/MFIle-Manager-universal-debug.apk
```

To produce release outputs:

```sh
./gradlew :app:assembleRelease
```

Release signing is not configured in this repository yet. These outputs have `-unsigned` in their filenames and cannot be installed as-is. For distribution, create a signed APK or App Bundle using Android Studio's **Generate Signed Bundle / APK** flow, or configure Gradle release signing with a protected keystore. Never commit keystores or signing passwords.

## App Versioning

The app's version is managed in the root `gradle.properties` file:

```properties
appVersionCode=1
appVersionName=1.0
```

- Increase `appVersionCode` for every release uploaded or distributed as an update. It must be an integer greater than the code in the previous release.
- Set `appVersionName` to the human-readable version shown to users, such as `1.1` or `2.0.0`.

The Android module reads these properties in `app/build.gradle`. Keep `versionCode` increasing even if you reuse or change the displayed `versionName`.

## App Icon

The launcher icon is referenced by the `<application>` element in `app/src/main/AndroidManifest.xml`.

- `app/src/main/res/drawable/ic_launcher_foreground.xml` contains the foreground artwork.
- `app/src/main/res/values/colors.xml` defines the launcher background color (`ic_launcher_background`).
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` provides the one-color icon used by supported themed launchers.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` define adaptive icons for Android 8.0 and newer.
- `app/src/main/res/mipmap/ic_launcher.xml` and `ic_launcher_round.xml` provide a fallback for older Android versions.

Edit the foreground vector and background color to update the current icon. Keep important artwork centered within the safe area so launcher masks do not clip it. If you prefer image files to vector XML, use Android Studio's **Image Asset** tool to import artwork and generate the required launcher resource variants; keep the manifest resource names (`ic_launcher` and `ic_launcher_round`) consistent.

## File Browser Settings

Open **Settings** in the top-right corner to configure the list:

- **Group by:** none, file type, or modified date.
- **Sort by:** name, modified date (newest first), or extension.
- **Folders first:** show directories before files.
- **Dark theme:** switch between light and dark appearance.

These settings are saved on the device. The search field filters the current folder by name. Use the path field and **Go** button to open an absolute path, or a relative path from the current folder. The **Up** button moves to the parent directory.

Tap a folder to open it or a file to open it with a compatible app. The **Create** toolbar control offers a new folder or empty file. The first long-press selects an item; long-press that selected item again to open the icon-and-label action menu. For a single file, **Open as...** lets you choose a MIME type such as text, audio, video, image, PDF, APK, archive, or any type. **Rename** and **Properties** are available for a single selected item. Once selection starts, tap rows to add or remove them; long-press an unselected row to add it, then long-press a selected row to open the menu. The menu includes copy, paste, delete, select-all, and clear-selection actions. Paste is also available from Settings when the list is empty. The selection indicator shows the current count.

The phone's Back button navigates to the parent directory. At the storage root it shows an exit prompt; double-press Back within two seconds to exit the app.

Rows show a format-specific icon, modified date, and file size. Images, videos, and PDFs generate small previews on a background executor; unsupported or unreadable files keep their format icon. Previews are cached in memory while the screen is open.

Pasting multiple items asks about each conflicting destination. Choose **Replace** or **Skip** for that item, **Replace all** or **Skip all** for the remaining conflicts, or **Cancel** to stop; any items already processed remain processed. Replacing a folder merges it, overwrites matching files, and keeps unrelated destination files. Deletion asks for confirmation because folders are removed recursively.

When opening a file, the app uses a read-only `FileProvider` content URI and Android's **Open with** chooser. The selected app must support that file's type. APK installation remains subject to Android's install-from-this-source security settings.

## Archive Support

Long-press selected files or folders and choose **Compress** to create ZIP, 7z, TAR, or TAR.GZ archives. GZIP is available for a single file. ZIP and 7z can be password-protected; TAR, TAR.GZ, and GZ do not provide encryption. The result dialog reports input size, archive size, and the compression ratio.

For ZIP, 7z, TAR, TAR.GZ, GZ, and RAR files, choose **Browse archive** to preview entry names and sizes or **Extract archive** to unpack into a new folder. Passwords can be supplied when browsing or extracting encrypted archives. Absolute and traversal paths are rejected during extraction.

RAR archives can be browsed and extracted, including password-protected archives supported by the reader, but creating RAR files is not supported. Use ZIP or 7z when you need to create password-protected archives.

## HTTP File Sharing

1. Configure **HTTP share login** from **Settings**. The username defaults to `MFile Manager`; usernames must be 1-64 printable ASCII characters and cannot contain a colon. Passwords must be at least 8 characters.
2. Long-press a file or folder and choose **Share this file/folder over HTTP**. Only one item can be shared at a time; starting another share replaces the active one after confirmation.
3. Open one of the displayed `http://<device-ip>:8080` addresses on another device connected to the same local network, then enter the configured username and password when prompted. Existing installs continue to use `MFile Manager` unless the username is changed in Settings.

The browser page follows the app's light/dark preference and checks for theme changes while open. Each entry shows its modified date and time, and files also show their size. This lets you verify timestamps after an upload replacement.

The browser page lets you browse within the shared folder, download individual files, select multiple items for a streaming ZIP download, upload multiple files, and delete selected items. For an existing upload name, choose **Replace** or **Skip** for that file, **Replace all** or **Skip all** for remaining conflicts, or **Cancel remaining**. New filenames still upload when Skip all is active. Replacements are written atomically. After an upload batch completes, the browser refreshes the listing. Deletion requires browser confirmation, is password-protected, and cannot remove the shared root. Sharing a single file is download-only. The persistent Android notification and the in-app status bar both provide a stop control; the status bar can also copy the share address.

The server runs as a foreground service on port `8080` and stops when you use either Stop control. If the port is already occupied, sharing will not start. Android 13 and newer require notification permission so the persistent stop action is available.

**Security:** The server requires Basic authentication with the configured username and password, checks that requested paths remain inside the selected share, and limits access to that file/folder. However, this is plain HTTP: credentials and file contents are not encrypted in transit. Use it only on a trusted private network; do not forward port 8080 to the public internet or use it on untrusted Wi-Fi.

## FTP File Sharing

1. Configure the shared username and password under **Settings → File share login**.
2. Long-press a folder and choose **Share folder over FTP (read-only)**. FTP shares folders only; single files can be shared with HTTP.
3. Connect from another device with an FTP client in **passive mode**, using an address shown in the in-app status, port `2121`, and the configured credentials.

FTP access is read-only: clients can list and download files, but cannot upload, overwrite, rename, or delete them. The server uses passive data ports `21210-21219`; allow TCP port `2121` and that passive range through the local network firewall. The in-app Stop control and the persistent FTP notification both stop the service.

**Security:** Standard FTP does not encrypt credentials or file data. Use FTP only on a trusted private network; never expose it to the public internet or use it on untrusted Wi-Fi.

## License

This application and its source code are proprietary and are not offered under a public source-code license. Google Play does not require you to publish the source or choose an open-source license to distribute an app.

Third-party libraries bundled in the app remain governed by their own licenses. Before publishing, include any required license/attribution notices in the app or its distribution materials and verify each dependency's terms. A closed-source project does not remove those obligations.

The app's **Settings → Open source licenses** screen displays dependency license information generated during the build. Review it against the final release APK and upstream notices, especially for components whose POM metadata omits a license entry.

## Google Play Readiness

As of October 2026, this project still targets API 34, below Google Play's API 36 target requirement for new apps and updates. `MANAGE_EXTERNAL_STORAGE` is a restricted permission requiring a Play Console declaration and approval; file-management is an eligible core-use category, but approval is not automatic. `REQUEST_INSTALL_PACKAGES` also needs an accurate declaration and strictly user-initiated installation. The HTTP and FTP sharing features transmit data without transport encryption and need clear Play listing, privacy-policy, and Data safety disclosures. Review current Play policy and permission declarations before submission.

## Android Storage Access

The app has a minimum SDK of 24 and targets SDK 34, configured in `app/build.gradle`.

- On Android 11 (API 30) and newer, the app requests the special **All files access** permission in system settings.
- On Android 10 and older, it requests the applicable external storage runtime permissions.

The app's storage permission is sensitive. Google Play distribution may require the permission to meet the Play policy for broad file access; review current Play policy before publishing.

## Project Layout

```text
.
├── app/
│   ├── build.gradle                 # Android app configuration and dependencies
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/                    # Application code
│       └── res/                     # Layouts, strings, themes, and launcher icons
├── gradle/wrapper/                  # Gradle Wrapper configuration
├── build.gradle                     # Plugin versions and root build tasks
├── gradle.properties                # Shared Gradle and app version properties
├── settings.gradle                  # Project and plugin repositories
└── gradlew                          # Wrapper for Linux and macOS
```

## Troubleshooting

- **`SDK location not found`:** install Android SDK Platform 34 and configure `ANDROID_HOME`/`ANDROID_SDK_ROOT` or `local.properties`.
- **Java or Gradle compatibility errors:** use JDK 17 for the Android Gradle Plugin in this project.
- **`Invalid app package` when installing a release:** check whether the file is an unsigned release APK. Install the signed universal debug APK for local testing, or sign a release build.
- **APK is incompatible with the device:** use `MFIle-Manager-universal-debug.apk`, or select the ABI-specific APK matching the device architecture.
- **APK install is blocked:** allow **Install unknown apps** for MFIle Manager when Android opens its settings screen. Android also rejects unsigned APKs; use a signed APK built for the device.
- **Storage list is empty or permission is denied:** grant the requested storage access in Android settings and reopen the app.
- **A path does not open:** use an existing readable directory path. Relative paths are resolved from the directory currently being browsed.
