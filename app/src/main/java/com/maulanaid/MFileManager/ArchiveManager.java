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

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ArchiveManager {
    public enum Format {
        ZIP(".zip"),
        SEVEN_ZIP(".7z"),
        TAR(".tar"),
        TAR_GZ(".tar.gz"),
        GZIP(".gz");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    public static final class EntryInfo {
        public final String name;
        public final long unpackedSize;
        public final long packedSize;
        public final boolean directory;

        EntryInfo(String name, long unpackedSize, long packedSize, boolean directory) {
            this.name = name;
            this.unpackedSize = unpackedSize;
            this.packedSize = packedSize;
            this.directory = directory;
        }
    }

    public static final class Result {
        public final long originalBytes;
        public final long archiveBytes;
        public final int fileCount;

        Result(long originalBytes, long archiveBytes, int fileCount) {
            this.originalBytes = originalBytes;
            this.archiveBytes = archiveBytes;
            this.fileCount = fileCount;
        }

        public double savedPercent() {
            if (originalBytes == 0) {
                return 0;
            }
            return (1.0 - (double) archiveBytes / originalBytes) * 100.0;
        }
    }

    private static final int BUFFER_SIZE = 32 * 1024;

    private ArchiveManager() {
    }

    public static boolean isSupportedArchive(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".7z") || name.endsWith(".rar")
                || name.endsWith(".tar") || name.endsWith(".tar.gz")
                || name.endsWith(".tgz") || name.endsWith(".gz");
    }

    public static Format detectOutputFormat(String extension) {
        for (Format format : Format.values()) {
            if (format.extension.equalsIgnoreCase(extension)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported archive format");
    }

    public static String removeArchiveExtension(File archive) {
        String name = archive.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        String[] extensions = { ".tar.gz", ".tgz", ".tar", ".zip", ".7z", ".rar", ".gz" };
        for (String extension : extensions) {
            if (lower.endsWith(extension)) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name;
    }

    public static List<EntryInfo> listEntries(File archive, char[] password) throws Exception {
        String name = archive.getName().toLowerCase(Locale.ROOT);
        List<EntryInfo> entries = new ArrayList<>();
        if (name.endsWith(".zip")) {
            try (ZipFile zip = password == null || password.length == 0
                    ? new ZipFile(archive)
                    : new ZipFile(archive, password)) {
                for (net.lingala.zip4j.model.FileHeader header : zip.getFileHeaders()) {
                    entries.add(new EntryInfo(header.getFileName(), header.getUncompressedSize(),
                            header.getCompressedSize(), header.isDirectory()));
                }
            }
            return entries;
        }
        if (name.endsWith(".7z")) {
            try (SevenZFile sevenZ = password == null || password.length == 0
                    ? new SevenZFile(archive)
                    : new SevenZFile(archive, password)) {
                for (SevenZArchiveEntry entry : sevenZ.getEntries()) {
                    entries.add(new EntryInfo(entry.getName(), entry.getSize(),
                            -1, entry.isDirectory()));
                }
            }
            return entries;
        }
        if (name.endsWith(".rar")) {
            try (Archive rar = password == null || password.length == 0
                    ? new Archive(archive)
                    : new Archive(archive, new String(password))) {
                for (FileHeader header : rar.getFileHeaders()) {
                    entries.add(new EntryInfo(header.getFileName(), header.getFullUnpackSize(),
                            header.getFullPackSize(), header.isDirectory()));
                }
            }
            return entries;
        }
        if (name.endsWith(".gz") && !name.endsWith(".tar.gz") && !name.endsWith(".tgz")) {
            long unpacked = 0;
            String originalName = removeArchiveExtension(archive);
            try (InputStream input = new GzipCompressorInputStream(
                    new BufferedInputStream(new FileInputStream(archive)), true)) {
                String storedName = ((GzipCompressorInputStream) input).getMetaData().getFilename();
                if (storedName != null && !storedName.isEmpty()) {
                    originalName = storedName;
                }
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    unpacked += count;
                }
            }
            entries.add(new EntryInfo(originalName, unpacked,
                    archive.length(), false));
            return entries;
        }

        try (TarArchiveInputStream tar = openTarInput(archive, name.endsWith(".tar.gz")
                || name.endsWith(".tgz"))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                entries.add(new EntryInfo(entry.getName(), entry.getSize(), -1,
                        entry.isDirectory()));
            }
        }
        return entries;
    }

    public static Result create(List<File> sources, File destination, Format format,
            char[] password) throws Exception {
        if (sources.isEmpty()) {
            throw new IOException("Select at least one file or folder");
        }
        if (format == Format.GZIP && (sources.size() != 1 || !sources.get(0).isFile())) {
            throw new IOException("GZIP can only compress one file");
        }
        boolean hasPassword = password != null && password.length > 0;
        if (hasPassword && format != Format.ZIP && format != Format.SEVEN_ZIP) {
            throw new IOException("Only ZIP and 7z support password protection");
        }

        File parent = destination.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.isDirectory() || !parent.canWrite()) {
            throw new IOException("Destination folder cannot be written");
        }
        if (destination.exists()) {
            throw new IOException("An item with this name already exists");
        }
        String destinationPath = destination.getCanonicalPath();
        List<SourceEntry> entries = collectSources(sources, destinationPath);
        long originalBytes = 0;
        int fileCount = 0;
        for (SourceEntry entry : entries) {
            if (!entry.file.isDirectory()) {
                originalBytes += entry.file.length();
                fileCount++;
            }
        }

        File temporary = File.createTempFile(".mfile-archive-", ".tmp", parent);
        boolean completed = false;
        try {
            switch (format) {
                case ZIP:
                    writeZip(entries, temporary, password);
                    break;
                case SEVEN_ZIP:
                    writeSevenZip(entries, temporary, password);
                    break;
                case TAR:
                    writeTar(entries, temporary, false);
                    break;
                case TAR_GZ:
                    writeTar(entries, temporary, true);
                    break;
                case GZIP:
                    writeGzip(sources.get(0), temporary);
                    break;
                default:
                    throw new IOException("Unsupported archive format");
            }
            if (!temporary.renameTo(destination)) {
                throw new IOException("Could not save archive");
            }
            completed = true;
            return new Result(originalBytes, destination.length(), fileCount);
        } finally {
            if (!completed) {
                temporary.delete();
            }
        }
    }

    public static int extract(File archive, File destination, char[] password) throws Exception {
        if (!isSupportedArchive(archive)) {
            throw new IOException("Unsupported archive format");
        }
        if (destination.exists() || !destination.mkdirs()) {
            throw new IOException("Extraction folder already exists or cannot be created");
        }
        try {
            String name = archive.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".zip")) {
                return extractZip(archive, destination, password);
            }
            if (name.endsWith(".7z")) {
                return extractSevenZip(archive, destination, password);
            }
            if (name.endsWith(".rar")) {
                return extractRar(archive, destination, password);
            }
            if (name.endsWith(".gz") && !name.endsWith(".tar.gz") && !name.endsWith(".tgz")) {
                extractGzip(archive, destination);
                return 1;
            }
            return extractTar(archive, destination,
                    name.endsWith(".tar.gz") || name.endsWith(".tgz"));
        } catch (Exception exception) {
            deleteRecursively(destination);
            throw exception;
        }
    }

    private static int extractZip(File archive, File destination, char[] password) throws Exception {
        int count = 0;
        try (ZipFile zip = password == null || password.length == 0
                ? new ZipFile(archive)
                : new ZipFile(archive, password)) {
            for (net.lingala.zip4j.model.FileHeader header : zip.getFileHeaders()) {
                File target = safeTarget(destination, header.getFileName());
                if (header.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("Could not create extracted folder");
                    }
                    continue;
                }
                ensureParent(target);
                try (InputStream input = zip.getInputStream(header);
                        OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    copy(input, output);
                }
                count++;
            }
        }
        return count;
    }

    private static int extractSevenZip(File archive, File destination, char[] password)
            throws IOException {
        int count = 0;
        try (SevenZFile sevenZ = password == null || password.length == 0
                ? new SevenZFile(archive)
                : new SevenZFile(archive, password)) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZ.getNextEntry()) != null) {
                File target = safeTarget(destination, entry.getName());
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("Could not create extracted folder");
                    }
                    continue;
                }
                ensureParent(target);
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = sevenZ.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
                count++;
            }
        }
        return count;
    }

    private static int extractRar(File archive, File destination, char[] password) throws Exception {
        int count = 0;
        try (Archive rar = password == null || password.length == 0
                ? new Archive(archive)
                : new Archive(archive, new String(password))) {
            for (FileHeader header : rar.getFileHeaders()) {
                File target = safeTarget(destination, header.getFileName());
                if (header.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("Could not create extracted folder");
                    }
                    continue;
                }
                ensureParent(target);
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    rar.extractFile(header, output);
                }
                count++;
            }
        }
        return count;
    }

    private static int extractTar(File archive, File destination, boolean gzip) throws IOException {
        int count = 0;
        try (TarArchiveInputStream tar = openTarInput(archive, gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                File target = safeTarget(destination, entry.getName());
                if (entry.isSymbolicLink() || entry.isLink()) {
                    continue;
                }
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("Could not create extracted folder");
                    }
                    continue;
                }
                if (!entry.isFile()) {
                    continue;
                }
                ensureParent(target);
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    copy(tar, output);
                }
                count++;
            }
        }
        return count;
    }

    private static void extractGzip(File archive, File destination) throws IOException {
        try (InputStream input = new GzipCompressorInputStream(
                new BufferedInputStream(new FileInputStream(archive)), true)) {
            String storedName = ((GzipCompressorInputStream) input).getMetaData().getFilename();
            String outputName = storedName == null || storedName.isEmpty()
                    ? removeArchiveExtension(archive)
                    : storedName;
            File target = safeTarget(destination, outputName);
            ensureParent(target);
            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                copy(input, output);
            }
        }
    }

    private static void writeZip(List<SourceEntry> entries, File destination, char[] password)
            throws IOException {
        OutputStream fileOutput = new BufferedOutputStream(new FileOutputStream(destination));
        try (ZipOutputStream zip = password == null || password.length == 0
                ? new ZipOutputStream(fileOutput)
                : new ZipOutputStream(fileOutput, password)) {
            for (SourceEntry source : entries) {
                boolean directory = source.file.isDirectory();
                ZipParameters parameters = new ZipParameters();
                parameters.setFileNameInZip(directory && !source.name.endsWith("/")
                        ? source.name + "/"
                        : source.name);
                parameters.setCompressionMethod(CompressionMethod.DEFLATE);
                parameters.setCompressionLevel(CompressionLevel.NORMAL);
                parameters.setLastModifiedFileTime(source.file.lastModified());
                parameters.setEncryptFiles(!directory && password != null && password.length > 0);
                if (parameters.isEncryptFiles()) {
                    parameters.setEncryptionMethod(EncryptionMethod.AES);
                    parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                }
                zip.putNextEntry(parameters);
                if (!directory) {
                    try (InputStream input = new BufferedInputStream(new FileInputStream(source.file))) {
                        copy(input, zip);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void writeSevenZip(List<SourceEntry> entries, File destination, char[] password)
            throws IOException {
        try (SevenZOutputFile sevenZ = password == null || password.length == 0
                ? new SevenZOutputFile(destination)
                : new SevenZOutputFile(destination, password)) {
            for (SourceEntry source : entries) {
                SevenZArchiveEntry entry = sevenZ.createArchiveEntry(source.file, source.name);
                sevenZ.putArchiveEntry(entry);
                if (!source.file.isDirectory()) {
                    try (InputStream input = new BufferedInputStream(new FileInputStream(source.file))) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            sevenZ.write(buffer, 0, read);
                        }
                    }
                }
                sevenZ.closeArchiveEntry();
            }
        }
    }

    private static void writeTar(List<SourceEntry> entries, File destination, boolean gzip)
            throws IOException {
        OutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
        if (gzip) {
            output = new GzipCompressorOutputStream(output);
        }
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            for (SourceEntry source : entries) {
                TarArchiveEntry entry = tar.createArchiveEntry(source.file, source.name);
                tar.putArchiveEntry(entry);
                if (!source.file.isDirectory()) {
                    try (InputStream input = new BufferedInputStream(new FileInputStream(source.file))) {
                        copy(input, tar);
                    }
                }
                tar.closeArchiveEntry();
            }
        }
    }

    private static void writeGzip(File source, File destination) throws IOException {
        GzipParameters parameters = new GzipParameters();
        parameters.setFilename(source.getName());
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
                OutputStream output = new GzipCompressorOutputStream(
                        new BufferedOutputStream(new FileOutputStream(destination)), parameters)) {
            copy(input, output);
        }
    }

    private static List<SourceEntry> collectSources(List<File> sources, String outputPath)
            throws IOException {
        List<SourceEntry> entries = new ArrayList<>();
        for (File source : sources) {
            if (!source.exists() || (!source.isDirectory() && !source.isFile())) {
                throw new IOException("A selected item is unavailable");
            }
            String canonical = source.getCanonicalPath();
            if (source.isDirectory() && outputPath.startsWith(canonical + File.separator)) {
                throw new IOException("Archive destination cannot be inside a selected folder");
            }
            addSourceEntries(source, source.getName(), outputPath, entries);
        }
        return entries;
    }

    private static void addSourceEntries(File file, String entryName, String outputPath,
            List<SourceEntry> entries) throws IOException {
        File canonical = file.getCanonicalFile();
        if (!canonical.equals(file.getAbsoluteFile())) {
            return;
        }
        if (canonical.getPath().equals(outputPath)) {
            return;
        }
        if (file.isDirectory()) {
            entries.add(new SourceEntry(file, entryName.endsWith("/") ? entryName : entryName + "/"));
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Could not read folder: " + file.getName());
            }
            Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File child : children) {
                addSourceEntries(child, entryName + "/" + child.getName(), outputPath, entries);
            }
        } else if (file.isFile()) {
            entries.add(new SourceEntry(file, entryName));
        }
    }

    private static TarArchiveInputStream openTarInput(File archive, boolean gzip) throws IOException {
        InputStream input = new BufferedInputStream(new FileInputStream(archive));
        if (gzip) {
            input = new GzipCompressorInputStream(input, true);
        }
        return new TarArchiveInputStream(input);
    }

    private static File safeTarget(File destination, String entryName) throws IOException {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IOException("Archive contains an absolute path");
        }
        File root = destination.getCanonicalFile();
        File target = new File(root, normalized).getCanonicalFile();
        String rootPath = root.getPath();
        if (!target.getPath().equals(rootPath)
                && !target.getPath().startsWith(rootPath + File.separator)) {
            throw new IOException("Archive entry escapes the extraction folder");
        }
        return target;
    }

    private static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create extraction folder");
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static final class SourceEntry {
        final File file;
        final String name;

        SourceEntry(File file, String name) {
            this.file = file;
            this.name = name;
        }
    }
}
