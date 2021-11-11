package dev.jeka.core.tool.builtins.repos;

import dev.jeka.core.api.depmanagement.JkRepo;
import dev.jeka.core.api.depmanagement.publication.JkNexusRepos;
import dev.jeka.core.api.project.JkProject;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.utils.JkUtilsString;
import dev.jeka.core.tool.JkClass;
import dev.jeka.core.tool.JkDoc;
import dev.jeka.core.tool.JkPlugin;
import dev.jeka.core.tool.builtins.project.JkPluginProject;

import java.util.Optional;

public class JkPluginNexus extends JkPlugin {

    private static final String TASK_NAME = "Closing and releasing repositories";

    @JkDoc("Close and Release automatically repository after publish.")
    public boolean closeAndRelease = true;

    @JkDoc("Comma separated filters for taking in account only specified repositories with specified profile name.")
    public String profileNamesFilter = "";

    protected JkPluginNexus(JkClass jkClass) {
        super(jkClass);
    }

    @Override
    protected void afterSetup() throws Exception {
        JkPluginProject projectPlugin = getJkClass().getPlugins().getOptional(JkPluginProject.class).orElse(null);
        if (projectPlugin == null) {
            JkLog.warn("No project plugin configured here.");
            return;
        }
        String[] profileNames = JkUtilsString.isBlank(profileNamesFilter) ? new String[0]
                : profileNamesFilter.split(",");
        projectPlugin.getProject().getPublication().getPostActions().append(TASK_NAME, () -> {
            JkRepo repo = getFirst(projectPlugin.getProject());
            if (repo != null) {
                JkNexusRepos.ofUrlAndCredentials(repo).closeAndReleaseOpenRepositories(profileNames);
            } else {
                JkLog.warn("No remote repository configured for publishing");
            }
        });
    }

    public void closeAndOrRelease() {
        Optional<JkPluginProject> projectPlugin = getJkClass().getPlugins().getOptional(JkPluginProject.class);
        if (!projectPlugin.isPresent()) {
            JkLog.warn("No project plugin configured here.");
            return;
        }
        String[] profileNames = profileNamesFilter.split(",");
        JkRepo repo = getFirst(projectPlugin.get().getProject());
        if (repo != null) {
            JkNexusRepos.ofUrlAndCredentials(repo).closeAndRelease(profileNames);
        } else {
            JkLog.warn("No remote repository configured for publishing");
        }
    }

    public static void configureForOSSRHRepo(JkProject project, String ...profileNames) {
        JkRepo repo = project.getPublication().getMaven().getRepos()
                .getRepoConfigHavingUrl(JkRepo.MAVEN_OSSRH_DEPLOY_RELEASE);
        configureForRepo(project, repo, profileNames);
    }

    public static void configureForFirstRemoteRepo(JkProject project, String ...profileNames) {
        JkRepo repo = getFirst(project);
        configureForRepo(project, repo, profileNames);
    }

    public static void configureForRepo(JkProject project, JkRepo repo, String ...profileNames) {
        if (repo == null) {
            JkLog.warn("No Nexus OSSRH repo found.");
            return;
        }
        JkNexusRepos nexusRepos = JkNexusRepos.ofUrlAndCredentials(repo);
        configureForRepo(project, nexusRepos, profileNames);
    }

    public static void configureForRepo(JkProject project, JkNexusRepos nexusRepos, String ...profileNames) {
        project.getPublication().getPostActions().append(TASK_NAME,
                () -> nexusRepos.closeAndReleaseOpenRepositories(profileNames));
    }

    private static JkRepo getFirst(JkProject project) {
        JkRepo repo = project.getPublication().findFirstRepo();
        if (repo != null && repo.getCredentials() == null || repo.getCredentials().isEmpty()) {
            JkLog.warn("No credentials found on repo " + repo);
        }
        return repo;
    }

}