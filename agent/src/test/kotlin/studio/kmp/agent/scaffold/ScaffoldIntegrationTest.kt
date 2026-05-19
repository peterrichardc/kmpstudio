package studio.kmp.agent.scaffold

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class ScaffoldIntegrationTest {

    @Test
    fun `scaffold android clean project builds successfully`() {
        val tempDir = Files.createTempDirectory("kmp-scaffold-test").toFile()
        try {
            val ctx = TemplateContext(
                projectName  = "TestApp",
                packageName  = "com.example.testapp",
                packagePath  = "com/example/testapp",
                architecture = "clean",
                android      = true,
                ios          = true,
                desktop      = false,
                web          = false,
                ktor         = false,
                sqldelight   = false,
                datastore    = false,
                koin         = false,
                coil         = false,
                voyager      = false,
                molecule     = false
            )

            runBlocking {
                ProjectScaffolder().scaffold(ctx, tempDir) { path, done, error ->
                    if (error != null) println("  [scaffold error] $error")
                    else if (!done) println("  created: $path")
                }
            }

            // Verify wrapper files are present
            assertTrue(File(tempDir, "gradlew").exists(),                          "gradlew missing")
            assertTrue(File(tempDir, "gradle/wrapper/gradle-wrapper.jar").exists(),"gradle-wrapper.jar missing")
            assertTrue(File(tempDir, "gradle/wrapper/gradle-wrapper.properties").exists(), "gradle-wrapper.properties missing")
            assertTrue(File(tempDir, "androidApp/build.gradle.kts").exists(),      "androidApp/build.gradle.kts missing")

            val wrapperProps = File(tempDir, "gradle/wrapper/gradle-wrapper.properties").readText()
            assertTrue("gradle-8.14.3" in wrapperProps, "Expected gradle-8.14.3 in wrapper properties")

            // Run the actual build
            println("\n▶ Running :androidApp:assembleDebug …")
            val proc = ProcessBuilder(
                File(tempDir, "gradlew").absolutePath,
                ":androidApp:assembleDebug",
                "--no-daemon",
                "--stacktrace"
            ).directory(tempDir)
             .redirectErrorStream(true)
             .start()

            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()

            println(output)
            assertEquals(0, exitCode, "Build failed. Output above ↑")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
