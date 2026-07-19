package anikuta.buildlogic

import org.gradle.api.Project

fun Project.getBuildTime(): String =
    System.currentTimeMillis().toString()

fun Project.getCommitCount(): String =
    runCatching {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    }.getOrDefault("1")

fun Project.getGitSha(): String =
    runCatching {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    }.getOrDefault("unknown")
