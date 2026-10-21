plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Jackson: la misma librería de JSON que usa Spring por defecto.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("reclamos.MainKt")
}

// Para que `./gradlew run --args="..."` lea y escriba relativo a la carpeta del proyecto.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
