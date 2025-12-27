# Agent Notes

## Build Environment

- **Java Version**: Java 17 or 21 only (Java 25 is NOT compatible)
- **JAVA_HOME**: `/opt/homebrew/opt/openjdk@17`
- **Android builds**: Must set `JAVA_HOME` before running Gradle commands
- Example: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew assembleDebug`
