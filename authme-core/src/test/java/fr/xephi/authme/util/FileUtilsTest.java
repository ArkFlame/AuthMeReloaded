package fr.xephi.authme.util;

import com.google.common.io.Files;
import fr.xephi.authme.TestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void shouldNotCopyFile() throws IOException {
        File file = new File(temporaryFolder, "config.yml");
        // purposely don't copy config.yml to verify that config.yml isn't copied by the method
        File emailJarFile = TestHelper.getJarFile("/email.html");
        Files.copy(emailJarFile, file);

        boolean result = FileUtils.copyFileFromResource(file, "config.yml");

        assertThat(result, equalTo(true));
        assertThat(file.length(), equalTo(emailJarFile.length()));
    }

    @Test
    void shouldCopyFileFromJar() {
        File file = new File(temporaryFolder, "some/folders/welcome.txt");

        boolean result = FileUtils.copyFileFromResource(file, "welcome.txt");

        assertThat(result, equalTo(true));
        assertThat(file.exists(), equalTo(true));
        File configJarFile = TestHelper.getJarFile("/welcome.txt");
        assertThat(file.length(), equalTo(configJarFile.length()));
    }

    @Test
    void shouldReturnFalseForInvalidJarFile() {
        File file = new File(temporaryFolder, "bogus");

        boolean result = FileUtils.copyFileFromResource(file, "does-not-exist");

        assertThat(result, equalTo(false));
        assertThat(file.exists(), equalTo(false));
    }

    @Test
    void shouldReturnFalseForParentInvalidParentFolders() throws IOException {
        new File(temporaryFolder, "hello").createNewFile();
        File fileToCreate = new File(temporaryFolder, "hello/test");

        boolean result = FileUtils.copyFileFromResource(fileToCreate, "welcome.txt");

        assertThat(result, equalTo(false));
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
    void shouldDoNothingForNullFile() {
        File file = null;

        FileUtils.delete(file);

        // Nothing happens
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

    @Test
    void shouldCreateDirectory() {
        File dir = new File(temporaryFolder, "folder/folder2/myFolder");

        boolean result = FileUtils.createDirectory(dir);

        assertThat(result, equalTo(true));
        assertThat(dir.exists(), equalTo(true));
        assertThat(dir.isDirectory(), equalTo(true));
    }

    @Test
    void shouldReturnFalseOnDirectoryCreateFail() throws IOException {
        File dirAsFile = new File(temporaryFolder, "file");
        dirAsFile.createNewFile();

        boolean result = FileUtils.createDirectory(dirAsFile);

        assertThat(result, equalTo(false));
        assertThat(dirAsFile.isFile(), equalTo(true));
    }

    @Test
    void shouldReturnFalseForNullDirectory() {
        assertThat(FileUtils.createDirectory(null), equalTo(false));
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
