dependencies {
    api(project(":core"))
    compileOnly(libs.junit5.api)
    compileOnly(libs.junit.platform.launcher)
    compileOnly(libs.junit.platform.engine)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit5.engine)
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "skipper-junit5"
        pom {
            name.set("skipper-junit5")
            description.set("Skipper test-gating framework — JUnit 5 integration")
        }
    }
}
