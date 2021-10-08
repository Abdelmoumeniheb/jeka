package dev.jeka.core.api.tooling;

import dev.jeka.core.api.depmanagement.JkVersion;
import dev.jeka.core.api.system.JkLog;
import dev.jeka.core.api.system.JkProcess;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper for Git command line interface. This class assumes Git is installed on the host machine.
 */
public final class JkGitProcess extends JkProcess<JkGitProcess> {

    private JkGitProcess() {
        super("git");
    }

    public static JkGitProcess of(Path dir) {
        return new JkGitProcess().setWorkingDir(dir);
    }

    public static JkGitProcess of(String dir) {
        return of(Paths.get(dir));
    }

    public static JkGitProcess of() {
        return of("");
    }

    public String getCurrentBranch() {
        return this.clone()
                .addParams("rev-parse", "--abbrev-ref", "HEAD")
                .setLogOutput(false)
                .execAndReturnOutput().get(0);
    }

    public boolean isRemoteEqual() {
        Object local = clone().addParams("rev-parse", "@").execAndReturnOutput();
        Object remote = clone().addParams("rev-parse", "@{u}").execAndReturnOutput();
        return local.equals(remote);
    }

    public boolean isWorkspaceDirty() {
        return !clone()
                .addParams("diff", "HEAD", "--stat")
                .setLogOutput(false)
                .execAndReturnOutput().isEmpty();
    }

    public String getCurrentCommit() {
        return clone()
                .addParams("rev-parse", "HEAD")
                .setLogOutput(false)
                .execAndReturnOutput().get(0);
    }

    public List<String> getTagsOfCurrentCommit() {
        return clone()
                .addParams("tag", "-l", "--points-at", "HEAD")
                .setLogOutput(false)
                .execAndReturnOutput();
    }

    public List<String> getLastCommitMessage() {
        return clone()
                .addParams("log", "--oneline", "--format=%B", "-n 1", "HEAD")
                .setLogOutput(false)
                .execAndReturnOutput();
    }

    /**
     * Convenient method to extract information from the last commit message title.
     * This splits title is separated words, then looks for the fist word starting
     * with the specified prefix. The returned suffix is the word found minus the prefix.<p/>
     * This method returns <code>null</code> if no such prefix found.
     *
     * For example, if the title is 'Release:0.9.5.RC1 : Rework Dependencies', then
     * invoking this method with 'Release:' argument will return '0.9.5.RC1'.
     */
    public String extractSuffixFromLastCommitMessage(String prefix) {
        List<String> messageLines = getLastCommitMessage();
        if (messageLines.isEmpty()) {
            return null;
        }
        String[] words = messageLines.get(0).split(" ");
        for (String word : words) {
            if (word.startsWith(prefix)) {
                return word.substring(prefix.length());
            }
        }
        return null;
    }

    public JkGitProcess tagAndPush(String name) {
        tag(name);
        clone().addParams("push", "origin", name).exec();
        return this;
    }

    public JkGitProcess tag(String name) {
        clone().addParams("tag", name).exec();
        return this;
    }

    /**
     * Returns version according the last commit message. If the commit message contains a word
     * starting with the specified prefix keyword then the substring following this suffix will be
     * returned.<br/>
     * If no such prefix found, then a version formatted as [branch]-SNAPSHOT will be returned
     */
    public String getVersionFromCommitMessage(String prefixKeyword) {
        String afterSuffix = extractSuffixFromLastCommitMessage(prefixKeyword);
        if (afterSuffix != null) {
            return afterSuffix;
        }
        String branch;
        try {
            branch = getCurrentBranch();
            return branch + "-SNAPSHOT";
        } catch (IllegalStateException e) {
            JkLog.warn(e.getMessage());
            return JkVersion.UNSPECIFIED.getValue();
        }
    }

    /**
     * @see #getVersionFromTag(String)
     */
    public String getVersionFromTag() {
        return getVersionFromTag("");
    }


    /**
     * If the current commit is tagged then the version is the tag name (last in alphabetical order). Otherwise
     * the version is [branch]-SNAPSHOT
     */
    public String getVersionFromTag(String prefix) {
        List<String> tags;
        String branch;
        try {
            tags = getTagsOfCurrentCommit().stream()
                    .filter(tag -> tag.startsWith(prefix))
                    .collect(Collectors.toList());
            branch = getCurrentBranch();
        } catch (IllegalStateException e) {
            JkLog.warn(e.getMessage());
            return JkVersion.UNSPECIFIED.getValue();
        }
        if (tags.isEmpty()) {
            return branch + "-SNAPSHOT";
        } else {
            return tags.get(tags.size() - 1);
        }
    }

    /**
     * @see #getVersionFromTag()
     */
    public JkVersion getJkVersionFromTag() {
        return JkVersion.of(getVersionFromTag());
    }



}