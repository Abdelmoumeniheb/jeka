import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.project.JkProjectPackaging;
import dev.jeka.core.tool.*;
import dev.jeka.core.tool.builtins.project.ProjectKBean;

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
class NormalJarKBean extends KBean {

    @JkInject
    private ProjectKBean projectKBean;

    /*
     *  Creates a sample build instance of the 'dev.jeka.samples' project.
     *  The 'samples' project path must be relative to this one.
     *  So in this case, the two projects are supposed to lie in the same folder.
     */
    @JkInject("../samples.basic")
    private JkRunbase basicRunbase;

    @JkPostInit
    private void postInit(ProjectKBean projectKBean) {
        JkProject project = projectKBean.project;
        project.flatFacade.setMainArtifactJarType(JkProjectPackaging.JarType.FAT);
        project.compilation.dependencies
                .add(basicRunbase.load(ProjectKBean.class).project.toDependency());
    }

    public void cleanPack() {
        cleanOutput();
        projectKBean.pack();
    }

    public static void main(String[] args) {
        JkInit.kbean(NormalJarKBean.class).cleanPack();
    }

}
