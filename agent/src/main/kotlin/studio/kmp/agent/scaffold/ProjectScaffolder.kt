package studio.kmp.agent.scaffold

import com.samskivert.mustache.Mustache
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.InputStreamReader

class ProjectScaffolder {

    // (templateResource, outputRelativePath) — paths support {{packagePath}} substitution
    private fun manifestFor(ctx: TemplateContext): List<Pair<String, String>> {
        if (ctx.isLibrary) return manifestForLibrary(ctx)
        return buildList {
            // ── Common ────────────────────────────────────────────────────────────
            add("common/gitignore" to ".gitignore")
            add("common/settings.gradle.kts" to "settings.gradle.kts")
            add("common/root.build.gradle.kts" to "build.gradle.kts")
            add("common/gradle.properties" to "gradle.properties")
            add("common/shared.build.gradle.kts" to "shared/build.gradle.kts")
            add("common/shared.Platform.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/Platform.kt")

            // ── Targets ───────────────────────────────────────────────────────────
            if (ctx.android) {
                add("common/android.build.gradle.kts" to "androidApp/build.gradle.kts")
                add("common/android.manifest" to "androidApp/src/main/AndroidManifest.xml")
                add("common/android.MainActivity.kt" to "androidApp/src/main/kotlin/{{packagePath}}/MainActivity.kt")
                add("common/android.Platform.kt" to "shared/src/androidMain/kotlin/{{packagePath}}/Platform.android.kt")
            }
            if (ctx.ios) {
                add("common/ios.Platform.kt" to "shared/src/iosMain/kotlin/{{packagePath}}/Platform.ios.kt")
                add("common/ios.MainViewController.kt" to "shared/src/iosMain/kotlin/{{packagePath}}/MainViewController.kt")
                add("common/ios.project.pbxproj" to "iosApp/iosApp.xcodeproj/project.pbxproj")
                add("common/ios.workspace.contents" to "iosApp/iosApp.xcodeproj/project.xcworkspace/contents.xcworkspacedata")
                add("common/ios.Config.xcconfig" to "iosApp/Configuration/Config.xcconfig")
                add("common/ios.iOSApp.swift" to "iosApp/iosApp/iOSApp.swift")
                add("common/ios.ContentView.swift" to "iosApp/iosApp/ContentView.swift")
                add("common/ios.Info.plist" to "iosApp/iosApp/Info.plist")
                add("common/ios.Assets.Contents.json" to "iosApp/iosApp/Assets.xcassets/Contents.json")
                add("common/ios.AccentColor.Contents.json" to "iosApp/iosApp/Assets.xcassets/AccentColor.colorset/Contents.json")
                add("common/ios.AppIcon.Contents.json" to "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json")
                add("common/ios.PreviewAssets.Contents.json" to "iosApp/iosApp/Preview Content/Preview Assets.xcassets/Contents.json")
            }
            if (ctx.desktop) {
                add("common/desktop.build.gradle.kts" to "desktopApp/build.gradle.kts")
                add("common/desktop.Main.kt" to "desktopApp/src/main/kotlin/{{packagePath}}/Main.kt")
                add("common/desktop.Platform.kt" to "shared/src/desktopMain/kotlin/{{packagePath}}/Platform.desktop.kt")
            }
            if (ctx.web) {
                add("common/web.Platform.kt" to "shared/src/wasmJsMain/kotlin/{{packagePath}}/Platform.wasmJs.kt")
            }

            // ── Architecture-specific ─────────────────────────────────────────────
            val arch = ctx.architecture
            when (ctx.architecture) {
                "clean" -> {
                    add("$arch/App.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/App.kt")
                    add("$arch/domain.Greeting.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/domain/model/Greeting.kt")
                    add("$arch/domain.GreetingRepository.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/domain/repository/GreetingRepository.kt")
                    add("$arch/domain.GetGreetingUseCase.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/domain/usecase/GetGreetingUseCase.kt")
                    add("$arch/data.GreetingRepositoryImpl.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/data/repository/GreetingRepositoryImpl.kt")
                }
                "mvi" -> {
                    add("$arch/App.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/App.kt")
                    add("$arch/HomeContract.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/feature/home/HomeContract.kt")
                    add("$arch/HomeStore.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/feature/home/HomeStore.kt")
                }
                "mvvm" -> {
                    add("$arch/App.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/App.kt")
                    add("$arch/AppState.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/model/AppState.kt")
                    add("$arch/HomeViewModel.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/viewmodel/HomeViewModel.kt")
                }
            }
        }
    }

    private fun manifestForLibrary(ctx: TemplateContext): List<Pair<String, String>> = buildList {
        add("common/gitignore" to ".gitignore")
        add("library/settings.gradle.kts" to "settings.gradle.kts")
        add("library/root.build.gradle.kts" to "build.gradle.kts")
        add("common/gradle.properties" to "gradle.properties")
        add("library/shared.build.gradle.kts" to "shared/build.gradle.kts")
        add("common/shared.Platform.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/Platform.kt")
        add("library/SdkClient.kt" to "shared/src/commonMain/kotlin/{{packagePath}}/SdkClient.kt")

        if (ctx.android) {
            add("common/android.Platform.kt" to "shared/src/androidMain/kotlin/{{packagePath}}/Platform.android.kt")
        }
        if (ctx.ios) {
            add("common/ios.Platform.kt" to "shared/src/iosMain/kotlin/{{packagePath}}/Platform.ios.kt")
        }
        if (ctx.desktop) {
            add("common/desktop.Platform.kt" to "shared/src/desktopMain/kotlin/{{packagePath}}/Platform.desktop.kt")
        }
        if (ctx.web) {
            add("common/web.Platform.kt" to "shared/src/wasmJsMain/kotlin/{{packagePath}}/Platform.wasmJs.kt")
        }
    }

