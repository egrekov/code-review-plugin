import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "com.codereview"
val appVersion = file(".app_version").readText().trim()
version = appVersion

// Диапазон совместимых сборок IDE — используется и в plugin.xml, и в updatePlugins.xml
val pluginSinceBuild = "243"
val pluginUntilBuild = "263.*"

// id берём из plugin.xml, чтобы не было расхождений
val pluginId = javax.xml.parsers.DocumentBuilderFactory.newInstance()
    .newDocumentBuilder()
    .parse(file("src/main/resources/META-INF/plugin.xml"))
    .getElementsByTagName("id").item(0).textContent.trim()

val updatePluginsXmlFile = layout.buildDirectory.file("distributions/updatePlugins.xml")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm("2025.1")
        bundledPlugin("com.intellij.modules.vcs")
        bundledPlugin("Git4Idea")
    }
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        name = "Code Review Helper"
        version = appVersion
        description = "Simple code review tool with Redmine export"
        ideaVersion {
            sinceBuild = pluginSinceBuild
            untilBuild = pluginUntilBuild
        }
    }

    pluginVerification {
        ides {
            // Верифицируем против той же IDE, против которой собирается плагин
            create(IntelliJPlatformType.PhpStorm, "2025.1")
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

    // Генерирует updatePlugins.xml для кастомного plugin repository.
    // URL до ZIP передаётся извне, чтобы задачу можно было использовать и в CI, и локально:
    //   ./gradlew generateUpdatePluginsXml -PpluginDownloadUrl=https://.../code-review-plugin-$appVersion.zip
    register("generateUpdatePluginsXml") {
        group = "distribution"
        description = "Генерирует updatePlugins.xml для кастомного plugin repository"
        outputs.file(updatePluginsXmlFile)
        doLast {
            val downloadUrl = providers.gradleProperty("pluginDownloadUrl").orNull
                ?: throw GradleException(
                    "Укажите -PpluginDownloadUrl=<абсолютный URL до собранного ZIP>"
                )
            updatePluginsXmlFile.get().asFile.writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <plugins>
                    <plugin id="$pluginId" url="$downloadUrl" version="$appVersion">
                        <idea-version since-build="$pluginSinceBuild" until-build="$pluginUntilBuild"/>
                        <name>Code Review Helper</name>
                    </plugin>
                </plugins>
                """.trimIndent() + "\n"
            )
        }
    }
}
