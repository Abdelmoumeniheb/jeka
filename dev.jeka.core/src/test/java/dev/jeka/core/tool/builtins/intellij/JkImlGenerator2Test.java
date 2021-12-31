package dev.jeka.core.tool.builtins.intellij;


import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.file.JkPathSequence;
import dev.jeka.core.api.java.JkClasspath;
import dev.jeka.core.api.project.JkIdeSupport;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.tooling.intellij.JkIml;
import dev.jeka.core.api.tooling.intellij.JkImlGenerator;
import dev.jeka.core.api.tooling.intellij.JkImlGenerator2;
import org.junit.Test;

import java.nio.file.Paths;

import static dev.jeka.core.api.depmanagement.JkPopularModules.*;

public class JkImlGenerator2Test {

    @Test
    public void withoutJavaProject() {
        JkImlGenerator2 imlGenerator = JkImlGenerator2.of()
                .setDefClasspath(JkPathSequence.of(JkClasspath.ofCurrentRuntime()))
                .setBaseDir(Paths.get(""));
        JkIml iml = imlGenerator.computeIml();
        iml.toDoc().print(System.out);
    }

    @Test
    public void withJavaProject() {
        JkProject project = JkProject.of();
        project.getConstruction().getCompilation().configureDependencies(deps -> dependencies());
        JkImlGenerator2 imlGenerator = JkImlGenerator2.of()
                .setIdeSupport(project.getJavaIdeSupport())
                .setDefClasspath(JkPathSequence.of(JkLocator.getJekaJarPath()));
        JkIml iml = imlGenerator.computeIml();
        iml.toDoc().print(System.out);
    }

    private JkDependencySet dependencies() {
        return JkDependencySet.of()
                .and(GUAVA.version("21.0"))
                .and(JAVAX_SERVLET_API.version("3.1.0"))
                .and(JUNIT.version("4.11"))
                .and(MOCKITO_ALL.version("1.10.19"));
    }

}