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

    // lowercase kebab-case for Maven artifactId convention
    val projectArtifactId: String get() =
        projectName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

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
        fun from(req: WsMessage.ScaffoldRequest) = TemplateContext(
            projectName  = req.projectName,
            packageName  = req.packageName,
            packagePath  = req.packageName.replace('.', '/'),
            architecture = req.architecture,
            android      = "android" in req.targets,
            ios          = "ios" in req.targets,
            desktop      = "desktop" in req.targets,
            web          = "web" in req.targets,
            ktor         = "ktor" in req.libraries,
            sqldelight   = "sqldelight" in req.libraries,
            datastore    = "datastore" in req.libraries,
            koin         = "koin" in req.libraries,
            coil         = "coil" in req.libraries,
            voyager      = "voyager" in req.libraries,
            molecule     = "molecule" in req.libraries,
        )
    }
}
