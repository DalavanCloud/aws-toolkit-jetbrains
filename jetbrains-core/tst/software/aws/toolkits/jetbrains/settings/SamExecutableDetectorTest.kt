// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.settings

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import software.aws.toolkits.core.rules.EnvironmentVariableHelper
import java.io.File

abstract class SamExecutableDetectorTestBase {
    @Rule
    @JvmField
    val envHelper = EnvironmentVariableHelper()

    @Rule
    @JvmField
    val tempFolderRule = TemporaryFolder()

    lateinit var detector: SamExecutableDetector
    lateinit var tempFolder: String

    @Before
    open fun setUp() {
        Assume.assumeTrue(SystemInfo.isUnix)
        envHelper.remove("PATH")

        tempFolder = "${tempFolderRule.newFolder()}${File.separator}"

        detector = object : SamExecutableDetector() {
            override fun file(folder: String, name: String): File {
                // Used to "mount" the expected folders in a temp dir
                val actualPath = super.file(folder, name)
                return File(tempFolder, sanitizePath(actualPath.absolutePath))
            }
        }
    }

    private fun sanitizePath(original: String): String = original.trimStart(File.separatorChar).replace("^(\\w):".toRegex()) { "${it.groupValues[1]}_" }

    private fun unsanitizePath(sanitized: String?): String? {
        val suffix = sanitized?.removePrefix(tempFolder)?.replace("^(\\w)_".toRegex()) { "${it.groupValues[1]}:" }
        return suffix?.let { "${File.separator}$suffix" }
    }

    protected fun assertExecutable(expected: String?) {
        assertThat(unsanitizePath(detector.detect())).isEqualTo(expected)
    }

    protected fun touch(vararg paths: String) {
        for (path in paths) {
            val sanitized = sanitizePath(path)
            assertThat(FileUtil.createIfDoesntExist(File(tempFolder, sanitized))).isTrue()
        }
    }
}

class SamExecutableDetectorUnixTest : SamExecutableDetectorTestBase() {
    @Before
    override fun setUp() {
        Assume.assumeTrue(SystemInfo.isUnix)
        super.setUp()
    }

    @Test
    fun testUserBin() {
        touch("/usr/local/bin/sam")
        assertExecutable("/usr/local/bin/sam")
    }

    @Test
    fun testPath() {
        val tempDirectory = FileUtil.createTempDirectory("tempSam", null)
        val expected = File(tempDirectory, "sam").absolutePath
        touch(expected)
        envHelper["PATH"] = tempDirectory.absolutePath

        assertExecutable(expected)
    }
}

class SamExecutableDetectorWindowsTest : SamExecutableDetectorTestBase() {
    @Before
    override fun setUp() {
        Assume.assumeTrue(SystemInfo.isWindows)
        super.setUp()
    }

    @Test
    fun testProgramFilesX86Cmd() {
        touch("C:\\Program Files (x86)\\Amazon\\AWSSAMCLI\\bin\\sam.cmd")
        assertExecutable("C:\\Program Files (x86)\\Amazon\\AWSSAMCLI\\bin\\sam.cmd")
    }

    @Test
    fun testProgramFilesX86Exe() {
        touch("C:\\Program Files (x86)\\Amazon\\AWSSAMCLI\\bin\\sam.exe")
        assertExecutable("C:\\Program Files (x86)\\Amazon\\AWSSAMCLI\\bin\\sam.exe")
    }

    @Test
    fun testProgramFilesCmd() {
        touch("C:\\Program Files\\Amazon\\AWSSAMCLI\\bin\\sam.cmd")
        assertExecutable("C:\\Program Files\\Amazon\\AWSSAMCLI\\bin\\sam.cmd")
    }

    @Test
    fun testProgramFilesExe() {
        touch("C:\\Program Files\\Amazon\\AWSSAMCLI\\bin\\sam.exe")
        assertExecutable("C:\\Program Files\\Amazon\\AWSSAMCLI\\bin\\sam.exe")
    }

    @Test
    fun testPathCmd() {
        val tempDirectory = FileUtil.createTempDirectory("tempSam", null)
        val expected = File(tempDirectory, "sam.cmd").absolutePath
        touch(expected)
        envHelper["PATH"] = tempDirectory.absolutePath

        assertExecutable(expected)
    }

    @Test
    fun testPathExe() {
        val tempDirectory = FileUtil.createTempDirectory("tempSam", null)
        val expected = File(tempDirectory, "sam.exe").absolutePath
        touch(expected)
        envHelper["PATH"] = tempDirectory.absolutePath

        assertExecutable(expected)
    }
}