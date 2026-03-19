dependencies {
    api(project(":core"))
    compileOnly(libs.cucumber.java)
    compileOnly(libs.cucumber.core)

    testImplementation(libs.cucumber.java)
    testImplementation(libs.cucumber.junit5)
    testImplementation(libs.cucumber.core)
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(project(":junit5"))
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "skipper-cucumber"
        pom {
            name.set("skipper-cucumber")
            description.set("Skipper test-gating framework — Cucumber JVM integration")
        }
    }
}
