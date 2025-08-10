# Penn TotalRecall - Ant to Gradle Migration

This document describes the migration from Apache Ant to Gradle build system.

## What Changed

### Files Added
- `build.gradle` - Main Gradle build configuration
- `settings.gradle` - Gradle project settings  
- `gradle.properties` - Build properties and performance settings
- `gradlew` / `gradlew.bat` - Gradle wrapper scripts
- `gradle/wrapper/` - Gradle wrapper configuration

### Key Improvements
- **Dependency Management**: Maven Central integration for most dependencies
- **Build Performance**: Parallel builds, daemon mode, and caching enabled
- **Modern Java**: Updated to Java 8 minimum (from Java 6)
- **Updated Dependencies**: Most XML/utility libraries updated to latest versions
- **Simplified Configuration**: More concise than original Ant XML

## Build Commands

### Basic Build Tasks
```bash
./gradlew build          # Compile and test
./gradlew jar            # Create runnable JAR
./gradlew clean          # Clean build artifacts
./gradlew run            # Run the application
```

### Packaging Tasks
```bash
./gradlew compileNative     # Compile native audio libraries
./gradlew distZip           # Create distribution archive
./gradlew packageWindowsExe # Windows .exe (requires Launch4j)
./gradlew packageMacApp     # Mac .app bundle
./gradlew packageLinuxDeb   # Debian package
```

### Testing
```bash
./gradlew test          # Run automated tests
./gradlew checkJavaVersion  # Verify Java version
```

## Dependencies Updated

| Original | New Version | Notes |
|----------|-------------|--------|
| ICU4J 2.6.1 | 74.2 | Latest version |
| JNA (unknown) | 5.14.0 | Latest stable |
| Xalan 2.6.0 | 2.7.3 | Security updates |
| Xerces 2.6.2 | 2.12.2 | Major updates |
| XML APIs 1.3.02 | 1.4.01 | Updated |
| XOM 1.0 | 1.3.9 | Many improvements |

## Platform-Specific Notes

### Windows
- Still requires Launch4j for .exe creation
- NSIS for installer creation
- Native compilation uses vcbuild

### Mac  
- Requires jarbundler (consider updating to gradle plugin)
- DMG creation uses hdiutil
- Multi-architecture support maintained

### Linux
- Debian package creation (consider gradle-deb-plugin)
- Native make-based compilation
- 32/64-bit library support

## Migration Notes

1. **Java Version**: Minimum increased from Java 6 to Java 8
2. **Scala**: Kept at 2.9.1 for compatibility (consider upgrading)
3. **Local JARs**: Some dependencies still use local files in `lib/`
4. **Testing**: Manual test framework preserved, consider adding JUnit/ScalaTest
5. **Native Build**: Makefile-based compilation preserved

## Next Steps

1. Test build on all target platforms
2. Update CI/CD if applicable  
3. Consider upgrading Scala version
4. Add automated testing framework
5. Migrate remaining local JARs to Maven dependencies