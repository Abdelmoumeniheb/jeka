import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.project.JkCompileLayout;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.project.JkProjectPackaging;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.api.tooling.intellij.JkIml;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.KBean;
import dev.jeka.core.tool.builtins.project.ProjectKBean;
import dev.jeka.core.tool.builtins.tooling.ide.IntellijKBean;
import dev.jeka.core.tool.builtins.tooling.maven.MavenKBean;

class SonarqubeBuild extends KBean {

    private final ProjectKBean projectKBean = load(ProjectKBean.class);

    SonarqubeBuild() {
        load(IntellijKBean.class)
                .replaceLibByModule("dev.jeka.jeka-core.jar", "dev.jeka.core")
                .setModuleAttributes("dev.jeka.core", JkIml.Scope.COMPILE, null);
    }

    protected void init() {
        JkProject project = projectKBean.project;
        project.setJvmTargetVersion(JkJavaVersion.V8).flatFacade
                .setModuleId("dev.jeka:sonarqube-plugin")
                .setMixResourcesAndSources()
                .setLayoutStyle(JkCompileLayout.Style.SIMPLE)
                //.setMainArtifactJarType(JkProjectPackaging.JarType.SHADE)
                .dependencies.compile
                    .add(JkLocator.getJekaJarPath());
        project.flatFacade.dependencies.runtime
                        .remove(JkLocator.getJekaJarPath());
        load(MavenKBean.class).getMavenPublication()
                    .pomMetadata
                        .setProjectName("Jeka plugin for Sonarqube")
                        .setProjectDescription("A Jeka plugin for Jacoco coverage tool")
                        .addGithubDeveloper("djeang", "djeangdev@yahoo.fr");
    }

    public void cleanPack() {
        projectKBean.clean();
        projectKBean.pack();
    }

    public static void main(String[] args) {
        SonarqubeBuild build = JkInit.kbean(SonarqubeBuild.class);
        build.cleanPack();
        build.load(MavenKBean.class).publishLocal();
    }


}