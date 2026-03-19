# skipper-java

Java test-gating framework powered by Google Sheets. Part of the [Skipper](https://github.com/get-skipper) ecosystem.

Disable specific tests remotely — no code changes needed. Tests are skipped when a future `disabledUntil` date is set in a shared Google Spreadsheet.

## Packages

| Artifact | Framework | Description |
|----------|-----------|-------------|
| `io.getskipper:skipper-core` | — | Core Google Sheets client, resolver, and writer |
| `io.getskipper:skipper-junit5` | JUnit 5 | `LauncherSessionListener` + `ExecutionCondition` |
| `io.getskipper:skipper-testng` | TestNG | `ISuiteListener` + `ITestListener` |
| `io.getskipper:skipper-cucumber` | Cucumber JVM | `ConcurrentEventListener` plugin |
| `io.getskipper:skipper-playwright` | Playwright for Java | Extension + base class |

Published to [GitHub Packages](https://github.com/orgs/get-skipper/packages).

## Spreadsheet Schema

| Column | Type | Description |
|--------|------|-------------|
| `testId` | string | Unique test identifier |
| `disabledUntil` | date (YYYY-MM-DD) | Empty or past = runs; future = skipped |
| `notes` | string | Free text, ignored by Skipper |

## Usage — JUnit 5

Add to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/get-skipper/skipper-java")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    testImplementation("io.getskipper:skipper-junit5:0.1.0")
}
```

Enable auto-detection in `src/test/resources/junit-platform.properties`:

```properties
junit.extensions.autodetection.enabled=true
```

Set environment variables:

```bash
SKIPPER_SPREADSHEET_ID=your-spreadsheet-id
GOOGLE_CREDS_B64=$(base64 -i service-account.json)   # or SKIPPER_CREDENTIALS_FILE=./service-account.json
SKIPPER_SHEET_NAME=your-sheet-name
```

Run:

```bash
./gradlew test
```

## Usage — TestNG

```kotlin
dependencies {
    testImplementation("io.getskipper:skipper-testng:0.1.0")
}
```

In `testng.xml`:

```xml
<listeners>
  <listener class-name="io.getskipper.testng.SkipperListener"/>
</listeners>
```

## Usage — Cucumber JVM

```kotlin
dependencies {
    testImplementation("io.getskipper:skipper-cucumber:0.1.0")
}
```

In `@CucumberOptions`:

```java
@CucumberOptions(plugin = {"io.getskipper.cucumber.SkipperPlugin"})
```

## Usage — Playwright

```kotlin
dependencies {
    testImplementation("io.getskipper:skipper-playwright:0.1.0")
    testImplementation("com.microsoft.playwright:playwright:1.44.0")
}
```

Extend `SkipperPlaywrightTest`:

```java
class LoginTest extends SkipperPlaywrightTest {
    @Test
    void shouldLogin() {
        page.navigate("https://example.com/login");
        // ...
    }
}
```

## Sync Mode

Run with `SKIPPER_MODE=sync` to reconcile the spreadsheet after each test run:
- New test IDs are appended with empty `disabledUntil` (enabled by default)
- Test IDs no longer present in the suite are removed

```bash
SKIPPER_MODE=sync ./gradlew test
```

## Test ID Format

```
com/example/AuthTest.java > AuthTest > shouldLogin
```

Derived from the fully-qualified class name and method name.

## License

MIT
