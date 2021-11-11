import dev.jeka.core.api.java.JkJavaVersion;
import dev.jeka.core.api.system.JkLocator;
import dev.jeka.core.tool.JkClass;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.project.JkPluginProject;

public class JacocoPluginBuild extends JkClass {

    private final JkPluginProject projectPlugin = getPlugin(JkPluginProject.class);

    @Override
    protected void setup() {
        projectPlugin.getProject().simpleFacade()
                .setJvmTargetVersion(JkJavaVersion.V8)
                .mixResourcesAndSources()
                .setSimpleLayout()
                .setCompileDependencies(deps -> deps
                        .andFiles(JkLocator.getJekaJarPath())
                );

        projectPlugin.getProject().getPublication().getMaven()
                .setModuleId("dev.jeka:jacoco-plugin")
                .getPomMetadata()
                        .setProjectName("Jeka plugin for Jacoco")
                        .setProjectDescription("A Jeka plugin for Jacoco coverage tool")
                        .addGithubDeveloper("djeang", "djeangdev@yahoo.fr");
    }

    public void cleanPack() {
        clean(); projectPlugin.pack();
    }

    public static void main(String[] args) {
        JkInit.instanceOf(JacocoPluginBuild.class, args).cleanPack();
    }

}