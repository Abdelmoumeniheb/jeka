import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.project.JkCompileLayout;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.tooling.intellij.JkIml;
import dev.jeka.core.tool.KBean;
import dev.jeka.core.tool.builtins.project.ProjectKBean;
import dev.jeka.core.tool.builtins.tooling.ide.IntellijKBean;
import dev.jeka.core.tool.builtins.tooling.maven.MavenKBean;

class KotlinBuild extends KBean {

    final JkProject project = load(ProjectKBean.class).project;

    KotlinBuild() {
        load(IntellijKBean.class)
                .replaceLibByModule("dev.jeka.jeka-core.jar", "dev.jeka.core")
                .setModuleAttributes("dev.jeka.core", JkIml.Scope.COMPILE, null);
    }

    protected void init() {
        project.flatFacade
            .setModuleId("dev.jeka:kotlin-plugin")
            .setJvmTargetVersion(JkJavaVersion.V8)
            .setLayoutStyle(JkCompileLayout.Style.SIMPLE)
            .setMixResourcesAndSources()
            .dependencies.compile
                    .add(JkLocator.getJekaJarPath());

        load(MavenKBean.class).getMavenPublication()
                    .pomMetadata
                        .setProjectName("Jeka plugin for Kotlin")
                        .setProjectDescription("A Jeka plugin for Kotlin language support")
                        .addGithubDeveloper("djeang", "djeangdev@yahoo.fr");
    }

}