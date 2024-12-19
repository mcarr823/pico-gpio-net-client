plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "dev.mcarr"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.network)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.mcarr"
            artifactId = "pgnc"
            version = "0.0.1"

            from(components["kotlin"])
        }
    }
    repositories {
        mavenLocal()
    }
}