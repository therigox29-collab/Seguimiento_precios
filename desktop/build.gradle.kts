import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.rodrigo.misprecios"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.json:json:20240303")

    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "MisPrecios"
            packageVersion = "1.0.0"

            // El set de módulos automático de Compose Desktop no incluye java.sql,
            // que es lo que usa sqlite-jdbc para hablar con la base de datos local.
            // Sin este módulo la JVM ni siquiera llega a arrancar ("Failed to launch JVM").
            modules("java.sql", "java.naming", "jdk.unsupported")

            windows {
                menuGroup = "MisPrecios"
                perUserInstall = true
            }
        }
    }
}
