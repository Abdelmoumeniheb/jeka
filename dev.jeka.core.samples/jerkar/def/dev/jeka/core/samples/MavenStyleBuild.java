package dev.jeka.core.samples;

import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.JkRepoSet;
import dev.jeka.core.api.file.JkPathMatcher;
import dev.jeka.core.api.file.JkResourceProcessor;
import dev.jeka.core.tool.JkRun;
import dev.jeka.core.tool.builtins.java.JkPluginJava;

import java.util.HashMap;
import java.util.Map;

import static dev.jeka.core.api.depmanagement.JkJavaDepScopes.TEST;
import static dev.jeka.core.api.depmanagement.JkPopularModules.*;

/**
 * Build sample for a jar project depending on several external modules. This
 * build produces :
 * - jar, fat-jar, sources-jar by launching 'jerkar'
 * - all this + javadoc by launching 'jerkar doDefault doc'
 * - all this + publish on remote repository by typing 'jerkar doDefault doc publish'
 * 
 * @author Jerome Angibaud
 */
public class MavenStyleBuild extends JkRun {

    public final JkPluginJava javaPlugin = getPlugin(JkPluginJava.class);

    @Override
    protected void setup() {
        javaPlugin.getProject()
                .setVersionedModule("org.jerkar:script-samples", "0.3-SNAPSHOT")
                .addDependencies(dependencies());
        javaPlugin.getProject().getMaker().setDownloadRepos(JkRepoSet.of(JkRepo.of("http://my.repo1"), JkRepo.ofMavenCentral()));
        javaPlugin.getProject().getMaker().getTasksForPublishing().setPublishRepos(publishRepositories());

    }

    JkDependencySet dependencies() {
        return JkDependencySet.of()
                .and(GUAVA, "21.0")	// Popular modules are available as Java constant
                .and(JERSEY_SERVER, "1.19")
                .and(JUNIT, "4.11", TEST);
    }

    static JkResourceProcessor.JkInterpolator interpolator() {
        Map<String, String> varReplacement = new HashMap<>();
        varReplacement.put("${server.ip}", "123.213.111.12");
        return JkResourceProcessor.JkInterpolator.of(JkPathMatcher.of("**/*.xml"), varReplacement);
    }

    JkRepoSet publishRepositories() {
        return JkRepoSet.ofOssrhSnapshotAndRelease("myusername", "mypasword");
    }

}