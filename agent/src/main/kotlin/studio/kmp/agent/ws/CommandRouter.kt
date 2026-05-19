package studio.kmp.agent.ws

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import studio.kmp.agent.executor.CliExecutor
import studio.kmp.agent.executor.EmulatorManager
import studio.kmp.shared.model.*
import java.io.File

class CommandRouter(
    private val executor:        CliExecutor,
    private val emulatorManager: EmulatorManager,
    private val sessionManager:  SessionManager,
    private val scope:           CoroutineScope,
    private val json:            Json
) {
    fun route(cmd: WsMessage.Command, send: suspend (String) -> Unit) {
        when (cmd.type) {

            CommandType.STOP -> {
                val cancelled = sessionManager.cancel(cmd.id)
                if (!cancelled) scope.launch {
                    send(encode(WsMessage.ErrorMsg("No active session: id=${cmd.id}")))
                }
            }

            CommandType.RUN -> {
                val job = scope.launch {
                    val serial = ensureDevice(cmd.id, send)
                    if (serial == null) {
                        send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                        return@launch
                    }
                    val workDir = cmd.workDir
                    if (workDir == null) {
                        send(encode(WsMessage.ErrorMsg("No project directory — open a project first.")))
                        send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                        return@launch
                    }

                    // ── Step 1: Build APK ─────────────────────────────────────
                    val buildOk = buildApk(cmd.id, workDir, send)
                    if (!buildOk) {
                        send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                        return@launch
                    }

                    // ── Step 2: Find APK ──────────────────────────────────────
                    val apk = findApk(workDir)
                    if (apk == null) {
                        send(encode(WsMessage.ErrorMsg("APK not found after build. Check that at least one Android module ran assembleDebug successfully.")))
                        send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                        return@launch
                    }
                    send(encode(WsMessage.Output(commandId = cmd.id, chunk = "▶ Installing ${apk.name} on $serial…")))

                    // ── Step 3: Install & launch ──────────────────────────────
                    executor.execute(listOf("run", "--apks", apk.absolutePath, "--device", serial), workDir)
                        .catch { e -> send(encode(WsMessage.ErrorMsg(e.message ?: "Run error"))) }
                        .collect { line ->
                            send(encode(WsMessage.Output(
                                commandId = cmd.id, chunk = line.text,
                                stream    = if (line.isError) StreamType.STDERR else StreamType.STDOUT
                            )))
                        }

                    send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                    sessionManager.cancel(cmd.id)
                }
                sessionManager.register(cmd.id, job)
            }

            CommandType.START_EMULATOR -> {
                val avdName = cmd.args.firstOrNull() ?: run {
                    scope.launch { send(encode(WsMessage.ErrorMsg("START_EMULATOR: avd name required"))) }
                    return
                }
                val job = scope.launch {
                    emulatorManager.autoStart(avdName) { phase, msg ->
                        send(encode(WsMessage.EmulatorStatus(
                            phase   = phase,
                            avdName = avdName,
                            serial  = if (phase == "ready") msg else null,
                            message = if (phase == "ready") "Emulator ready" else msg
                        )))
                    }
                    send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                    sessionManager.cancel(cmd.id)
                }
                sessionManager.register(cmd.id, job)
            }

            CommandType.STOP_EMULATOR -> {
                val serial = cmd.args.firstOrNull() ?: run {
                    scope.launch { send(encode(WsMessage.ErrorMsg("STOP_EMULATOR: serial required"))) }
                    return
                }
                scope.launch {
                    emulatorManager.stopEmulator(serial)
                    send(encode(WsMessage.EmulatorStatus(
                        phase   = "stopped",
                        serial  = serial,
                        message = "Emulator stopped"
                    )))
                }
            }

            CommandType.LIST_AVDS -> {
                scope.launch {
                    send(encode(emulatorManager.buildAvdList()))
                }
            }

            CommandType.CREATE_AVD -> {
                val name = cmd.args.firstOrNull() ?: run {
                    scope.launch { send(encode(WsMessage.ErrorMsg("CREATE_AVD: name required"))) }
                    return
                }
                val job = scope.launch {
                    emulatorManager.createAvd(name) { line ->
                        send(encode(WsMessage.Output(commandId = cmd.id, chunk = line)))
                    }
                    send(encode(emulatorManager.buildAvdList()))
                    send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                    sessionManager.cancel(cmd.id)
                }
                sessionManager.register(cmd.id, job)
            }

            CommandType.BUILD -> {
                val job = scope.launch {
                    val workDir = cmd.workDir ?: run {
                        send(encode(WsMessage.ErrorMsg("No project directory — open a project first.")))
                        send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                        return@launch
                    }
                    buildApk(cmd.id, workDir, send)
                    send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                    sessionManager.cancel(cmd.id)
                }
                sessionManager.register(cmd.id, job)
            }

            CommandType.LOGCAT -> {
                val job = scope.launch {
                    val serials = emulatorManager.listOnlineSerials()
                    val adbArgs = mutableListOf("adb")
                    if (serials.isNotEmpty()) adbArgs += listOf("-s", serials.first())
                    adbArgs += listOf("logcat", "--format=threadtime") + cmd.args
                    executor.executeRaw(adbArgs)
                        .catch { e -> send(encode(WsMessage.ErrorMsg(e.message ?: "Logcat error"))) }
                        .collect { line ->
                            send(encode(WsMessage.Output(
                                commandId = cmd.id,
                                chunk     = line.text,
                                stream    = if (line.isError) StreamType.STDERR else StreamType.STDOUT
                            )))
                        }
                    send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                    sessionManager.cancel(cmd.id)
                }
                sessionManager.register(cmd.id, job)
            }

            else -> {
                val args = buildArgs(cmd)
                val job = scope.launch {
                    executor.execute(args, cmd.workDir)
                        .catch { e -> send(encode(WsMessage.ErrorMsg(e.message ?: "Process error"))) }
                        .collect { line ->
                            send(encode(WsMessage.Output(
                                commandId = cmd.id,
                                chunk     = line.text,
                                stream    = if (line.isError) StreamType.STDERR else StreamType.STDOUT
                            )))
                        }
                    send(encode(WsMessage.Output(commandId = cmd.id, chunk = "", done = true)))
                    sessionManager.cancel(cmd.id)
                }
                sessionManager.register(cmd.id, job)
            }
        }
    }

    private suspend fun ensureDevice(commandId: String, send: suspend (String) -> Unit): String? {
        val online = emulatorManager.listOnlineSerials()
        if (online.isNotEmpty()) return online.first()

        val avds = emulatorManager.listAvdNames()
        if (avds.isEmpty()) {
            send(encode(WsMessage.ErrorMsg("No devices or AVDs found. Connect a device or create an AVD in Android Studio.")))
            return null
        }
        val avdName = avds.first()
        send(encode(WsMessage.Output(commandId = commandId, chunk = "No device online — auto-starting: $avdName")))

        return emulatorManager.autoStart(avdName) { phase, msg ->
            send(encode(WsMessage.EmulatorStatus(
                phase   = phase,
                avdName = avdName,
                serial  = if (phase == "ready") msg else null,
                message = if (phase == "ready") "Emulator ready" else msg
            )))
            send(encode(WsMessage.Output(commandId = commandId, chunk = msg)))
        }
    }

    private fun buildArgs(cmd: WsMessage.Command): List<String> = when (cmd.type) {
        CommandType.VERSION -> listOf("--version")
        CommandType.BUILD   -> listOf("build") + cmd.args
        else                -> emptyList()
    }

    private fun ensureLocalProperties(workDir: String) {
        val localProps = File(workDir, "local.properties")
        if (localProps.exists()) return
        val sdkDir = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: "${System.getProperty("user.home")}/Library/Android/sdk"
        localProps.writeText("sdk.dir=${sdkDir.replace("\\", "/")}\n")
    }

    suspend fun runProjectBuild(
        commandId: String,
        workDir: String,
        send: suspend (String) -> Unit
    ): Pair<Boolean, String> {
        ensureLocalProperties(workDir)
        val errors = StringBuilder()
        var buildOk = false
        executor.executeRaw(
            listOf(gradleCmd(workDir), "build", "-x", "test", "--no-daemon"), workDir
        )
            .catch { e -> errors.appendLine("Build error: ${e.message}") }
            .collect { line ->
                send(encode(WsMessage.Output(
                    commandId = commandId,
                    chunk     = line.text,
                    stream    = if (line.isError) StreamType.STDERR else StreamType.STDOUT
                )))
                if (line.text.contains("BUILD SUCCESSFUL")) buildOk = true
                if (isCompilerError(line.text)) errors.appendLine(line.text)
            }
        return buildOk to errors.toString()
    }

    private fun isCompilerError(text: String): Boolean = text.trimStart().let { t ->
        t.startsWith("e: ") || t.startsWith("error:") ||
        t.contains(": error:") || t.contains("BUILD FAILED") ||
        t.contains("FAILURE:")
    }

    private suspend fun buildApk(commandId: String, workDir: String, send: suspend (String) -> Unit): Boolean {
        ensureLocalProperties(workDir)
        val androidModule = detectAndroidModule(workDir)
        send(encode(WsMessage.Output(commandId = commandId, chunk = "▶ Building $androidModule:assembleDebug…")))
        var buildOk = false
        executor.executeRaw(listOf(gradleCmd(workDir), "$androidModule:assembleDebug", "--no-daemon"), workDir)
            .catch { e -> send(encode(WsMessage.ErrorMsg("Build error: ${e.message}"))) }
            .collect { line ->
                send(encode(WsMessage.Output(
                    commandId = commandId, chunk = line.text,
                    stream    = if (line.isError) StreamType.STDERR else StreamType.STDOUT
                )))
                if (line.text.contains("BUILD SUCCESSFUL")) buildOk = true
            }
        if (!buildOk) send(encode(WsMessage.ErrorMsg("Build failed — check log for errors.")))
        return buildOk
    }

    /** Reads settings.gradle.kts to find the first Gradle subproject that contains `android {`. */
    /** Finds the first subproject that applies com.android.application — unambiguous unlike `android {`. */
    private fun detectAndroidModule(workDir: String): String {
        val settings = File(workDir, "settings.gradle.kts").takeIf { it.exists() }
            ?: File(workDir, "settings.gradle").takeIf { it.exists() }
            ?: return ":androidApp"
        return Regex("""include\s*\(\s*["']?:?(\w[\w-]*)["']?\s*\)""")
            .findAll(settings.readText())
            .map { it.groupValues[1].trimStart(':') }
            .firstOrNull { module ->
                File(workDir, module).resolve("build.gradle.kts")
                    .let { it.exists() && it.readText().contains("com.android.application") }
            }?.let { ":$it" } ?: ":androidApp"
    }

    private fun gradleCmd(workDir: String): String {
        val wrapper = File(workDir, "gradlew")
        if (wrapper.canExecute()) return wrapper.absolutePath
        // Make executable if present but not set
        if (wrapper.exists()) { wrapper.setExecutable(true); return wrapper.absolutePath }
        return "gradle"
    }

    private fun findApk(workDir: String): File? =
        File(workDir).walkTopDown()
            .filter { it.extension == "apk" && it.path.contains("outputs/apk/debug") }
            .firstOrNull()

    private fun encode(msg: WsMessage): String =
        json.encodeToString(WsMessage.serializer(), msg)
}
