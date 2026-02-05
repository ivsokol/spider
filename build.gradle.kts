import java.net.HttpURLConnection
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jreleaser.gradle.plugin.dsl.deploy.maven.GithubMavenDeployer
import org.jreleaser.gradle.plugin.dsl.deploy.maven.MavenCentralMavenDeployer

plugins {
  val kotlinVersion = "2.3.0"

  kotlin("jvm") version kotlinVersion
  `java-library`
  `maven-publish`

  id("com.diffplug.spotless") version "8.2.1"
  id("org.jetbrains.dokka-javadoc") version "2.1.0"
  id("org.jetbrains.kotlinx.kover") version "0.9.6"
  id("org.jreleaser") version "1.15.0"
}

group = "io.github.ivsokol"

version = "1.3.0"

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.slf4j:slf4j-api:2.0.17")
  runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.3")
  runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("io.kotest:kotest-runner-junit5:${project.property("kotestVersion")}")
  testImplementation("io.kotest:kotest-assertions-json-jvm:${project.property("kotestVersion")}")
}

tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
  useJUnitPlatform()
  maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
  reports.html.required.set(false)
}

val dokkaJavadoc =
    tasks.named<org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask>(
        "dokkaGeneratePublicationJavadoc"
    )

dokka {
  dokkaPublications.named("javadoc") {
    outputDirectory.set(layout.buildDirectory.dir("docs/javadoc"))
  }
}

tasks.register<Jar>("dokkaJavadocJar") {
  dependsOn(dokkaJavadoc)
  from(dokkaJavadoc.flatMap { it.outputDirectory })
  archiveClassifier.set("javadoc")
}

project.tasks.getByName("jar").dependsOn("dokkaJavadocJar")

project.tasks.getByName("dokkaJavadocJar").dependsOn("javadoc")

project.tasks.getByName("jreleaserFullRelease").dependsOn("publish")

java {
  withSourcesJar()
  withJavadocJar()
}

jreleaser {
  project {
    name = "Spider"
    description = "Spider - minimal dependency injection framework"
    inceptionYear = "2025"
    license = "Apache-2.0"
    maintainer("Ivan Sokol")
    links {
      homepage = "https://ivsokol.github.io/spider"
      license = "https://opensource.org/licenses/Apache-2.0"
    }
    java {
      groupId = "io.github.ivsokol"
      artifactId = "spider"
    }
    signing {
      setActive("ALWAYS")
      armored = true
    }
    release { github { overwrite = true } }
    deploy {
      maven {
        mavenCentral {
          (create("maven-central") as MavenCentralMavenDeployer).apply {
            if (isArtifactPublished(group.toString(), "spider", project.version.toString())) {
              logger.lifecycle(
                  "Artifact is already published to Maven Central. Skipping 'maven-central' deployment."
              )
              setActive("NEVER")
            } else {
              setActive("ALWAYS")
            }
            url = "https://central.sonatype.com/api/v1/publisher"
            stagingRepository("build/staging-deploy")
            applyMavenCentralRules = true
          }
        }
        github {
          (create("github") as GithubMavenDeployer).apply {
            setActive("ALWAYS")
            stagingRepository("build/staging-deploy")
          }
        }
      }
    }
  }
}

publishing {
  publications {
    create<MavenPublication>("mavenKotlin") {
      groupId = "io.github.ivsokol"
      artifactId = "spider"
      version = project.version.toString()
      from(components["java"])

      pom {
        name = "Spider"
        description =
            "Spider - minimal dependency injection framework that supports factory and singleton scopes with module and container support"
        url = "https://ivsokol.github.io/spider"
        licenses {
          license {
            name = "The Apache License, Version 2.0"
            url = "https://opensource.org/licenses/Apache-2.0"
          }
        }
        developers {
          developer {
            id = "ivsokol"
            name = "Ivan Sokol"
            email = "ivan.sokol@gmail.com"
          }
        }
        scm {
          connection = "scm:git:https://ivsokol.github.io/spider.git"
          url = "https://ivsokol.github.io/spider"
        }
      }
    }
  }
  repositories { maven { url = uri(layout.buildDirectory.dir("staging-deploy")) } }
}

tasks.withType<KotlinCompile> { compilerOptions { jvmTarget.set(JvmTarget.JVM_25) } }

kotlin { jvmToolchain(25) }

project.tasks.getByName("assemble").finalizedBy("spotlessApply")

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  kotlin { ktfmt() }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt()
  }
  json {
    target("src/**/*.json")
    simple().indentWithSpaces(2)
  }
}

kover.reports.verify.rule { minBound(90) }

fun isArtifactPublished(groupId: String, artifactId: String, version: String): Boolean {
  if (version.endsWith("SNAPSHOT")) return false
  return try {
    val url = URI("https://central.sonatype.com/artifact/$groupId/$artifactId/$version").toURL()
    with(url.openConnection() as HttpURLConnection) {
      requestMethod = "GET"
      connectTimeout = 5000
      readTimeout = 5000
      if (responseCode == 200) {
        val response = inputStream.bufferedReader().use { it.readText() }
        !response.contains("NEXT_HTTP_ERROR_FALLBACK;404")
      } else {
        false
      }
    }
  } catch (_: Exception) {
    false
  }
}
