import dev.jeka.core.api.depmanagement.JkDependencySet;
import dev.jeka.core.api.tooling.intellij.JkImlGenerator;
import dev.jeka.core.samples.JavaPluginBuild;
import dev.jeka.core.tool.JkClass;
import dev.jeka.core.tool.JkDefImport;
import dev.jeka.core.tool.JkInit;
import dev.jeka.core.tool.builtins.project.JkPluginProject;

/**
 * Simple build demonstrating how Jeka can handle multi-project build.
 * <p>
 * Here, the project depends on the <code>dev.jeka.core-samples</code> sibling project.
 * <p>
 * Compilation depends on a jar produced by <code>dev.jeka.core-samples</code>
 * project and from its transitive dependencies.
 * 
 * @author Jerome Angibaud
 * 
 * @formatter:off
 */
public class NormalJarBuild extends JkClass {

    JkPluginProject projectPlugin = getPlugin(JkPluginProject.class);

    /*
     *  Creates a sample build instance of the 'org.jerkar.samples' project.
     *  The 'samples' project path must be relative to this one.
     *  So in this case, the two projects are supposed to lie in the same folder.
     */
    @JkDefImport("../dev.jeka.samples.basic")
    private JavaPluginBuild sampleBuild;


    @Override
    protected void setup() {
        projectPlugin.getProject()
            .getPublication()
                .getArtifactProducer()
                    .putMainArtifact(projectPlugin.getProject().getConstruction()::createFatJar).__.__
            .simpleFacade()
                .setCompileDependencies(deps -> deps
                        .and(sampleBuild.projectPlugin.getProject().toDependency()));
    }

    public void cleanPack() {
        clean();
        projectPlugin.pack();
    }

    public void printIml() {
        JkImlGenerator imlGenerator = JkImlGenerator.of(projectPlugin.getJavaIdeSupport())
                .setDefDependencies(JkDependencySet.of(sampleBuild.projectPlugin.getProject().toDependency()))
                .setDefDependencyResolver(this.getDefDependencyResolver());
        System.out.println(imlGenerator.generate());
    }

    public static void main(String[] args) {
        JkInit.instanceOf(NormalJarBuild.class).cleanPack();
    }

}