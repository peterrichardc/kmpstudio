plugins {
    kotlin("multiplatform")           version "2.1.0" apply false
    kotlin("jvm")                     version "2.1.0" apply false
    kotlin("plugin.serialization")    version "2.1.0" apply false
    id("org.jetbrains.compose")       version "1.7.3"  apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("io.ktor.plugin")              version "3.1.3"  apply false
}
