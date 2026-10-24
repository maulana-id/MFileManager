package com.maulana.MFileManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ArchiveManagerTest {
    private File workDirectory;
    private File sourceDirectory;
    private File sourceFile;
    private static final String CONTENT = "Archive round-trip test data\n";

    @Before
    public void setUp() throws Exception {
        workDirectory = Files.createTempDirectory("mfile-archive-test").toFile();
        sourceDirectory = new File(workDirectory, "source");
        assertTrue(sourceDirectory.mkdir());
        File nested = new File(sourceDirectory, "nested");
        assertTrue(nested.mkdir());
        sourceFile = new File(nested, "sample.txt");
        Files.write(sourceFile.toPath(), CONTENT.getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() {
        deleteRecursively(workDirectory);
    }

    @Test
    public void roundTripsSupportedCreationFormats() throws Exception {
        List<ArchiveManager.Format> formats = Arrays.asList(
                ArchiveManager.Format.ZIP,
                ArchiveManager.Format.SEVEN_ZIP,
                ArchiveManager.Format.TAR,
                ArchiveManager.Format.TAR_GZ,
                ArchiveManager.Format.GZIP);

        for (ArchiveManager.Format format : formats) {
            File archive = new File(workDirectory, format.name() + format.extension());
            List<File> sources = format == ArchiveManager.Format.GZIP
                    ? Arrays.asList(sourceFile) : Arrays.asList(sourceDirectory);
            ArchiveManager.Result result = ArchiveManager.create(sources, archive, format, null);

            assertTrue(archive.isFile());
            assertEquals(format == ArchiveManager.Format.GZIP ? 1 : 1, result.fileCount);
            assertTrue(result.archiveBytes > 0);

            File extraction = new File(workDirectory, "extract-" + format.name());
            ArchiveManager.extract(archive, extraction, null);
            File extractedFile = format == ArchiveManager.Format.GZIP
                    ? new File(extraction, sourceFile.getName())
                    : new File(extraction, "source/nested/sample.txt");
            assertTrue("Missing extracted file for " + format, extractedFile.isFile());
            assertEquals(CONTENT, new String(Files.readAllBytes(extractedFile.toPath()),
                    StandardCharsets.UTF_8));
        }
    }

    @Test
    public void roundTripsPasswordProtectedZipAndSevenZip() throws Exception {
        char[] password = "correct horse battery staple".toCharArray();
        for (ArchiveManager.Format format : Arrays.asList(
                ArchiveManager.Format.ZIP, ArchiveManager.Format.SEVEN_ZIP)) {
            File archive = new File(workDirectory, "protected" + format.extension());
            ArchiveManager.create(Arrays.asList(sourceDirectory), archive, format, password);
            File extraction = new File(workDirectory, "protected-extract-" + format.name());
            ArchiveManager.extract(archive, extraction, password);
            File extractedFile = new File(extraction, "source/nested/sample.txt");
            assertEquals(CONTENT, new String(Files.readAllBytes(extractedFile.toPath()),
                    StandardCharsets.UTF_8));
        }
        Arrays.fill(password, '\0');
    }

    @Test
    public void rejectsZipSlipEntries() throws Exception {
        File archive = new File(workDirectory, "traversal.zip");
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                new FileOutputStream(archive))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("../escape.txt"));
            zip.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        File extraction = new File(workDirectory, "traversal-extract");
        try {
            ArchiveManager.extract(archive, extraction, null);
            fail("Expected traversal entry to be rejected");
        } catch (java.io.IOException expected) {
            assertFalse(new File(workDirectory, "escape.txt").exists());
            assertFalse(extraction.exists());
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}