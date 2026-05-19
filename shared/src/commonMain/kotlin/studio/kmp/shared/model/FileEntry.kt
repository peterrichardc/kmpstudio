package studio.kmp.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long
)
