package org.jerkar.tool;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jerkar.api.depmanagement.JkDependencies;
import org.jerkar.api.depmanagement.JkDependency;
import org.jerkar.api.depmanagement.JkDependencyResolver;
import org.jerkar.api.depmanagement.JkPublishRepo;
import org.jerkar.api.depmanagement.JkRepo;
import org.jerkar.api.depmanagement.JkRepos;
import org.jerkar.api.depmanagement.JkScopeMapping;
import org.jerkar.api.file.JkFileTree;
import org.jerkar.api.file.JkPath;
import org.jerkar.api.file.JkPathFilter;
import org.jerkar.api.java.JkClassLoader;
import org.jerkar.api.java.JkClasspath;
import org.jerkar.api.java.JkJavaCompiler;
import org.jerkar.api.system.JkLocator;
import org.jerkar.api.system.JkLog;
import org.jerkar.api.utils.JkUtilsFile;
import org.jerkar.api.utils.JkUtilsReflect;
import org.jerkar.api.utils.JkUtilsTime;
import org.jerkar.tool.CommandLine.MethodInvocation;

/**
 * Engine having responsibility of compiling build classes, instantiate/configure build instances
 * and run them.<br/>
 * Build classes are expected to lie in [project base dir]/build/def <br/>
 * Classes having simple name starting with '_' are ignored.
 */
final class Engine {

    private final JkPathFilter BUILD_SOURCE_FILTER = JkPathFilter.include("**/*.java").andExclude("**/_*");

    private final File projectBaseDir;

    private JkDependencies buildDependencies;

    private JkRepos buildRepos;

    private List<File> rootsOfImportedBuilds = new LinkedList<>();

    private final BuildResolver resolver;

    /**
     * Constructs an engine for specified base directory .
     */
    Engine(File baseDir) {
        super();
        this.projectBaseDir = JkUtilsFile.canonicalFile(baseDir);
        buildRepos = repos();
        this.buildDependencies = JkDependencies.of();
        this.resolver = new BuildResolver(baseDir);
    }

    private void preCompile() {
        final JavaSourceParser parser = JavaSourceParser.of(this.projectBaseDir,
                JkFileTree.of(resolver.buildSourceDir).andFilter(BUILD_SOURCE_FILTER));
        this.buildDependencies = this.buildDependencies.and(parser.dependencies());
        this.buildRepos = parser.importRepos().and(buildRepos);
        this.rootsOfImportedBuilds = parser.projects();
    }

    // Compiles and returns the runtime classpath
    private JkPath compile() {
        final LinkedHashSet<File> entries = new LinkedHashSet<>();
        compile(new HashSet<>(), entries);
        return JkPath.of(entries).withoutDuplicates();
    }

    private void compile(Set<File> yetCompiledProjects, LinkedHashSet<File> path) {
        if (!this.resolver.hasBuildSource() || yetCompiledProjects.contains(this.projectBaseDir)) {
            return;
        }
        yetCompiledProjects.add(this.projectBaseDir);
        preCompile(); // This enrich dependencies
        JkLog.startln("Compiling build classes for project " + this.projectBaseDir.getName());
        JkLog.startln("Resolving compilation classpath");
        final JkDependencyResolver buildClassDependencyResolver = getBuildDefDependencyResolver();
        final JkPath buildPath = buildClassDependencyResolver.get(this.buildDefDependencies());
        path.addAll(buildPath.entries());
        path.addAll(compileDependentProjects(yetCompiledProjects, path).entries());
        JkLog.done();
        this.compileBuild(JkPath.of(path));
        path.add(this.resolver.buildClassDir);
        JkLog.done();
    }

    <T extends JkBuild> T getBuild(Class<T> baseClass) {
        if (resolver.needCompile()) {
            this.compile();
        }
        return resolver.resolve(baseClass);
    }

    List<Class<?>> getBuildClasses() {
        if (resolver.needCompile()) {
            this.compile();
        }
        return resolver.resolveBuildClasses();
    }

