package dev.jeka.core.api.tooling.intellij;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkQualifiedDependency;
import dev.jeka.core.api.depmanagement.JkQualifiedDependencySet;
import dev.jeka.core.api.depmanagement.resolution.JkDependencyResolver;
import dev.jeka.core.api.depmanagement.resolution.JkResolvedDependencyNode;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.marshalling.JkDomElement;
import dev.jeka.core.api.project.JkIdeSupport;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsIterable;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.tool.JkConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Provides method to generate and read Eclipse metadata files.
 */
public final class JkImlGenerator2 {

    private JkPathSequence defClasspath = JkPathSequence.of();

    private JkPathSequence defImportedProjects = JkPathSequence.of();

    /* When true, path will be mentioned with $JEKA_HOME$ and $JEKA_REPO$ instead of explicit absolute path. */
    private boolean useVarPath = true;

    private Path explicitJekaHome;

    private JkIdeSupport ideSupport;

    // Only used if ideSupport is <code>null</code>
    private Path baseDir;

    private boolean skipJeka;

    private Consumer<JkIml> imlConfigurer = jkIml -> {};

    private boolean failOnDepsResolutionError = true;

    private JkImlGenerator2() {
    }

    public static JkImlGenerator2 of() {
        return new JkImlGenerator2();
    }

    public boolean isUseVarPath() {
        return useVarPath;
    }

    public JkImlGenerator2 setUseVarPath(boolean useVarPath) {
        this.useVarPath = useVarPath;
        return this;
    }

    public JkImlGenerator2 setDefClasspath(JkPathSequence defClasspath) {
        this.defClasspath = defClasspath;
        return this;
    }

    public JkImlGenerator2 setDefImportedProjects(JkPathSequence defImportedProjects) {
        this.defImportedProjects = defImportedProjects;
        return this;
    }

