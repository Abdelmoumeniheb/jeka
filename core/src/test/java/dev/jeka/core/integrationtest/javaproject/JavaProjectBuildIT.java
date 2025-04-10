package dev.jeka.core.integrationtest.javaproject;

import dev.jeka.core.api.depmanagement.JkTransitivity;
import dev.jeka.core.api.depmanagement.publication.JkIvyPublication;
import dev.jeka.core.api.depmanagement.publication.JkMavenPublication;
import dev.jeka.core.api.file.JkZipTree;
import dev.jeka.core.api.project.JkCompileLayout;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.project.JkProjectPublications;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class JavaProjectBuildIT {

    private static Path unzipToDir(String zipName) throws IOException, URISyntaxException {
        final Path dest = Files.createTempDirectory(JavaProjectBuildIT.class.getName());
        final Path zip = Paths.get(JavaProjectBuildIT.class.getResource(zipName).toURI());
        JkZipTree.of(zip).copyTo(dest);
        System.out.println("unzipped in " + dest);
        return dest;
    }

    @Test
    void publish_maven_ok() throws IOException, URISyntaxException {
        Path root = unzipToDir("sample-multiproject.zip");
        JkProject project = JkProject.of().setBaseDir(root.resolve("base"));
        project.flatFacade.setLayoutStyle(JkCompileLayout.Style.SIMPLE);
        project.compilation.dependencies
                        .add("com.google.guava:guava:23.0")
                        .add("javax.servlet:javax.servlet-api:4.0.1");
        project.packaging.runtimeDependencies
                        .add("org.postgresql:postgresql:42.2.19")
                        .modify(deps -> deps.withTransitivity("com.google.guava:guava", JkTransitivity.RUNTIME))
                        .remove("javax.servlet:javax.servlet-api");
        project.testing.compilation.dependencies
                        .add("org.mockito:mockito-core:2.10.0");
        project
                .setModuleId("my:project").setVersion("MyVersion-snapshot")
                .setVersion("1-SNAPSHOT");
        project.pack();
        JkMavenPublication mavenPublication = JkMavenPublication.of(project.asBuildable()).publishLocal();
        mavenPublication.publishLocal();
        System.out.println(project.getInfo());
        Assertions.assertEquals(JkTransitivity.COMPILE, mavenPublication.getDependencies()
                .get("com.google.guava:guava").getTransitivity());
    }

    @Test
    void publish_ivy_ok() throws IOException, URISyntaxException {
        Path root = unzipToDir("sample-multiproject.zip");
        JkProject project = JkProject.of().setBaseDir(root.resolve("base"));
        project.flatFacade.setLayoutStyle(JkCompileLayout.Style.SIMPLE);
        project.compilation.dependencies
                .add("com.google.guava:guava:23.0")
                .add("javax.servlet:javax.servlet-api:4.0.1");
        project.packaging.runtimeDependencies
                        .add("org.postgresql:postgresql:42.2.19")
                        .modify(deps -> deps.withTransitivity("com.google.guava:guava", JkTransitivity.RUNTIME))
                        .remove("javax.servlet:javax.servlet-api");
       project.testing.compilation.dependencies
                        .add("org.mockito:mockito-core:2.10.0");

        JkIvyPublication ivyPublication = JkProjectPublications.ivyPublication(project)
                .setModuleId("my:module")
                .setVersion("0.1");
        project.pack();
        ivyPublication.publishLocal();
        System.out.println(project.getInfo());
    }

}