    suspend fun scaffold(
        ctx: TemplateContext,
        projectDir: File,
        onProgress: suspend (filePath: String, done: Boolean, error: String?) -> Unit
    ) {
        val mustacheMap = ctx.toMustacheMap()
        var errorCount = 0

        manifestFor(ctx).forEach { (resource, rawOutputPath) ->
            runCatching {
                val outputPath = rawOutputPath.replace("{{packagePath}}", ctx.packagePath)
                val outputFile = File(projectDir, outputPath)
                outputFile.parentFile?.mkdirs()

                val rendered = renderTemplate(ctx.architecture, resource, mustacheMap)
                outputFile.writeText(rendered)
                onProgress(outputPath, false, null)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                errorCount++
                onProgress(rawOutputPath, false, e.message)
            }
        }

        // Copy Gradle wrapper from kmpstudio, then pin the exact version the AGP requires.
        // Wrapped so a failure here still emits done=true — otherwise the wizard hangs forever.
        listOf(
            "gradle wrapper"            to { copyGradleWrapper(projectDir) },
            "gradle-wrapper.properties" to { writeGradleWrapperProperties(projectDir, ctx.gradleVersion) },
            "local.properties"          to { writeLocalProperties(projectDir) },
        ).forEach { (label, action) ->
            runCatching { action() }.onFailure { e ->
                if (e is CancellationException) throw e
                errorCount++
                onProgress(label, false, e.message)
            }
        }

        val finalError = if (errorCount > 0) "$errorCount file(s) failed to generate" else null
        onProgress("", true, finalError)
    }

    private fun writeLocalProperties(projectDir: File) {
        val localProps = File(projectDir, "local.properties")
        if (localProps.exists()) return
        val sdkDir = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: "${System.getProperty("user.home")}/Library/Android/sdk"
        localProps.writeText("sdk.dir=${sdkDir.replace("\\", "/")}\n")
    }

    private fun writeGradleWrapperProperties(projectDir: File, gradleVersion: String) {
        val propsFile = File(projectDir, "gradle/wrapper/gradle-wrapper.properties")
        propsFile.parentFile?.mkdirs()
        propsFile.writeText(
            "distributionBase=GRADLE_USER_HOME\n" +
            "distributionPath=wrapper/dists\n" +
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-${gradleVersion}-bin.zip\n" +
            "networkTimeout=10000\n" +
            "validateDistributionUrl=true\n" +
            "zipStoreBase=GRADLE_USER_HOME\n" +
            "zipStorePath=wrapper/dists\n"
        )
    }

    private fun copyGradleWrapper(projectDir: File) {
        // Walk up from agent classes dir to find kmpstudio root (has gradle/wrapper/gradle-wrapper.jar)
        val kmpRoot = generateSequence(File(javaClass.protectionDomain?.codeSource?.location?.toURI())) {
            it.parentFile
        }.take(10).firstOrNull { File(it, "gradle/wrapper/gradle-wrapper.jar").exists() }
            ?: error("gradle-wrapper.jar not found in kmpstudio install tree — wrapper copy aborted")

        // Copy only the wrapper JAR — NOT kmpstudio's custom gradlew (which hardcodes a Gradle path)
        val wrapperJar = File(kmpRoot, "gradle/wrapper/gradle-wrapper.jar")
        val targetWrapper = File(projectDir, "gradle/wrapper")
        targetWrapper.mkdirs()
        wrapperJar.copyTo(File(targetWrapper, "gradle-wrapper.jar"), overwrite = true)

        // POSIX launcher
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText(STANDARD_GRADLEW)
        gradlew.setExecutable(true)

        // Windows launcher
        File(projectDir, "gradlew.bat").writeText(STANDARD_GRADLEW_BAT)
    }

    companion object {
        // Standard Gradle wrapper scripts — honour JAVA_HOME / JAVA_OPTS / GRADLE_OPTS like the
        // official scripts; just much smaller. Written verbatim so we never copy kmpstudio's
        // custom hardcoded gradlew.
        private val STANDARD_GRADLEW = """
            #!/bin/sh
            set -e
            APP_HOME=${'$'}( cd "${'$'}( dirname "${'$'}0" )" && pwd -P )
            if [ -n "${'$'}JAVA_HOME" ]; then
                JAVACMD="${'$'}JAVA_HOME/bin/java"
            else
                JAVACMD="java"
            fi
            exec "${'$'}JAVACMD" ${'$'}JAVA_OPTS ${'$'}GRADLE_OPTS \
              -classpath "${'$'}APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
              org.gradle.wrapper.GradleWrapperMain "${'$'}@"
        """.trimIndent() + "\n"

        private val STANDARD_GRADLEW_BAT = """
            @echo off
            setlocal
            set DIRNAME=%~dp0
            if defined JAVA_HOME (
                set JAVA_EXE=%JAVA_HOME%\bin\java.exe
            ) else (
                set JAVA_EXE=java
            )
            "%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
            endlocal
        """.trimIndent() + "\r\n"
    }

    private fun renderTemplate(arch: String, resource: String, ctx: Map<String, Any>): String {
        // Check user override directory first
        val userFile = File(
            System.getProperty("user.home"),
            ".kmpstudio/templates/$resource.mustache"
        )
        val source: String = if (userFile.exists()) {
            userFile.readText()
        } else {
            val stream = javaClass.getResourceAsStream("/templates/$resource.mustache")
                ?: error("Template not found: /templates/$resource.mustache")
            InputStreamReader(stream).readText()
        }

        return Mustache.compiler().compile(source).execute(ctx)
    }
}
