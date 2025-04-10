package dev.jeka.core.api.java;

import dev.jeka.core.api.file.JkPathTree;
import dev.jeka.core.api.file.JkPathTreeSet;
import dev.jeka.core.api.system.JkLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class JkJavadocMakerRunner {

    public static void main(String[] args) throws Exception {
        Path srcDir = Paths.get(JkJavadocProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("../../../src/main/java").normalize();
        JkPathTreeSet sources = JkPathTreeSet.ofRoots(srcDir);
        Path out = Files.createTempDirectory("jekatest");
        JkLog.setDecorator(JkLog.Style.INDENT);
        JkLog.setVerbosity(JkLog.Verbosity.VERBOSE);
        Iterable<Path> classpath = JkPathTree.of(srcDir.resolve("jeka/libs/compile+po").normalize()).getFiles();
        JkJavadocProcessor.of()
                .make(classpath, sources, out);
    }
}
