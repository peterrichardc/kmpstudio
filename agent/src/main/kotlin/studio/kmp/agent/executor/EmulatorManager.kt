package studio.kmp.agent.executor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import studio.kmp.shared.model.AvdInfo
import studio.kmp.shared.model.WsMessage
import java.io.File

class EmulatorManager {

    // ── SDK path resolution ───────────────────────────────────────────────────

    private val sdkHome: File? by lazy {
        val fromEnv = System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }
            ?: System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }
        if (fromEnv != null) return@lazy File(fromEnv)

        // Common macOS / Linux defaults
        listOf(
            "${System.getProperty("user.home")}/Library/Android/sdk",
            "${System.getProperty("user.home")}/Android/Sdk",
            "/opt/android-sdk"
        ).map { File(it) }.firstOrNull { it.isDirectory }
    }

    private fun bin(vararg candidates: String): String {
        for (rel in candidates) {
            val full = sdkHome?.resolve(rel)
            if (full?.canExecute() == true) return full.absolutePath
        }
        return candidates.last().substringAfterLast('/')   // fallback: just the command name
    }

    val adbBinPath            get() = bin("platform-tools/adb", "adb")
    private val adbBin        get() = adbBinPath
    private val emulatorBin   get() = bin("emulator/emulator", "emulator")
    private val avdmanagerBin get() = bin(
        "tools/bin/avdmanager",
        "cmdline-tools/latest/bin/avdmanager",
        "avdmanager"
    )

    // ── AVD listing (reads ~/.android/avd/ — no emulator on PATH required) ──

    fun listAvdNames(): List<String> {
        val avdDir = File(System.getProperty("user.home"), ".android/avd")
        return avdDir.listFiles()
            ?.filter { it.extension == "ini" && !it.name.startsWith(".") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    // ── Online device detection ───────────────────────────────────────────────

    fun listOnlineSerials(): List<String> = runCatching {
        val process = ProcessBuilder(adbBin, "devices").start()
        val result  = process.inputStream.bufferedReader().readLines()
            .drop(1)
            .filter { it.contains("\tdevice") }
            .map { it.split("\t").first().trim() }
        process.waitFor()
        result
    }.getOrDefault(emptyList())

    private fun avdNameForSerial(serial: String): String? = runCatching {
        val process = ProcessBuilder(adbBin, "-s", serial, "emu", "avd", "name").start()
        val name    = process.inputStream.bufferedReader().readLine()?.trim()
        process.waitFor()
        name
    }.getOrNull()

    fun buildAvdList(): WsMessage.AvdList {
        val avdNames        = listAvdNames()
        val onlineSerials   = listOnlineSerials()
        val emulatorSerials = onlineSerials.filter { it.startsWith("emulator-") }
        val physicalSerials = onlineSerials.filter { !it.startsWith("emulator-") }

        val serialToName = emulatorSerials.associateWith { avdNameForSerial(it) }
        val nameToSerial = serialToName.entries
            .filter { it.value != null }
            .associate { it.value!! to it.key }

        val avdInfoList = avdNames.map { name ->
            val serial = nameToSerial[name]
            AvdInfo(name = name, serial = serial, running = serial != null)
        }

        val unmappedEmulators = emulatorSerials
            .filter { serial -> avdInfoList.none { it.serial == serial } }
            .map { serial -> AvdInfo(name = serialToName[serial] ?: serial, serial = serial, running = true) }

        val physicalDevices = physicalSerials
            .map { serial -> AvdInfo(name = serial, serial = serial, running = true) }

        return WsMessage.AvdList(avds = avdInfoList + unmappedEmulators + physicalDevices)
    }

    // ── Stop ─────────────────────────────────────────────────────────────────

    fun stopEmulator(serial: String) {
        runCatching { ProcessBuilder(adbBin, "-s", serial, "emu", "kill").start().waitFor() }
    }

    // ── Create AVD ────────────────────────────────────────────────────────────

    suspend fun createAvd(name: String, onLine: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        val systemImagesDir = sdkHome?.resolve("system-images")
        val pkg = systemImagesDir
            ?.walkTopDown()
            ?.filter { it.name == "package.xml" }
            ?.firstOrNull()
            ?.parentFile
            ?.let { dir ->
                // Build SDK package path: system-images;android-XX;variant;arch
                dir.relativeTo(sdkHome!!).path.replace(File.separatorChar, ';')
            }

        if (pkg == null) {
            onLine("Could not find a system image under ${systemImagesDir?.absolutePath ?: "unknown"}.")
            onLine("Install a system image via Android Studio → SDK Manager → SDK Images.")
            return@withContext
        }

        onLine("Creating AVD '$name' using package: $pkg")
        val proc = ProcessBuilder(avdmanagerBin, "create", "avd", "-n", name, "-k", pkg, "--force")
            .redirectErrorStream(true)
            .start()

        // Answer the "custom hardware profile?" interactive prompt without shell interpolation
        proc.outputStream.bufferedWriter().use { it.write("no\n") }

        proc.inputStream.bufferedReader().use { r ->
            var line = r.readLine()
            while (line != null) { onLine(line); line = r.readLine() }
        }
        proc.waitFor()
    }

    // ── Auto-start ────────────────────────────────────────────────────────────

    suspend fun autoStart(
        avdName: String,
        onStatus: suspend (phase: String, msg: String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        onStatus("starting", "Starting emulator: $avdName…")
        ProcessBuilder(emulatorBin, "-avd", avdName, "-no-boot-anim")
            .redirectErrorStream(true)
            .start()

        onStatus("booting", "Waiting for device to come online…")

        repeat(30) {
            delay(3_000)
            val serial = listOnlineSerials().firstOrNull { it.startsWith("emulator-") }
            if (serial != null) {
                onStatus("booting", "Device $serial online — waiting for full boot…")
                repeat(20) {
                    delay(3_000)
                    val booted = runCatching {
                        val process = ProcessBuilder(adbBin, "-s", serial, "shell", "getprop", "sys.boot_completed").start()
                        val result  = process.inputStream.bufferedReader().readLine()?.trim()
                        process.waitFor()
                        result
                    }.getOrNull() == "1"
                    if (booted) {
                        onStatus("ready", serial)
                        return@withContext serial
                    }
                }
                onStatus("error", "Emulator $serial did not finish booting in time")
                return@withContext null
            }
        }
        onStatus("error", "Emulator failed to come online within 90 s")
        null
    }
}
