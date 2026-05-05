plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "com.codereview"
version = "1.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm("2025.1")
    }
}


intellijPlatform {
    pluginConfiguration {
        name = "Code Review Helper"
        version = "1.0.3"
        description = "Simple code review tool with Redmine export"
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "261.*"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    // Disable buildSearchableOptions — it launches a full IDE instance
    // and fails when the project path contains non-ASCII chars or is too long
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    buildSearchableOptions {
        enabled = false
    }

    prepareJarSearchableOptions {
        enabled = false
    }
}
