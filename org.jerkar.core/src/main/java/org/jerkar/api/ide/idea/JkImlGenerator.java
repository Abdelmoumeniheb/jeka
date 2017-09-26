package org.jerkar.api.ide.idea;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jerkar.api.depmanagement.*;
import org.jerkar.api.file.JkFileTree;
import org.jerkar.api.java.JkJavaVersion;
import org.jerkar.api.project.JkProjectSourceLayout;
import org.jerkar.api.project.java.JkJavaProject;
import org.jerkar.api.system.JkLocator;
import org.jerkar.api.utils.JkUtilsFile;
import org.jerkar.api.utils.JkUtilsIterable;
import org.jerkar.api.utils.JkUtilsString;
import org.jerkar.api.utils.JkUtilsThrowable;
import org.jerkar.tool.JkConstants;

/**
 * Provides method to generate and read Eclipse metadata files.
 */
public final class JkImlGenerator {

    private static final String ENCODING = "UTF-8";

    private static final String T1 = "  ";

    private static final String T2 = T1 + T1;

    private static final String T3 = T2 + T1;

    private static final String T4 = T3 + T1;

    private static final String T5 = T4 + T1;

    private JkProjectSourceLayout sourceLayout;

    /** Used to generate JRE container */
    private JkJavaVersion sourceJavaVersion;


    /** Dependency resolver to fetch module dependencies */
    private JkDependencyResolver dependencyResolver;

    public JkDependencies dependencies;

    /** Dependency resolver to fetch module dependencies for build classes */
    private JkDependencyResolver buildDependencyResolver;

    private JkDependencies buildDependencies;

    /** Can be empty but not null */
    private Iterable<File> importedBuildProjects = JkUtilsIterable.listOf();

    private boolean forceJdkVersion;

    /* When true, path will be mentioned with $JERKAR_HOME$ and $JERKAR_REPO$ instead of explicit absolute path. */
    private boolean useVarPath;

    private final Set<String> paths = new HashSet<>();

    private XMLStreamWriter writer;

    /**
     * Constructs a {@link JkImlGenerator} to the project base directory
     */
    public JkImlGenerator(JkProjectSourceLayout sourceLayout, JkDependencies dependencies,
            JkDependencyResolver resolver, JkJavaVersion sourceVersion) {
        super();
        this.sourceLayout = sourceLayout;
        this.dependencies = dependencies;
        this.dependencyResolver = resolver;
    }

    public JkImlGenerator(JkJavaProject javaProject) {
        this(javaProject.getSourceLayout(), javaProject.getDependencies(),
                javaProject.maker().getDependencyResolver(), javaProject.getSourceVersion());
    }

    /** Generate the .classpath file */
    public String generate() {
        try {
            return _generate();
        } catch (final Exception e) {
            throw JkUtilsThrowable.unchecked(e);
        }
    }

    private String _generate() throws IOException, XMLStreamException, FactoryConfigurationError {
        final ByteArrayOutputStream fos = new ByteArrayOutputStream();
        writer = createWriter(fos);
        writeHead();
        writeOutput();
        writeJdk();
        writeContent();
        writeOrderEntrySourceFolder();
        final Set<File> allPaths = new HashSet<>();
        final Set<File> allModules = new HashSet<>();
        if (this.dependencyResolver != null) {
            writeDependencies(dependencies, this.dependencyResolver, allPaths, allModules, false);
        }
        if (this.buildDependencyResolver != null) {
            writeDependencies(this.buildDependencies, this.buildDependencyResolver, allPaths, allModules, true);
        }
        writeBuildProjectDependencies(allModules);

        writeFoot();
        writer.close();
        return fos.toString(ENCODING);
    }

    private void writeHead() throws XMLStreamException {
        writer.writeStartDocument(ENCODING, "1.0");
        writer.writeCharacters("\n");
        writer.writeStartElement("module");
        writer.writeAttribute("type", "JAVA_MODULE");
        writer.writeAttribute("version", "4");
        writer.writeCharacters("\n" + T1);
        writer.writeStartElement("component");
        writer.writeAttribute("name", "NewModuleRootManager");
        writer.writeAttribute("inherit-compiler-output", "false");
        writer.writeCharacters("\n");
    }