    /**
     * Pre-compile and compile build classes (if needed) then execute the build
     * of this project.
     */
    void execute(JkInit init) {
        this.buildDependencies = this.buildDependencies.andScopeless(init.commandLine().dependencies());
        JkLog.startHeaded("Compiling and instantiating build class");
        JkPath runtimeClasspath = compile();
        if (!init.commandLine().dependencies().isEmpty()) {
            JkLog.startln("Grab dependencies specified in command line");
            final JkPath cmdPath = pathOf(init.commandLine().dependencies());
            runtimeClasspath = runtimeClasspath.andHead(cmdPath);
            if (JkLog.verbose()) {
                JkLog.done("Command line extra path : " + cmdPath);
            } else {
                JkLog.done();
            }
        }
        JkLog.info("Instantiating and configuring build class");
        final BuildAndPluginDictionnary buildAndDict = getBuildInstance(init, runtimeClasspath);
        if (buildAndDict == null) {
            throw new JkException("Can't find or guess any build class for project hosted in " + this.projectBaseDir
                    + " .\nAre you sure this directory is a buildable project ?");
        }
        JkLog.done();
        try {
            this.launch(buildAndDict.build, buildAndDict.dictionnary, init.commandLine());
        } catch (final RuntimeException e) {
            JkLog.error("Engine " + projectBaseDir.getAbsolutePath() + " failed");
            throw e;
        }
    }

    private JkPath pathOf(List<? extends JkDependency> dependencies) {
        final JkDependencies deps = JkDependencies.of(dependencies);
        return JkDependencyResolver.of(this.buildRepos).get(deps);
    }

    JkBuild instantiate(JkInit init) {
        final JkPath runtimePath = compile();
        JkLog.nextLine();
        final BuildAndPluginDictionnary buildAndDict = getBuildInstance(init, runtimePath);
        if (buildAndDict == null) {
            return null;
        }
        return buildAndDict.build;
    }

    private BuildAndPluginDictionnary getBuildInstance(JkInit init, JkPath runtimePath) {
        final JkClassLoader classLoader = JkClassLoader.current();
        classLoader.addEntries(runtimePath);
        JkLog.trace("Setting build execution classpath to : " + classLoader.childClasspath());
        final JkBuild build = resolver.resolve(init.buildClassHint());
        if (build == null) {
            return null;
        }
        try {
            build.setBuildDefDependencyResolver(this.buildDefDependencies(), getBuildDefDependencyResolver());
            final PluginDictionnary dictionnary = init.initProject(build);
            final BuildAndPluginDictionnary result = new BuildAndPluginDictionnary();
            result.build = build;
            result.dictionnary = dictionnary;
            return result;
        } catch (final RuntimeException e) {
            JkLog.error("Engine " + projectBaseDir.getAbsolutePath() + " failed");
            throw e;
        }
    }

    private static class BuildAndPluginDictionnary {
        JkBuild build;
        PluginDictionnary dictionnary;
    }

    private JkDependencies buildDefDependencies() {

        // If true, we assume Jerkar is provided by IDE (development mode)
        final boolean devMode = JkLocator.jerkarJarFile().isDirectory();

        return JkDependencies.builder().on(buildDependencies
                .withDefaultScope(JkScopeMapping.ALL_TO_DEFAULT))
                .onFiles(localBuildPath())
                .onFilesIf(devMode, JkClasspath.current())
                .onFilesIf(!devMode, jerkarLibs())
                .build();
    }

    private JkPath localBuildPath() {
        final List<File> extraLibs = new LinkedList<>();
        final File localDeflibDir = new File(this.projectBaseDir, JkConstants.BUILD_BOOT);
        if (localDeflibDir.exists()) {
            extraLibs.addAll(JkFileTree.of(localDeflibDir).include("**/*.jar").files(false));
        }
        return JkPath.of(extraLibs).withoutDuplicates();
    }

    private static JkPath jerkarLibs() {
        final List<File> extraLibs = new LinkedList<>();
        extraLibs.add(JkLocator.jerkarJarFile());
        return JkPath.of(extraLibs).withoutDuplicates();
    }

    private JkPath compileDependentProjects(Set<File> yetCompiledProjects, LinkedHashSet<File> pathEntries) {
        JkPath jkPath = JkPath.of();
        if (!this.rootsOfImportedBuilds.isEmpty()) {
            JkLog.info("Compile build classes of dependent projects : "
                    + toRelativePaths(this.projectBaseDir, this.rootsOfImportedBuilds));
        }
        for (final File file : this.rootsOfImportedBuilds) {
            final Engine engine = new Engine(file);
            engine.compile(yetCompiledProjects, pathEntries);
            jkPath = jkPath.and(file);
        }
        return jkPath;
    }

    private void compileBuild(JkPath buildPath) {
        baseBuildCompiler().withClasspath(buildPath).compile();
        JkFileTree.of(this.resolver.buildSourceDir).exclude("**/*.java").copyTo(this.resolver.buildClassDir);
    }

    private void launch(JkBuild build, PluginDictionnary dictionnary, CommandLine commandLine) {

        // Now run projects
        if (!commandLine.getSubProjectMethods().isEmpty()) {
            for (final JkBuild subBuild : build.importedBuilds().all()) {
                runProject(subBuild, commandLine.getSubProjectMethods(), dictionnary);
            }
        }
        runProject(build, commandLine.getMasterMethods(), dictionnary);
    }

