package com.wutsi.kokibot.marketplace

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.slf4j.LoggerFactory
import java.io.File

class GitSkillFinder {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(GitSkillFinder::class.java)
    }

    /**
     * Return the list of `SKILL.md` files found in the given repository
     */
    fun find(repoUrl: String, baseDirectory: File): List<File> {
        val repoName = if (repoUrl.endsWith(".git")) {
            repoUrl.substringAfterLast("/").removeSuffix(".git")
        } else {
            repoUrl.substringAfterLast("/")
        }
        val localRepoPath = File(baseDirectory, repoName)
        if (!localRepoPath.exists()) {
            localRepoPath.mkdirs()
        }

        // 1. Sync the repository
        if (File(localRepoPath, ".git").exists()) {
            update(localRepoPath)
        } else {
            clone(repoUrl, localRepoPath)
        }

        // 2. Find all SKILL.md files in the local clone
        return findAllSkillFiles(localRepoPath)
    }

    private fun clone(repoUrl: String, localRepoPath: File) {
        LOGGER.info("Cloning $repoUrl to $localRepoPath")
        val remoteRefs = Git.lsRemoteRepository().setRemote(repoUrl).call()
        val headRef = remoteRefs.find { it.name == Constants.HEAD }
        val defaultBranch = headRef?.target?.name?.removePrefix("refs/heads/") ?: "main"

        Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(localRepoPath)
            .setBranch(defaultBranch)
            .call()
            .close()
    }

    private fun update(localRepoPath: File) {
        LOGGER.info("Updating $localRepoPath")
        Git.open(localRepoPath).use { it.pull().call() }
    }

    private fun findAllSkillFiles(root: File): List<File> {
        val skillFiles = mutableListOf<File>()

        // Recursive walk through the directory
        root.walkTopDown().forEach { file ->
            if (file.isFile && file.name.equals("SKILL.md", ignoreCase = false)) {
                skillFiles.add(file)
            }
        }

        return skillFiles
    }
}
