
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
}

group = "com.example"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.2")
    implementation("io.ktor:ktor-serialization-jackson-jvm:3.5.2")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.2")
    implementation("io.ktor:ktor-server-routing-openapi-jvm:3.5.2")
    implementation("io.ktor:ktor-server-swagger-jvm:3.5.2")
    implementation("io.ktor:ktor-openapi-schema-reflect-jvm:3.5.2")
    implementation(libs.logback.classic)
    implementation("org.flywaydb:flyway-core:9.22.3")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir("com/PokeHelper/src/api/kotlin")
}

kotlin.sourceSets.named("test") {
    kotlin.srcDir("com/PokeHelper/src/test/kotlin")
}

sourceSets.main {
    resources.srcDir("com/PokeHelper/src/api/resources")
}