    private void writeFoot() throws XMLStreamException {
        writer.writeCharacters(T1);
        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();
        writer.close();
    }

    private void writeOutput() throws XMLStreamException {

        // TODO should get location to #outputClassFolder and #outputTestClassFolder
        writer.writeCharacters(T2);
        writer.writeEmptyElement("output");
        writer.writeAttribute("url", "file://$MODULE_DIR$/build/output/classes");
        writer.writeCharacters("\n");
        writer.writeCharacters(T2);
        writer.writeEmptyElement("output-test");
        writer.writeAttribute("url", "file://$MODULE_DIR$/build/output/test-classes");
        writer.writeCharacters("\n");
        writer.writeCharacters(T2);
        writer.writeEmptyElement("exclude-output");
        writer.writeCharacters("\n");
    }

    private void writeContent() throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeStartElement("content");
        writer.writeAttribute("url", "file://$MODULE_DIR$");
        writer.writeCharacters("\n");

        // Write build sources
        writer.writeCharacters(T3);
        writer.writeEmptyElement("sourceFolder");
        writer.writeAttribute("url", "file://$MODULE_DIR$/" + JkConstants.BUILD_DEF_DIR);
        writer.writeAttribute("isTestSource", "true");
        writer.writeCharacters("\n");
        writer.writeCharacters(T2);

        // Write test sources
        final File projectDir = this.sourceLayout.baseDir();
        for (final JkFileTree fileTree : this.sourceLayout.tests().fileTrees()) {
            if (fileTree.exists()) {
                writer.writeCharacters(T3);
                writer.writeEmptyElement("sourceFolder");
                final String path = JkUtilsFile.getRelativePath(projectDir, fileTree.root()).replace('\\', '/');
                writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
                writer.writeAttribute("isTestSource", "true");
                writer.writeCharacters("\n");
            }
        }

        // write test resources
        for (final JkFileTree fileTree : this.sourceLayout.testResources().fileTrees()) {
            if (fileTree.exists()) {
                writer.writeCharacters(T3);
                writer.writeEmptyElement("sourceFolder");
                final String path = JkUtilsFile.getRelativePath(projectDir, fileTree.root()).replace('\\', '/');
                writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
                writer.writeAttribute("type", "java-test-resource");
                writer.writeCharacters("\n");
            }
        }

        // Write production sources

        for (final JkFileTree fileTree : this.sourceLayout.sources().fileTrees()) {
            if (fileTree.exists()) {
                writer.writeCharacters(T3);
                writer.writeEmptyElement("sourceFolder");
                final String path = JkUtilsFile.getRelativePath(projectDir, fileTree.root()).replace('\\', '/');
                writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
                writer.writeAttribute("isTestSource", "false");
                writer.writeCharacters("\n");
            }
        }

        // Write production test resources
        for (final JkFileTree fileTree : this.sourceLayout.resources().fileTrees()) {
            if (fileTree.exists()) {
                writer.writeCharacters(T3);
                writer.writeEmptyElement("sourceFolder");
                final String path = JkUtilsFile.getRelativePath(projectDir, fileTree.root()).replace('\\', '/');
                writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
                writer.writeAttribute("type", "java-resource");
                writer.writeCharacters("\n");
            }
        }

        writer.writeCharacters(T3);
        writer.writeEmptyElement("excludeFolder");
        final String path = JkConstants.BUILD_OUTPUT_PATH;
        writer.writeAttribute("url", "file://$MODULE_DIR$/" + path);
        writer.writeAttribute("isTestSource", "false");
        writer.writeCharacters("\n");
        writer.writeCharacters(T2);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private void writeBuildProjectDependencies(Set<File> allModules) throws XMLStreamException {
        for (final File rootFolder : this.importedBuildProjects) {
            if (!allModules.contains(rootFolder)) {
                writeOrderEntryForModule(rootFolder.getName(), "COMPILE");
                allModules.add(rootFolder);
            }
        }
    }

