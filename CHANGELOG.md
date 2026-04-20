# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-04-20

### Added

- **Quarantine debt CI summary** (`SkipperReporter`): at the end of every test run Skipper emits a structured quarantine report automatically — no extra configuration needed.
  - Markdown summary is appended to `GITHUB_STEP_SUMMARY` when the env var is set; otherwise it is printed to stdout.
  - `skipper-report.json` is always written to the working directory for archiving and downstream tooling.
  - Report includes: currently suppressed test count, tests expiring within 7 days, tests re-enabled this run (expired suppressions), and quarantine-days of debt (Σ `disabledUntil − today` across all active suppressions).
- `QuarantineReport` record in `skipper-core` — the data model for the above report.
- `SkipperReporter` class in `skipper-core` — builds and emits the report; callable independently from custom listeners.

## [1.1.0] - 2026-04-10

### Added

- **Fail-open mode** (`SKIPPER_FAIL_OPEN`, default `true`): when the Sheets API is unreachable and no valid cache exists, all tests run instead of the process crashing.
- **Disk cache** (`SKIPPER_CACHE_FILE`, default `.skipper-cache.json`): successful fetches are persisted to disk and used as a fallback within the TTL window.
- **Cache TTL** (`SKIPPER_CACHE_TTL`, default `300` seconds): configures how long a cached snapshot is considered valid.
- **Safe sync** (`SKIPPER_SYNC_ALLOW_DELETE`, default `false`): `SheetsWriter.sync()` detects orphaned rows and logs a warning; actual deletion requires explicit opt-in via the env var. Rows are removed via a single `batchUpdate` in reverse-index order.
- 9 unit tests for date parsing in `SheetsClientDateParsingTest`.
- 5 unit tests for cache semantics in `SkipperResolverCacheTest` (write/read round-trip, missing file, expired TTL, fail-open).

### Changed

- **Strict date parsing** (`SheetsClient`): only `YYYY-MM-DD` is accepted; any other format now throws `IllegalArgumentException` with the row number and sheet name, so bad spreadsheet data fails loudly at startup rather than silently mid-run.
- The `disabledUntil` expiry instant is now the start of the *following* UTC day (`date + 1 day` at midnight UTC), making the disable-until boundary timezone-independent across all CI runners.

### Removed

- Multi-format date fallback parsers in `SheetsClient` (replaced by strict `YYYY-MM-DD` parsing).

### Fixed

- Broken Javadoc `@link` reference in `SkipperResolver`. ([6288d76](https://github.com/get-skipper/skipper-java/commit/6288d76))

## [1.0.0] - 2026-03-19

### Added

- Initial release of the Skipper Java SDK.
- `skipper-core`: Google Sheets client (`SheetsClient`), test-gate resolver (`SkipperResolver`), and spreadsheet sync writer (`SheetsWriter`).
- `skipper-junit5`: `LauncherSessionListener` + `ExecutionCondition` integration.
- `skipper-testng`: `ISuiteListener` + `ITestListener` integration.
- `skipper-cucumber`: `ConcurrentEventListener` plugin for Cucumber JVM.
- `skipper-playwright`: extension and base class (`SkipperPlaywrightTest`) for Playwright for Java.
- Support for `SKIPPER_MODE=sync` to reconcile the spreadsheet after each test run.
- Support for credentials via `GOOGLE_CREDS_B64` (base64-encoded JSON) or `SKIPPER_CREDENTIALS_FILE` (file path).

[1.2.0]: https://github.com/get-skipper/skipper-java/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/get-skipper/skipper-java/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/get-skipper/skipper-java/releases/tag/v1.0.0
