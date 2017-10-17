package org.jerkar.api.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.jerkar.api.utils.JkUtilsPath;
import org.jerkar.api.utils.JkUtilsPathTest;
import org.junit.Test;

@SuppressWarnings("javadoc")
public class JkFileTreeTest {

    @Test
    public void testFilesOnly() throws Exception {
        final URL sampleFileUrl = JkUtilsPathTest.class
                .getResource("samplefolder/subfolder/sample.txt");
        final Path source = Paths.get(sampleFileUrl.toURI()).getParent().getParent();
        Files.createDirectories(source.resolve("emptyfolder"));   // git won't copy empty dir

        final Path sampleFile = Paths.get(sampleFileUrl.toURI());
        assertTrue(Files.exists(sampleFile));
        final Path sampleFolder = sampleFile.getParent().getParent();

        System.out.println(JkFileTree.of(sampleFolder).relativeFiles());

        final JkFileTree subfolderTxt1 = JkFileTree.of(sampleFolder).include("/subfolder/*.txt");
        assertEquals(1, subfolderTxt1.files().size());
        System.out.println(subfolderTxt1);

        final JkFileTree subfolderTxt2 = JkFileTree.of(sampleFolder).include("subfolder/*.txt");
        assertEquals(1, subfolderTxt2.files().size());
    }

    @Test
    public void testRelativeFiles() throws Exception {
        final URL sampleFileUrl = JkUtilsPathTest.class
                .getResource("samplefolder/subfolder/sample.txt");
        final Path source = Paths.get(sampleFileUrl.toURI()).getParent().getParent();
        Files.createDirectories(source.resolve("emptyfolder"));   // git won't copy empty dir

        final JkFileTree tree = JkFileTree.of(Paths.get(sampleFileUrl.toURI()).getParent().getParent());
        System.out.println(tree.relativeFiles());
    }

    @Test
    public void testStream() throws Exception {
        Path sampleDir = sampleDir();

        // Root is included in the stream
        assertTrue(JkFileTree.of(sampleDir).stream().anyMatch(path -> path.equals(sampleDir)));
    }

    @Test
    public void testZipTo() throws Exception {
        Path zip = Files.createTempFile("filetree", ".zip");
        Files.delete(zip);
        JkFileTree.of(sampleDir()).zipTo(zip);
        Path zipRoot = JkUtilsPath.zipRoot(zip);
        assertTrue(Files.exists(zipRoot.resolve("subfolder/sample.txt")));
        zipRoot.getFileSystem().close();

        // Test overwrite
        JkFileTree.of(sampleDir()).zipTo(zip);
    }

    @Test
    public void testOfZip() throws Exception {
        Path zipFile = Files.createTempFile("jksample",".zip");
        Files.deleteIfExists(zipFile);
        JkFileTree zipTree = JkFileTree.ofZip(zipFile);
        zipTree.importDir(sampleDir());
        List<Path> paths = zipTree.files();
        assertEquals(1, paths.size());

        //System.out.println(Files.exists(zipRoot));
        //zipRoot.getFileSystem().close();
        Files.delete(zipFile);
    }

    @Test  // Ensure we can create a zip from a zip
    public void testZipZipTo() throws IOException, URISyntaxException {
        Path zip = createSampleZip();
        Path zip2 = Files.createTempFile("sample2", ".zip");
        Files.delete(zip2);
        JkFileTree.ofZip(zip).zipTo(zip2);
        JkFileTree zip2Tree = JkFileTree.ofZip(zip2);
        assertTrue(Files.isDirectory(zip2Tree.get("subfolder")));
        assertTrue(Files.isRegularFile(zip2Tree.get("subfolder").resolve("sample.txt")));
        assertTrue(Files.isDirectory(zip2Tree.get("emptyfolder")));
        Files.delete(zip);
        Files.delete(zip2);
    }

    @Test  // Ensure we can import  a zip from a zip
    public void testZipImportDir() throws IOException, URISyntaxException {
        Path zip = createSampleZip();
        Path zip2 = Files.createTempFile("sample2", ".zip");
        Files.delete(zip2);
        JkFileTree zip2Tree = JkFileTree.ofZip(zip2);
        zip2Tree.importDir(JkFileTree.ofZip(zip).root());
        assertTrue(Files.isDirectory(zip2Tree.get("subfolder")));
        assertTrue(Files.isRegularFile(zip2Tree.get("subfolder").resolve("sample.txt")));
        assertTrue(Files.isDirectory(zip2Tree.get("emptyfolder")));
        Files.delete(zip);
        Files.delete(zip2);
    }

    @Test
    public void testImportTree() throws IOException, URISyntaxException {
        Path zip = createSampleZip();
        Path dirSample = Files.createTempDirectory("sample");
        JkFileTree tree = JkFileTree.of(dirSample);
        tree.importTree(JkFileTree.ofZip(createSampleZip()));
        assertTrue(Files.isDirectory(tree.get("subfolder")));
        assertTrue(Files.isRegularFile(tree.get("subfolder").resolve("sample.txt")));
        assertTrue(Files.isDirectory(tree.get("emptyfolder")));
        Files.delete(zip);
    }


    private static Path sampleDir() throws Exception {
        final URL sampleFileUrl = JkUtilsPathTest.class
                .getResource("samplefolder/subfolder/sample.txt");
        final Path source = Paths.get(sampleFileUrl.toURI()).getParent().getParent();
        Files.createDirectories(source.resolve("emptyfolder"));   // git won't copy empty dir
        final Path sampleFile = Paths.get(sampleFileUrl.toURI());
        assertTrue(Files.exists(sampleFile));
        return sampleFile.getParent().getParent();
    }

    private static Path sampleFolder() throws IOException, URISyntaxException {
        final URL sampleFileUrl = JkUtilsPathTest.class
                .getResource("samplefolder/subfolder/sample.txt");
        final Path source = Paths.get(sampleFileUrl.toURI()).getParent().getParent();
        Files.createDirectories(source.resolve("emptyfolder"));   // git won't copy empty dir
        final Path sampleFile = Paths.get(sampleFileUrl.toURI());
        assertTrue(Files.exists(sampleFile));
        return sampleFile.getParent().getParent();
    }

    private static Path createSampleZip() throws IOException, URISyntaxException {
        Path folder = sampleFolder();
        Path zip = Files.createTempFile("sample", ".zip");
        Files.delete(zip);
        JkFileTree.of(folder).zipTo(zip);
        return zip;
    }




}