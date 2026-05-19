package studio.kmp.agent.executor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class CliExecutor {

    fun executeRaw(command: List<String>, workDir: String? = null): Flow<ProcessLine> =
        launchProcess(command, workDir)

    fun execute(args: List<String>, workDir: String? = null): Flow<ProcessLine> =
        launchProcess(listOf("android") + args, workDir)

    fun executeBinary(
        args: List<String>,
        workDir: String? = null,
        onChunk: suspend (ByteArray) -> Unit
    ): Flow<Unit> = channelFlow<Unit> {
        val process = ProcessBuilder(listOf("android") + args)
            .apply { workDir?.let { directory(File(it)) } }
            .start()

        launch(Dispatchers.IO) {
            val buf = ByteArray(BINARY_CHUNK_BYTES)
            process.inputStream.use { stream ->
                var n = stream.read(buf)
                while (n != -1) { onChunk(buf.copyOf(n)); n = stream.read(buf) }
            }
        }.join()

        process.waitFor()
    }.flowOn(Dispatchers.IO)

    private fun launchProcess(command: List<String>, workDir: String?): Flow<ProcessLine> = channelFlow {
        val process = ProcessBuilder(command)
            .apply { workDir?.let { directory(File(it)) } }
            .redirectErrorStream(false)
            .start()

        val outJob = launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) { send(ProcessLine(line, false)); line = reader.readLine() }
            }
        }
        val errJob = launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) { send(ProcessLine(line, true)); line = reader.readLine() }
            }
        }

        outJob.join()
        errJob.join()
        if (!process.waitFor(PROCESS_EXIT_WAIT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        // Guards against processes that don't exit cleanly after closing their output streams.
        // This is NOT a build timeout — builds can run >30 min; the join() above drains all output first.
        const val PROCESS_EXIT_WAIT_MS = 1_800_000L  // 30 minutes
        const val BINARY_CHUNK_BYTES   = 65_536
    }
}

data class ProcessLine(val text: String, val isError: Boolean)
