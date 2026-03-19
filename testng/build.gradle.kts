dependencies {
    api(project(":core"))
    compileOnly(libs.testng)

    testImplementation(libs.testng)
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(project(":junit5"))
}

publishing {
    publications.named<MavenPublication>("maven") {
        artifactId = "skipper-testng"
        pom {
            name.set("skipper-testng")
            description.set("Skipper test-gating framework — TestNG integration")
        }
    }
}
