package studio.kmp.agent.scaffold

import com.samskivert.mustache.Mustache
import java.io.File
import java.io.InputStreamReader

class ProjectScaffolder {

    // (templateResource, outputRelativePath) — paths support {{packagePath}} substitution
    private fun manifestFor(ctx: TemplateContext): List<Pair<String, String>> = buildList {
        // ── Common ────────────────────────────────────────────────────────────
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

    suspend fun scaffold(
        ctx: TemplateContext,
        projectDir: File,
        onProgress: suspend (filePath: String, done: Boolean, error: String?) -> Unit
    ) {
        val mustacheMap = ctx.toMustacheMap()

        manifestFor(ctx).forEach { (resource, rawOutputPath) ->
            runCatching {
                val outputPath = rawOutputPath.replace("{{packagePath}}", ctx.packagePath)
                val outputFile = File(projectDir, outputPath)
                outputFile.parentFile?.mkdirs()

                val rendered = renderTemplate(ctx.architecture, resource, mustacheMap)
                outputFile.writeText(rendered)
                onProgress(outputPath, false, null)
            }.onFailure { e ->
                onProgress(rawOutputPath, false, e.message)
            }
        }

        // Copy Gradle wrapper from kmpstudio, then pin the exact version the AGP requires
        copyGradleWrapper(projectDir)
        writeGradleWrapperProperties(projectDir, ctx.gradleVersion)
        writeLocalProperties(projectDir)

        onProgress("", true, null)
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
            ?: File(System.getProperty("user.dir"))

        // Copy only the wrapper JAR — NOT kmpstudio's custom gradlew (which hardcodes a Gradle path)
        runCatching {
            val wrapperJar = File(kmpRoot, "gradle/wrapper/gradle-wrapper.jar")
            if (wrapperJar.exists()) {
                val targetWrapper = File(projectDir, "gradle/wrapper")
                targetWrapper.mkdirs()
                wrapperJar.copyTo(File(targetWrapper, "gradle-wrapper.jar"), overwrite = true)
            }
        }

        // Write a proper standard gradlew script that reads gradle-wrapper.properties
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText(STANDARD_GRADLEW)
        gradlew.setExecutable(true)
    }

    companion object {
        // Standard Gradle wrapper script — reads gradle-wrapper.properties to determine which Gradle to download.
        // Written verbatim so we never copy kmpstudio's custom hardcoded gradlew.
        private val STANDARD_GRADLEW = """
            #!/bin/sh
            set -e
            APP_HOME=${'$'}( cd "${'$'}( dirname "${'$'}0" )" && pwd -P )
            exec java \
              -classpath "${'$'}APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
              org.gradle.wrapper.GradleWrapperMain "${'$'}@"
        """.trimIndent() + "\n"
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
