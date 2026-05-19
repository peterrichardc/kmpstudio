package studio.kmp.agent.ws

import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

class SessionManager {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun register(id: String, job: Job) { jobs[id] = job }

    fun cancel(id: String): Boolean =
        jobs.remove(id)?.also { it.cancel() } != null

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    val activeCount: Int get() = jobs.size
}
