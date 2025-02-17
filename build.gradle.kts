import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  val kotlinVersion = "2.1.10"

  kotlin("jvm") version kotlinVersion
  `java-library`
  `maven-publish`

  id("com.diffplug.spotless") version "7.0.2"
  id("org.jetbrains.dokka") version "2.0.0"
  id("org.jetbrains.kotlinx.kover") version "0.9.1"
  id("org.jreleaser") version "1.15.0"
}

group = "io.github.ivsokol"

version = "1.0.0"

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
  implementation("org.slf4j:slf4j-api:2.0.16")
  implementation("ch.qos.logback:logback-classic:1.5.16")
  implementation("org.fusesource.jansi:jansi:2.4.1")

  testImplementation("io.kotest:kotest-runner-junit5:${project.property("kotestVersion")}")
  testImplementation("io.kotest:kotest-assertions-json-jvm:${project.property("kotestVersion")}")
  testImplementation("io.kotest:kotest-framework-datatest-jvm:${project.property("kotestVersion")}")
  testImplementation("io.kotest:kotest-extensions-now-jvm:${project.property("kotestVersion")}")
  testImplementation(
      "io.kotest.extensions:kotest-extensions-clock:${project.property("kotestExtClockVersion")}")
}

tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
  useJUnitPlatform()
  maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
  reports.html.required.set(false)
}

tasks.dokkaJavadoc.configure { outputDirectory.set(layout.buildDirectory.dir("docs/javadoc")) }

tasks.register<Jar>("dokkaJavadocJar") {
  dependsOn(tasks.dokkaJavadoc)
  from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
  archiveClassifier.set("javadoc")
}

project.tasks.getByName("jar").dependsOn("dokkaJavadocJar")

project.tasks.getByName("javadoc").dependsOn("dokkaJavadoc")

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
          create("maven-central") {
            setActive("ALWAYS")
            url = "https://central.sonatype.com/api/v1/publisher"
            stagingRepository("build/staging-deploy")
            applyMavenCentralRules = true
          }
        }
        github {
          create("github") {
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

tasks.withType<KotlinCompile> { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

kotlin { jvmToolchain(21) }

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