    private void writeDependencies(JkDependencies dependencies, JkDependencyResolver resolver, Set<File> allPaths, Set<File> allModules,
            boolean forceTest) throws XMLStreamException {

        final JkResolveResult resolveResult = resolver.resolve(dependencies);
        final JkDependencyNode tree = resolveResult.dependencyTree();
        for (final JkDependencyNode node : tree.flatten()) {

            // Maven dependency
            if (node.isModuleNode()) {
                final String ideScope = forceTest ? "TEST" : ideScope(node.moduleInfo().resolvedScopes());
                final List<LibPath> paths = toLibPath(node.moduleInfo(), resolver.repositories(), ideScope);
                for (final LibPath libPath : paths) {
                    if (!allPaths.contains(libPath.bin)) {
                        writeOrderEntryForLib(libPath);
                        allPaths.add(libPath.bin);
                    }
                }

                // File dependencies (file system + computed)
            } else {
                final String ideScope = forceTest ? "TEST" : ideScope(node.nodeInfo().declaredScopes());
                final JkDependencyNode.FileNodeInfo fileNodeInfo = (JkDependencyNode.FileNodeInfo) node.nodeInfo();
                if (fileNodeInfo.isComputed()) {
                    final File projectDir = fileNodeInfo.computationOrigin().ideProjectBaseDir();
                    if (projectDir != null && !allModules.contains(projectDir)) {
                        writeOrderEntryForModule(projectDir.getName(), ideScope);
                        allModules.add(projectDir);
                    }
                } else {
                    writeFileEntries(fileNodeInfo.files(), paths, ideScope);
                }
            }
        }
    }

    private void writeFileEntries(Iterable<File> files, Set<String> paths, String ideScope) throws XMLStreamException {
        for (final File file : files) {
            final LibPath libPath = new LibPath();
            libPath.bin = file;
            libPath.scope = ideScope;
            libPath.source = lookForSources(file);
            libPath.javadoc = lookForJavadoc(file);
            writeOrderEntryForLib(libPath);
            paths.add(file.getPath());
        }
    }

    private List<LibPath> toLibPath(JkDependencyNode.ModuleNodeInfo moduleInfo, JkRepos repos,
            String scope) {
        final List<LibPath> result = new LinkedList<>();
        final JkModuleId moduleId = moduleInfo.moduleId();
        final JkVersion version = moduleInfo.resolvedVersion();
        final JkVersionedModule versionedModule = JkVersionedModule.of(moduleId, version);
        final List<File> files = moduleInfo.files();
        for (final File file : files) {
            final LibPath libPath = new LibPath();
            libPath.bin = file;
            libPath.scope = scope;
            libPath.source = repos.get(JkModuleDependency.of(versionedModule).classifier("sources"));
            libPath.javadoc = repos.get(JkModuleDependency.of(versionedModule).classifier("javadoc"));
            result.add(libPath);
        }
        return result;
    }

    private static Set<String> toStringScopes(Set<JkScope> scopes) {
        final Set<String> result = new HashSet<>();
        for (final JkScope scope : scopes) {
            result.add(scope.name());
        }
        return result;
    }

    private static String ideScope(Set<JkScope> scopesArg) {
        final Set<String> scopes = toStringScopes(scopesArg);
        if (scopes.contains(JkJavaDepScopes.COMPILE.name())) {
            return "COMPILE";
        }
        if (scopes.contains(JkJavaDepScopes.PROVIDED.name())) {
            return "PROVIDED";
        }
        if (scopes.contains(JkJavaDepScopes.RUNTIME.name())) {
            return "RUNTIME";
        }
        if (scopes.contains(JkJavaDepScopes.TEST.name())) {
            return "TEST";
        }
        return "COMPILE";
    }

