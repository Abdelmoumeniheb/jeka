package org.jerkar.tool.builtins.intellij;


import org.jerkar.api.depmanagement.JkDependencySet;
import org.jerkar.api.ide.intellij.JkImlGenerator;
import org.jerkar.api.project.java.JkJavaProject;
import org.junit.Test;

import java.nio.file.Paths;

import static org.jerkar.api.depmanagement.JkJavaDepScopes.PROVIDED;
import static org.jerkar.api.depmanagement.JkPopularModules.*;

public class JkImlGeneratorTest {

    @Test
    public void withoutJavaProject() {
        JkImlGenerator imlGenerator = new JkImlGenerator(Paths.get(""));
        String result = imlGenerator.generate();
        System.out.println(result);
    }

    @Test
    public void withJavaProject() {
        JkJavaProject project = new JkJavaProject(Paths.get(""));
        project.setDependencies(dependencies());
       // project.maker().setDependencyResolver(JkDependencyResolver.of(JkRepo.maven("http://194.253.70.251:8081/nexus/content/groups/multipharma")));
        JkImlGenerator imlGenerator = new JkImlGenerator(project);
        String result = imlGenerator.generate();
        System.out.println(result);
    }

    private JkDependencySet dependencies() {
        return JkDependencySet.of()
                .and(GUAVA, "21.0")
                .and(JAVAX_SERVLET_API, "3.1.0", PROVIDED)
                .and(JUNIT, "4.11")
                .and(MOCKITO_ALL, "1.10.19");
    }

}