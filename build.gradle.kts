import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  val kotlinVersion = "2.3.0"

  kotlin("jvm") version kotlinVersion
  `java-library`
  signing

  id("com.diffplug.spotless") version "8.2.1"
  id("org.jetbrains.dokka-javadoc") version "2.1.0"
  id("org.jetbrains.kotlinx.kover") version "0.9.6"
  id("com.vanniktech.maven.publish") version "0.32.0"
  id("com.github.breadmoirai.github-release") version "2.5.2"
}

group = "io.github.ivsokol"

version = "1.3.3"

repositories {
  mavenLocal()
  mavenCentral()
}

// Map existing JReleaser environment variables to Vanniktech plugin properties
ext["mavenCentralUsername"] = System.getenv("MAVEN_USERNAME") ?: ""

ext["mavenCentralPassword"] = System.getenv("MAVEN_PASSWORD") ?: ""

ext["signingInMemoryKey"] = System.getenv("GPG_SECRET_KEY") ?: ""

ext["signingInMemoryKeyId"] = System.getenv("GPG_PUBLIC_KEY")?.takeLast(8) ?: ""

ext["signingInMemoryKeyPassword"] = System.getenv("GPG_PASSPHRASE") ?: ""

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

java {
  withSourcesJar()
  withJavadocJar()
}

// Ensure javadocJar task depends on dokkaGeneratePublicationJavadoc
tasks.named<Jar>("javadocJar") {
  dependsOn(dokkaJavadoc)
  from(dokkaJavadoc.flatMap { it.outputDirectory })
}

mavenPublishing {
  publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
  signAllPublications()

  coordinates(group.toString(), "spider", version.toString())

  pom {
    name.set("Spider")
    description.set(
        "Spider - minimal dependency injection framework that supports factory and singleton scopes with module and container support"
    )
    url.set("https://ivsokol.github.io/spider")
    inceptionYear.set("2025")

    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://opensource.org/licenses/Apache-2.0")
        distribution.set("repo")
      }
    }

    developers {
      developer {
        id.set("ivsokol")
        name.set("Ivan Sokol")
        email.set("ivan.sokol@gmail.com")
      }
    }

    scm {
      url.set("https://github.com/ivsokol/spider")
      connection.set("scm:git:git://github.com/ivsokol/spider.git")
      developerConnection.set("scm:git:ssh://git@github.com/ivsokol/spider.git")
    }
  }
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

// GitHub Release configuration
githubRelease {
  token(System.getenv("JRELEASER_GITHUB_TOKEN") ?: "")
  owner.set("ivsokol")
  repo.set("spider")
  tagName.set("v${version}")
  releaseName.set("Spider v${version}")
  targetCommitish.set("main")
  body.set(
      """
        ## Spider v${version}
        
        Minimal dependency injection framework for Kotlin.
        
        ### Maven Central
        ```xml
        <dependency>
            <groupId>io.github.ivsokol</groupId>
            <artifactId>spider</artifactId>
            <version>${version}</version>
        </dependency>
        ```
        
        ### Gradle Kotlin DSL
        ```kotlin
        implementation("io.github.ivsokol:spider:${version}")
        ```
    """
          .trimIndent()
  )
  overwrite.set(true)
  allowUploadToExisting.set(true)
  releaseAssets(
      tasks.named("jar").map { it.outputs.files },
      tasks.named("sourcesJar").map { it.outputs.files },
      tasks.named("dokkaJavadocJar").map { it.outputs.files },
  )
}

// Ensure GitHub release task runs after build
tasks.named("githubRelease") { dependsOn("build", "sourcesJar", "dokkaJavadocJar") }
