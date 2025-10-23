// no import; use fully qualified name to avoid early classpath issues

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("com.vanniktech.maven.publish") version "0.34.0"
    signing
}

group = "dev.mudkip"
version = "0.1.0"
description = "Generate JSON Schema files from your Kotlin classes at runtime using kotlinx.serialization."

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    compileOnly("org.jetbrains:annotations:26.0.2-1")

    // Unit Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.24")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates(project.group.toString(), rootProject.name, project.version.toString())
    publishToMavenCentral()
    signAllPublications()

    pom {
        name = project.name
        description = project.description
        url = "https://github.com/mudkipdev/kotlinx-schema-generator"

        licenses {
            license {
                name = "MIT"
                url = "https://github.com/mudkipdev/kotlinx-schema-generator/blob/main/LICENSE"
            }
        }

        developers {
            developer {
                name = "mudkip"
                id = "mudkipdev"
                email = "mudkip@mudkip.dev"
                url = "https://mudkip.dev"
            }
        }

        scm {
            url = "https://github.com/mudkipdev/kotlinx-schema-generator"
            connection = "scm:git:git://github.com/mudkipdev/kotlinx-schema-generator.git"
            developerConnection = "scm:git:ssh://git@github.com/mudkipdev/kotlinx-schema-generator.git"
        }
    }
}
