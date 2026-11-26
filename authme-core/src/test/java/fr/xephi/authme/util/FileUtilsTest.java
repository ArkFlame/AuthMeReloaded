package fr.xephi.authme.util;

import com.google.common.io.Files;
import fr.xephi.authme.TestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Test for {@link FileUtils}.
 */
class FileUtilsTest {

    /** Regex that matches timestamps such as 20180211_1048. */
    private static final String BACKUP_TIMESTAMP_PATTERN = "20\\d{6}_\\d{4}";

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @TempDir
    File temporaryFolder;

    @ParameterizedTest
    @CsvSource({
        "config.yml, config.yml, email.html, true",
        "some/folders/welcome.txt, welcome.txt, welcome.txt, false"})
    void shouldCopyFileFromJar(String target, String resource, String source, boolean copyExisting)
            throws IOException {
        File file = new File(temporaryFolder, target);
        File jarFile = TestHelper.getJarFile("/" + source);
        if (copyExisting) {
            // purposely don't copy a resource named like the target to verify it isn't copied
            Files.copy(jarFile, file);
        }

        boolean result = FileUtils.copyFileFromResource(file, resource);

        assertThat(result, equalTo(true));
        assertThat(file.length(), equalTo(jarFile.length()));
    }

    @ParameterizedTest
    @CsvSource({"bogus, does-not-exist, false", "hello/test, welcome.txt, true"})
    void shouldReturnFalseForInvalidCopy(String fileName, String resourceName,
                                         boolean blockedParent) throws IOException {
        if (blockedParent) {
            new File(temporaryFolder, "hello").createNewFile();
        }
        File file = new File(temporaryFolder, fileName);

        boolean result = FileUtils.copyFileFromResource(file, resourceName);

        assertThat(result, equalTo(false));
        assertThat(file.exists(), equalTo(false));
    }

    @Test
    void shouldPurgeDirectory() throws IOException {
        File file1 = new File(temporaryFolder, "a/b/c/test.html");
        File file2 = new File(temporaryFolder, "a/b/f/toast.txt");
        File file3 = new File(temporaryFolder, "a/g/rest.png");
        File file4 = new File(temporaryFolder, "j/l/roast.tiff");
        createFiles(file1, file2, file3, file4);

        FileUtils.purgeDirectory(new File(temporaryFolder, "a"));

        assertThat(file1.exists(), equalTo(false));
        assertThat(file2.exists(), equalTo(false));
        assertThat(file3.exists(), equalTo(false));
        assertThat(file4.exists(), equalTo(true));
        assertThat(new File(temporaryFolder, "a").exists(), equalTo(true));
    }

    @Test
    void shouldDeleteFile() throws IOException {
        File file = TestHelper.createFile(temporaryFolder, "tempFile");
        assertThat(file.exists(), equalTo(true));

        FileUtils.delete(file);

        assertThat(file.exists(), equalTo(false));
    }

    @Test
    void shouldHandleNullInputs() {
        FileUtils.delete(null);
        assertThat(FileUtils.createDirectory(null), equalTo(false));
    }

    @Test
    void shouldGetResourceFromJar() {
        assertThat(FileUtils.getResourceFromJar("config.yml"), not(nullValue()));
        assertThat(FileUtils.getResourceFromJar("does-not-exist"), nullValue());
    }

    @Test
    void shouldConstructPath() {
        String result = FileUtils.makePath("path", "to", "test-file.txt");

        assertThat(result, equalTo("path" + File.separator + "to" + File.separator + "test-file.txt"));
    }

    @ParameterizedTest
    @CsvSource({"folder/folder2/myFolder, false", "file, true"})
    void shouldCreateDirectory(String path, boolean preCreateFile) throws IOException {
        File dir = new File(temporaryFolder, path);
        if (preCreateFile) {
            dir.createNewFile();
        }

        boolean result = FileUtils.createDirectory(dir);

        assertThat(result, equalTo(!preCreateFile));
        assertThat(preCreateFile ? dir.isFile() : dir.isDirectory(), equalTo(true));
    }

    @Test
    void shouldCreateCurrentTimestampString() {
        String currentTimeString = FileUtils.createCurrentTimeString();

        assertThat(currentTimeString, matchesPattern(BACKUP_TIMESTAMP_PATTERN));
    }

    @Test
    void shouldCreateBackupFile() {
        File file = new File("some/folders/config.yml");

        String backupFile = FileUtils.createBackupFilePath(file);

        String folders = String.join(File.separator,"some", "folders", "").replace("\\", "\\\\");
        assertThat(backupFile, matchesPattern(folders + "backup_config_" + BACKUP_TIMESTAMP_PATTERN + "\\.yml"));
    }

    private static void createFiles(File... files) throws IOException {
        for (File file : files) {
            boolean result = file.getParentFile().mkdirs() & file.createNewFile();
            if (!result) {
                throw new IllegalStateException("Cannot create file '" + file + "'");
            }
        }
    }
}
