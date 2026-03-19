# Contributing to skipper-java

## Setup

```bash
git clone https://github.com/get-skipper/skipper-java
cd skipper-java
./gradlew build
```

## Running Tests

```bash
make test          # unit tests only (no credentials required)
```

## Project Structure

```
core/              # shared resolver, Google Sheets client, writer, credentials
junit5/            # JUnit 5 extension (LauncherSessionListener + ExecutionCondition)
testng/            # TestNG ISuiteListener + ITestListener
cucumber/          # Cucumber JVM ConcurrentEventListener
playwright/        # Playwright for Java extension + base class
```

## Running Integration Tests

Copy the service account JSON to the repo root (gitignored):

```bash
cp /path/to/service-account-skipper-bot.json .
```

Then run in sync mode to verify the spreadsheet is updated:

```bash
SKIPPER_MODE=sync \
SKIPPER_SPREADSHEET_ID=<id> \
SKIPPER_SHEET_NAME=skipper-java \
make sync
```

## Pull Requests

- Ensure `make test` passes
- Add tests for new functionality
- Keep commits focused; one logical change per PR
