val skipperVersion = project.findProperty("version")?.toString()
    ?.takeIf { it != "unspecified" }
    ?: "1.1.0"

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
    }
    apply(plugin = "maven-publish")

    group = "io.getskipper"
    version = skipperVersion

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
        // Run tests from the project root so that service-account-skipper-bot.json
        // is resolved correctly relative to the workspace root.
        workingDir = rootProject.projectDir
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                groupId = "io.getskipper"
                pom {
                    url.set("https://github.com/get-skipper/skipper-java")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/get-skipper/skipper-java.git")
                        url.set("https://github.com/get-skipper/skipper-java")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/get-skipper/skipper-java")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: ""
                }
            }
        }
    }
}
