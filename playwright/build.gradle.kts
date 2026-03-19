dependencies {
    api(project(":core"))
    compileOnly(libs.junit5.api)
    compileOnly(libs.junit.platform.launcher)
    compileOnly(libs.playwright)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(project(":junit5"))
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "skipper-playwright"
        pom {
            name.set("skipper-playwright")
            description.set("Skipper test-gating framework — Playwright for Java integration")
        }
    }
}
