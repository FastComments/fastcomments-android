# FastComments Android SDK Development Guide

This is a library, and android SDK. It is a live commenting system. It should be fast, efficient, easy for developers to use, follow good UX, have good error handling,
and allow customizing colors and layouts.

## Build Commands
- Build all: `./gradlew build`
- Build app: `./gradlew :app:build`
- Build SDK: `./gradlew :libraries:sdk:build`
- Run unit tests: `./gradlew test`
- Run single test: `./gradlew :app:testDebugUnitTest --tests "com.fastcomments.ExampleUnitTest"`
- Run instrumented tests: `./gradlew connectedAndroidTest`
- Run lint: `./gradlew lint`

## Code Style Guidelines
- **Languages**: App uses Kotlin, SDK library uses Java
- **Imports**: Group by Android/Java imports first, then package imports, alphabetical within groups
- **Naming**:
  - Classes: PascalCase (FastCommentsView)
  - Methods/variables: camelCase (getComments(), commentList)
  - Kotlin test functions: snake_case (should_pass_validation)
  - Constants: UPPER_SNAKE_CASE
- **Error Handling**: Throw exceptions for invalid states, document in method comments
- **Architecture**: Jetpack Compose for app UI, RecyclerView-based adapters for SDK
- **Documentation**: JavaDoc for public SDK methods, KDoc for Kotlin public functions
- **Code Organization**: Small, focused classes with single responsibility