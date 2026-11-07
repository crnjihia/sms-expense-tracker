# Contributing to SMS Expense Tracker

Thank you for your interest in contributing to **SMS Expense Tracker**.

## Engineering Philosophy & Principles
- **Privacy First:** Zero network usage. No third-party tracking, analytics, or external telemetry.
- **Strict Typing & Clean Architecture:** Unidirectional data flow (MVI), Repository pattern over Room, dependency injection with Hilt.
- **Synthetic Data Exclusively:** All tests, fixtures, and samples must use synthetic Kenyan transaction data. Never commit real names, real phone numbers, or real M-Pesa receipts.

## Development Workflow
1. Fork or branch from `phase3-budget-categorisation` or `main`.
2. Name branches meaningfully: `feat/<feature-name>`, `fix/<bug-name>`, or `refactor/<scope>`.
3. Adhere to Kotlin coding conventions and formatting:
   ```bash
   ./gradlew ktlintCheck
   ./gradlew detekt
   ```
4. Run all unit tests before opening a pull request:
   ```bash
   ./gradlew testDebugUnitTest
   ```

## Commit Conventions
We enforce [Conventional Commits](https://www.conventionalcommits.org/):
- `feat(scope): add new capability`
- `fix(scope): resolve issue`
- `test(scope): add or update test coverage`
- `refactor(scope): internal structural changes without feature modification`
- `docs(scope): documentation updates`

