dependencies {
    api(libs.google.auth.library)
    api(libs.google.apis.sheets)
    implementation(libs.google.http.client.jackson)
    implementation(libs.jackson.databind)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit5.engine)
    // Pull in the Skipper JUnit 5 session listener at test runtime so that the core's
    // own tests are gated by Skipper (self-testing pattern used in all other ports).
    testRuntimeOnly(project(":junit5"))
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "skipper-core"
        pom {
            name.set("skipper-core")
            description.set("Skipper test-gating framework — core module")
        }
    }
}