    private static void runProject(JkBuild build, List<MethodInvocation> invokes,
            PluginDictionnary dictionnary) {
        JkLog.infoHeaded("Executing build for project " + build.baseTree().root().getName());
        JkLog.info("Build class : " + build.getClass().getName());
        JkLog.info("Base dir : " + build.baseTree().root().getPath());
        final Map<String, String> displayedOptions = JkOptions.toDisplayedMap(OptionInjector.injectedFields(build));
        if (JkLog.verbose()) {
            JkInit.logProps("Field values", displayedOptions);
        }
        execute(build, toBuildMethods(invokes, dictionnary), null);
    }

    /**
     * Executes the specified methods given the fromDir as working directory.
     */
    private static void execute(JkBuild build, Iterable<BuildMethod> methods, File fromDir) {
        for (final BuildMethod method : methods) {
            invoke(build, method, fromDir);
        }
    }

    private static void invoke(JkBuild build, BuildMethod modelMethod, File fromDir) {
        if (modelMethod.isMethodPlugin()) {
            JkBuildPlugin plugin = build.plugins().get(modelMethod.pluginClass());
            build.plugins().invoke(plugin, modelMethod.name());
        } else {
            invoke(build, modelMethod.name(), fromDir);
        }
    }

    /**
     * Invokes the specified method in this build.
     */
    private static void invoke(JkBuild build, String methodName, File fromDir) {
        final Method method;
        try {
            method = build.getClass().getMethod(methodName);
        } catch (final NoSuchMethodException e) {
            JkLog.warn("No zero-arg method '" + methodName + "' found in class '" + build.getClass()
            + "'. Skip.");
            JkLog.warnStream().flush();
            return;
        }
        final String context;
        if (fromDir != null) {
            final String path = JkUtilsFile.getRelativePath(fromDir, build.baseTree().root()).replace(
                    File.separator, "/");
            context = " to project " + path + ", class " + build.getClass().getName();
        } else {
            context = "";
        }
        JkLog.infoUnderlined("Method : " + methodName + context);
        final long time = System.nanoTime();
        try {
            JkUtilsReflect.invoke(build, method);
            JkLog.info("Method " + methodName + " success in "
                    + JkUtilsTime.durationInSeconds(time) + " seconds.");
        } catch (final RuntimeException e) {
            JkLog.info("Method " + methodName + " failed in " + JkUtilsTime.durationInSeconds(time)
            + " seconds.");
            throw e;
        }
    }

    private static List<BuildMethod> toBuildMethods(Iterable<MethodInvocation> invocations,
                                                    PluginDictionnary dictionnary) {
        final List<BuildMethod> buildMethods = new LinkedList<>();
        for (final MethodInvocation methodInvokation : invocations) {
            if (methodInvokation.isMethodPlugin()) {
                final Class<? extends JkBuildPlugin> clazz = dictionnary.loadByNameOrFail(methodInvokation.pluginName)
                        .pluginClass();
                buildMethods.add(BuildMethod.pluginMethod(clazz, methodInvokation.methodName));
            } else {
                buildMethods.add(BuildMethod.normal(methodInvokation.methodName));
            }
        }
        return buildMethods;
    }

    private JkJavaCompiler baseBuildCompiler() {
        final JkFileTree buildSource = JkFileTree.of(resolver.buildSourceDir).andFilter(BUILD_SOURCE_FILTER);
        if (!resolver.buildClassDir.exists()) {
            resolver.buildClassDir.mkdirs();
        }
        return JkJavaCompiler.outputtingIn(resolver.buildClassDir).andSources(buildSource).failOnError(true);
    }

    private JkDependencyResolver getBuildDefDependencyResolver() {
        final JkDependencies deps = this.buildDefDependencies();
        if (deps.containsModules()) {
            return JkDependencyResolver.of(this.buildRepos);
        }
        return JkDependencyResolver.of();
    }

    @Override
    public String toString() {
        return this.projectBaseDir.getName();
    }

    private static JkRepos repos() {
        return JkRepo
                .firstNonNull(JkRepoOptions.repoFromOptions("build"),
                        JkRepoOptions.repoFromOptions("download"), JkRepo.mavenCentral())
                .and(JkPublishRepo.local().repo());
    }

    private static List<String> toRelativePaths(File from, List<File> files) {
        final List<String> result = new LinkedList<>();
        for (final File file : files) {
            final String relPath = JkUtilsFile.getRelativePath(from, file);
            result.add(relPath);
        }
        return result;
    }

}