    public JkImlGenerator2 setBaseDir(Path baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    public JkImlGenerator2 configureIml(Consumer<JkIml> imlConfigurer) {
        this.imlConfigurer = this.imlConfigurer.andThen(imlConfigurer);
        return this;
    }

    public Path getExplicitJekaHome() {
        return explicitJekaHome;
    }

    public JkImlGenerator2 setExplicitJekaHome(Path explicitJekaHome) {
        this.explicitJekaHome = explicitJekaHome;
        return this;
    }

    public JkIdeSupport getIdeSupport() {
        return ideSupport;
    }

    public JkImlGenerator2 setIdeSupport(JkIdeSupport ideSupport) {
        this.ideSupport = ideSupport;
        return this;
    }

    public JkImlGenerator2 setSkipJeka(boolean skipJeka) {
        this.skipJeka = skipJeka;
        return this;
    }

    public JkImlGenerator2 setFailOnDepsResolutionError(boolean failOnDepsResolutionError) {
        this.failOnDepsResolutionError = failOnDepsResolutionError;
        return this;
    }

    public JkIml computeIml() {
        Path dir = ideSupport == null ? baseDir : ideSupport.getProdLayout().getBaseDir();
        JkIml iml = JkIml.of().setModuleDir(dir);
        JkLog.trace("Compute iml def classpath : " + defClasspath);
        JkLog.trace("Compute iml def imported projects : " + defImportedProjects);
        if (this.useVarPath) {
            iml.pathUrlResolver.setPathSubstitute(JkLocator.getJekaUserHomeDir(), JkLocator.getJekaHomeDir());
        }
        iml.getComponent()
                .getContent()
                    .addJekaStandards();
        if (ideSupport != null) {
            ideSupport.getProdLayout().resolveSources().getRootDirsOrZipFiles()
                    .forEach(path -> iml.getComponent().getContent().addSourceFolder(path, false, null));
            ideSupport.getProdLayout().resolveResources().getRootDirsOrZipFiles()
                    .forEach(path -> iml.getComponent().getContent().addSourceFolder(path, false, "java-resource"));
            ideSupport.getTestLayout().resolveSources().getRootDirsOrZipFiles().
                    forEach(path -> iml.getComponent().getContent().addSourceFolder(path, true, null));
            JkDependencyResolver depResolver = ideSupport.getDependencyResolver();
            JkResolvedDependencyNode tree = depResolver.resolve(
                    ideSupport.getDependencies(),
                    depResolver.getDefaultParams().clone().setFailOnDependencyResolutionError(failOnDepsResolutionError))
                    .getDependencyTree();
            iml.getComponent().getOrderEntries().addAll(projectOrderEntries(tree));
            Path generatedSources = ideSupport.getProdLayout().resolveGeneratedSourceDir();
            iml.getComponent().getContent().addSourceFolder(generatedSources, false, null);
        }
        iml.getComponent().getOrderEntries().addAll(defOrderEntries());
        imlConfigurer.accept(iml);
        return iml;
    }

    private Path baseDir() {
        return ideSupport == null ? baseDir : ideSupport.getProdLayout().getBaseDir();
    }

    // For def, we have computed classpath from runtime
    private List<JkIml.OrderEntry> defOrderEntries() {
        OrderEntries orderEntries = new OrderEntries();
        defClasspath.and(defImportedProjects).getEntries().stream()
                .filter((path -> !skipJeka || !JkLocator.getJekaJarPath().equals(path)))
                .filter(path -> !path.endsWith(Paths.get(JkConstants.DEF_BIN_DIR)))
                .forEach(path -> orderEntries.add(path, JkIml.Scope.TEST));
        return new LinkedList<>(orderEntries.orderEntries);
    }

    private class OrderEntries {

        private LinkedHashSet<JkIml.OrderEntry> orderEntries = new LinkedHashSet<>();

        void add(Path entry, JkIml.Scope scope) {
            JkIml.OrderEntry orderEntry;
            if (Files.isDirectory(entry)) {
                orderEntry = dirAsOrderEntry(entry, scope);
            } else {
                orderEntry = libAsOrderEntry(entry)
                        .setScope(scope)
                        .setExported(true);
            }
            orderEntries.add(orderEntry);
        }
    }

    private Path jekaHome() {
        if (explicitJekaHome != null) {
            return explicitJekaHome;
        }
        return JkLocator.getJekaHomeDir();
    }

    private JkIml.OrderEntry dirAsOrderEntry(Path dir, JkIml.Scope scope) {

        // first, look if it is inside an imported project
        for (Path importedProject : this.defImportedProjects) {
            if (importedProject.resolve(JkConstants.DEF_BIN_DIR).equals(dir)) {
                return JkIml.ModuleOrderEntry.of()
                        .setModuleName(moduleName(dir))
                        .setExported(true)
                        .setScope(scope);
            }
        }
        if (Files.exists(dir.resolve("jeka")) && Files.isDirectory(dir.resolve("jeka"))) {
            return JkIml.ModuleOrderEntry.of().setModuleName(moduleName(dir)).setScope(scope).setExported(true);
        }
        return JkIml.ModuleLibraryOrderEntry.of().setClasses(dir).setScope(scope).setExported(true);
    }

    private static String moduleName(Path projectDir) {
        return findImlFile(projectDir)
                .map(path -> JkUtilsString.substringBeforeLast(path.getFileName().toString(), ".iml"))
                .orElse(projectDir.getFileName().toString());
    }

    public static Optional<Path> findImlFile(Path projectDir) {
        return JkPathTree.of(projectDir).andMatching(".idea/*.iml", "*.iml").stream().findFirst();
    }

    private static JkIml.ModuleLibraryOrderEntry libAsOrderEntry(Path libPath) {
        return JkIml.ModuleLibraryOrderEntry.of()
                .setClasses(libPath)
                .setSources(lookForSources(libPath))
                .setJavadoc(lookForJavadoc(libPath));
    }

    private static Path lookForSources(Path binary) {
        final String name = binary.getFileName().toString();
        final String nameWithoutExt = JkUtilsString.substringBeforeLast(name, ".");
        final String ext = JkUtilsString.substringAfterLast(name, ".");
        final String sourceName = nameWithoutExt + "-sources." + ext;
        final List<Path> folders = JkUtilsIterable.listOf(
                binary.resolve("..").normalize(),
                binary.resolve("../sources").normalize(),
                binary.resolve("../../sources").normalize());
        final List<String> names = JkUtilsIterable.listOf(sourceName, nameWithoutExt + "-sources.zip");
        return lookFileHere(folders, names);
    }

    private static Path lookForJavadoc(Path binary) {
        final String name = binary.getFileName().toString();
        final String nameWithoutExt = JkUtilsString.substringBeforeLast(name, ".");
        final String ext = JkUtilsString.substringAfterLast(name, ".");
        final String sourceName = nameWithoutExt + "-javadoc." + ext;
        final List<Path> folders = JkUtilsIterable.listOf(
                binary.resolve("..").normalize(),
                binary.resolve("../../../libs-javadoc").normalize(),
                binary.resolve("../../libs-javadoc").normalize(),
                binary.resolve("../libs-javadoc").normalize());
        final List<String> names = JkUtilsIterable.listOf(sourceName, nameWithoutExt + "-javadoc.zip");
        return lookFileHere(folders, names);
    }

    private static Path lookFileHere(Iterable<Path> folders, Iterable<String> names) {
        for (final Path folder : folders) {
            for (final String name : names) {
                final Path candidate = folder.resolve(name).normalize();
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private List<JkIml.OrderEntry> projectOrderEntries(JkResolvedDependencyNode tree) {
        OrderEntries orderEntries = new OrderEntries();
        for (final JkResolvedDependencyNode node : tree.toFlattenList()) {

            // Maven dependency
            if (node.isModuleNode()) {
                JkResolvedDependencyNode.JkModuleNodeInfo moduleNodeInfo = node.getModuleInfo();
                if (moduleNodeInfo.isEvicted()) {
                    continue;
                }
                JkIml.Scope scope = ideScope(moduleNodeInfo.getRootConfigurations());
                moduleNodeInfo.getFiles().forEach(path -> orderEntries.add(path, scope));

                // File dependencies (file system + computed)
            } else {
                final JkResolvedDependencyNode.JkFileNodeInfo fileNodeInfo = (JkResolvedDependencyNode.JkFileNodeInfo) node.getNodeInfo();
                JkIml.Scope scope = ideScope(fileNodeInfo.getDeclaredConfigurations());
                if (fileNodeInfo.isComputed()) {
                    final Path projectDir = fileNodeInfo.computationOrigin().getIdeProjectDir();
                    if (projectDir != null) {
                        orderEntries.add(projectDir, scope);
                    }
                } else {
                    fileNodeInfo.getFiles().forEach(path -> orderEntries.add(path, scope));
                }
            }
        }
        return new LinkedList(orderEntries.orderEntries);
    }

    private static JkIml.Scope ideScope(Set<String> scopes) {
        if (scopes.contains(JkQualifiedDependencySet.COMPILE_SCOPE))  {
            return JkIml.Scope.COMPILE;
        } else if (scopes.contains(JkQualifiedDependencySet.RUNTIME_SCOPE)) {
            return JkIml.Scope.RUNTIME;
        } else if (scopes.contains(JkQualifiedDependencySet.PROVIDED_SCOPE)) {
            return JkIml.Scope.PROVIDED;
        } else if (scopes.contains(JkQualifiedDependencySet.TEST_SCOPE)) {
            return JkIml.Scope.TEST;
        } else {
            return JkIml.Scope.COMPILE;
        }
    }

}