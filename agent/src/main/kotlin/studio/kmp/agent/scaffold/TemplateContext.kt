package studio.kmp.agent.scaffold

import studio.kmp.shared.model.WsMessage

data class TemplateContext(
    val projectName: String,
    val packageName: String,
    val packagePath: String,          // com.example.app → com/example/app
    val architecture: String,
    val android: Boolean,
    val ios: Boolean,
    val desktop: Boolean,
    val web: Boolean,
    val ktor: Boolean,
    val sqldelight: Boolean,
    val datastore: Boolean,
    val koin: Boolean,
    val coil: Boolean,
    val voyager: Boolean,
    val molecule: Boolean,
    val kotlinVersion: String = "2.1.21",
    val composeVersion: String = "1.7.3",
    val agpVersion: String = "8.7.3",
    val gradleVersion: String = "8.14.3",  // already cached locally — first build is instant
    val ktorVersion: String = "3.1.3",
    val sqldelightVersion: String = "2.0.2",
    val koinVersion: String = "3.5.6",
    val coilVersion: String = "3.0.0",
    val voyagerVersion: String = "1.0.0",
) {
    val isLibrary: Boolean get() = architecture == "library"

    // lowercase kebab-case for Maven artifactId convention; falls back to "library"
    // when the project name contains no alphanumeric characters.
    val projectArtifactId: String get() =
        projectName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "library" }

    fun toMustacheMap(): Map<String, Any> = mapOf(
        "projectName"       to projectName,
        "projectArtifactId" to projectArtifactId,
        "packageName"       to packageName,
        "packagePath"       to packagePath,
        "android"           to android,
        "ios"               to ios,
        "desktop"           to desktop,
        "web"               to web,
        "ktor"              to ktor,
        "sqldelight"        to sqldelight,
        "datastore"         to datastore,
        "koin"              to koin,
        "coil"              to coil,
        "voyager"           to voyager,
        "molecule"          to molecule,
        "kotlinVersion"     to kotlinVersion,
        "composeVersion"    to composeVersion,
        "agpVersion"        to agpVersion,
        "gradleVersion"     to gradleVersion,
        "ktorVersion"       to ktorVersion,
        "sqldelightVersion" to sqldelightVersion,
        "koinVersion"       to koinVersion,
        "coilVersion"       to coilVersion,
        "voyagerVersion"    to voyagerVersion,
    )

    companion object {
        private val UI_ONLY_LIBS = setOf("coil", "voyager", "molecule")

        fun from(req: WsMessage.ScaffoldRequest): TemplateContext {
            // UI-only libs are meaningless for a library/SDK build and have no template blocks
            // in library/*.mustache — strip them server-side so a sneaky client can't request them.
            val libs = req.libraries.toSet().let { s ->
                if (req.architecture == "library") s - UI_ONLY_LIBS else s
            }
            return TemplateContext(
                projectName  = req.projectName,
                packageName  = req.packageName,
                packagePath  = req.packageName.replace('.', '/'),
                architecture = req.architecture,
                android      = "android" in req.targets,
                ios          = "ios" in req.targets,
                desktop      = "desktop" in req.targets,
                web          = "web" in req.targets,
                ktor         = "ktor" in libs,
                sqldelight   = "sqldelight" in libs,
                datastore    = "datastore" in libs,
                koin         = "koin" in libs,
                coil         = "coil" in libs,
                voyager      = "voyager" in libs,
                molecule     = "molecule" in libs,
            )
        }
    }
}