    private void writeJdk() throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeEmptyElement("orderEntry");
        if (this.forceJdkVersion  && this.sourceJavaVersion != null) {
            writer.writeAttribute("type", "jdk");
            final String jdkVersion = jdkVersion(this.sourceJavaVersion);
            writer.writeAttribute("jdkName", jdkVersion);
            writer.writeAttribute("jdkType", "JavaSDK");
        } else {
            writer.writeAttribute("type", "inheritedJdk");
        }
        writer.writeCharacters("\n");
    }

    private void writeOrderEntrySourceFolder() throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeEmptyElement("orderEntry");
        writer.writeAttribute("type", "sourceFolder");
        writer.writeAttribute("forTests", "false");
        writer.writeCharacters("\n");
    }

    private void writeOrderEntryForLib(LibPath libPath) throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeStartElement("orderEntry");
        writer.writeAttribute("type", "module-library");
        if (libPath.scope != null) {
            writer.writeAttribute("scope", libPath.scope);
        }
        writer.writeAttribute("exported", "");
        writer.writeCharacters("\n");
        writer.writeCharacters(T3);
        writer.writeStartElement("library");
        writer.writeCharacters("\n");
        writeLibType("CLASSES", libPath.bin);
        writer.writeCharacters("\n");
        writeLibType("JAVADOC", libPath.javadoc);
        writer.writeCharacters("\n");
        writeLibType("SOURCES", libPath.source);
        writer.writeCharacters("\n" + T3);
        writer.writeEndElement();
        writer.writeCharacters("\n" + T2);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private void writeOrderEntryForModule(String ideaModuleName, String scope) throws XMLStreamException {
        writer.writeCharacters(T2);
        writer.writeEmptyElement("orderEntry");
        writer.writeAttribute("type", "module");
        if (scope != null) {
            writer.writeAttribute("scope", scope);
        }
        writer.writeAttribute("module-name", ideaModuleName);
        writer.writeAttribute("exported", "");
        writer.writeCharacters("\n");
    }

    private void writeLibType(String type, File file) throws XMLStreamException {
        writer.writeCharacters(T4);
        if (file != null) {
            writer.writeStartElement(type);
            writer.writeCharacters("\n");
            writer.writeCharacters(T5);
            writer.writeEmptyElement("baseTree");
            writer.writeAttribute("url", ideaPath(this.sourceLayout.baseDir(), file));
            writer.writeCharacters("\n" + T4);
            writer.writeEndElement();
        } else {
            writer.writeEmptyElement(type);
        }
    }

    private String ideaPath(File projectDir, File file) {
        if (file.getName().toLowerCase().endsWith(".jar")) {
            if (JkUtilsFile.isAncestor(projectDir, file)) {
                final String relPath = JkUtilsFile.getRelativePath(projectDir, file);
                return "jar://$MODULE_DIR$/" + replacePathWithVar(relPath) + "!/";
            }
            final String path = JkUtilsFile.canonicalPath(file).replace('\\', '/');
            return "jar://" + replacePathWithVar(replacePathWithVar(path)) + "!/";
        }
        if (JkUtilsFile.isAncestor(projectDir, file)) {
            final String relPath = JkUtilsFile.getRelativePath(projectDir, file);
            return "file://$MODULE_DIR$/" + replacePathWithVar(relPath);
        }
        final String path = JkUtilsFile.canonicalPath(file).replace('\\', '/');
        return "file://" + replacePathWithVar(replacePathWithVar(path));

    }


    private static String jdkVersion(JkJavaVersion javaVersion) {
        if (JkJavaVersion.V1_4.equals(javaVersion)) {
            return "1.4";
        }
        if (JkJavaVersion.V5.equals(javaVersion)) {
            return "1.5";
        }
        if (JkJavaVersion.V6.equals(javaVersion)) {
            return "1.6";
        }
        if (JkJavaVersion.V7.equals(javaVersion)) {
            return "1.7";
        }
        if (JkJavaVersion.V8.equals(javaVersion)) {
            return "1.8";
        }
        if (JkJavaVersion.V9.equals(javaVersion)) {
            return "1.9";
        }
        return javaVersion.name();
    }

    private static class LibPath {
        File bin;
        File source;
        File javadoc;
        String scope;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final LibPath libPath = (LibPath) o;

            return bin.equals(libPath.bin);
        }

        @Override
        public int hashCode() {
            return bin.hashCode();
        }
    }

    private String replacePathWithVar(String path) {
        if (!useVarPath) {
            return path;
        }
        final String repo = JkLocator.jerkarRepositoryCache().getAbsolutePath().replace('\\', '/');
        final String home = JkLocator.jerkarHome().getAbsolutePath().replace('\\', '/');
        final String result = path.replace(repo, "$JERKAR_REPO$");
        if (!result.equals(path)) {
            return result;
        }
        return path.replace(home, "$JERKAR_HOME$");
    }

    private static XMLStreamWriter createWriter(ByteArrayOutputStream fos) {
        try {
            return XMLOutputFactory.newInstance().createXMLStreamWriter(fos, ENCODING);
        } catch (final XMLStreamException e) {
            throw JkUtilsThrowable.unchecked(e);
        }
    }

    /*
     * If the specified folder is the output folder of an eclipse project than it returns the asScopedDependency of this project,
     * else otherwise.
     */
    private static File getProjectFolderOf(Iterable<File> files, Iterable<File> projectDependencies) {
        if (!files.iterator().hasNext()) {
            return null;
        }
        File folder = files.iterator().next().getParentFile();
        while (folder != null) {
            if (JkFileTree.of(folder).include("*.iml").fileCount(false) == 1) {
                return folder;
            }
            if (JkUtilsIterable.listOf(projectDependencies).contains(folder)) {
                return folder;
            }
            folder = folder.getParentFile();
        }
        return null;
    }

    private File lookForSources(File binary) {
        final String name = binary.getName();
        final String nameWithoutExt = JkUtilsString.substringBeforeLast(name, ".");
        final String ext = JkUtilsString.substringAfterLast(name, ".");
        final String sourceName = nameWithoutExt + "-sources." + ext;
        final List<File> folders = JkUtilsIterable.listOf(
                binary.getParentFile(),
                new File(binary.getParentFile().getParentFile().getParentFile(), "libs-sources"),
                new File(binary.getParentFile().getParentFile(), "libs-sources"),
                new File(binary.getParentFile(), "libs-sources"));
        final List<String> names = JkUtilsIterable.listOf(sourceName, nameWithoutExt + "-sources.zip");
        return lookFileHere(folders, names);
    }

    private File lookForJavadoc(File binary) {
        final String name = binary.getName();
        final String nameWithoutExt = JkUtilsString.substringBeforeLast(name, ".");
        final String ext = JkUtilsString.substringAfterLast(name, ".");
        final String sourceName = nameWithoutExt + "-javadoc." + ext;
        final List<File> folders = JkUtilsIterable.listOf(
                binary.getParentFile(),
                new File(binary.getParentFile().getParentFile().getParentFile(), "libs-javadoc"),
                new File(binary.getParentFile().getParentFile(), "libs-javadoc"),
                new File(binary.getParentFile(), "libs-javadoc"));
        final List<String> names = JkUtilsIterable.listOf(sourceName, nameWithoutExt + "-javadoc.zip");
        return lookFileHere(folders, names);
    }

    private File lookFileHere(Iterable<File> folders, Iterable<String> names) {
        for (final File folder : folders) {
            for (final String name : names) {
                final File candidate = new File(folder, name);
                if (candidate.exists()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    // --------------------------- setters ------------------------------------------------


    public JkImlGenerator setSourceLayout(JkProjectSourceLayout sourceLayout) {
        this.sourceLayout = sourceLayout;
        return this;
    }

    public JkImlGenerator setSourceJavaVersion(JkJavaVersion sourceJavaVersion) {
        this.sourceJavaVersion = sourceJavaVersion;
        return this;
    }

    public JkImlGenerator setDependencies(JkDependencyResolver dependencyResolver, JkDependencies dependencies) {
        this.dependencyResolver = dependencyResolver;
        this.dependencies = dependencies;
        return this;
    }

    public JkImlGenerator setBuildDependencies(JkDependencyResolver buildDependencyResolver, JkDependencies dependencies) {
        this.buildDependencyResolver = buildDependencyResolver;
        this.buildDependencies = dependencies;
        return this;
    }

    public JkImlGenerator setImportedBuildProjects(Iterable<File> importedBuildProjects) {
        this.importedBuildProjects = importedBuildProjects;
        return this;
    }

    public JkImlGenerator setForceJdkVersion(boolean forceJdkVersion) {
        this.forceJdkVersion = forceJdkVersion;
        return this;
    }

    public JkImlGenerator setUseVarPath(boolean useVarPath) {
        this.useVarPath = useVarPath;
        return this;
    }

    public JkImlGenerator setWriter(XMLStreamWriter writer) {
        this.writer = writer;
        return this;
    }
}