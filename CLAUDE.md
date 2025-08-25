# CLAUDE.md - Penn TotalRecall Development Guide

## Project Overview

Penn TotalRecall is an audio annotation tool for research, developed by the Computational Memory Lab at UPenn. This guide provides context for AI assistants working on the codebase.

## Architecture & Technologies

### Core Technologies
- **Java 24** - Latest runtime (bundled with app)
- **Gradle 9.0.0** - Build system with modern plugins
- **FMOD Core** - High-performance audio engine via JNA
- **Swing** - Desktop GUI framework
- **Guice 7.0** - Dependency injection framework
- **JUnit 5** - Testing framework with Mockito

### Audio System
- **Direct FMOD Core JNA binding** - Eliminates C compilation entirely
- **Thread-safe audio playback** - `FmodCore.java` with synchronized access
- **Precision timing** - Comprehensive timing tests in `FmodCoreTest.java`
- **Waveform rendering** - Uses MaryTTS SignalProc for filtering

### Build System
- **Modern Gradle structure** - Standard `src/main/java`, `src/test/java` layout
- **Self-contained macOS app** - Uses `jpackage` with custom JVM runtime
- **Dependency management** - All dependencies from Maven Central
- **Code quality** - ErrorProne static analysis, Spotless formatting

### Configuration System
- **5-level hierarchy** - System properties > User config > Platform config > Application config > Defaults
- **Platform-specific paths** - macOS: `~/Library/Application Support`, Linux: `~/.penn-totalrecall`, Windows: `%APPDATA%`
- **Environment-aware** - Separate configs for development, CI, and production
- **Type-safe access** - Boolean, integer, double property parsing with defaults
- **Graceful fallback** - Malformed configs don't break application startup

### Dependency Injection
- **Guice-based architecture** - Full DI throughout application
- **Service-oriented design** - Core services in `env/` package
- **Interface-based testing** - Easy mocking and unit testing
- **Singleton lifecycle** - Thread-safe service instances
- **Bootstrap integration** - Clean application startup with `GuiceBootstrap`

## Recent Modernization (Completed)

### ✅ Audio System Modernization
- Replaced legacy FMOD Ex with FMOD Core
- Eliminated all C compilation using direct JNA bindings
- Maintained exact same public API for compatibility
- Added comprehensive timing tests

### ✅ Build System Overhaul
- Migrated from Ant to modern Gradle
- Adopted standard Gradle project structure
- Removed all Eclipse/IDE-specific files
- Integrated modern tooling (ErrorProne, Spotless, dependency checker)

### ✅ Dependency Cleanup
- **Eliminated 13 unused JAR files** from lib/ directory
- **Removed 10 unused Maven dependencies**
- Replaced 16-year-old signalproc.jar with modern MaryTTS 5.2.+
- Integrated swing-shortcut-manager source code (eliminated another JAR)

### ✅ macOS Integration Modernization
- Replaced deprecated Apple EAWT APIs with modern Desktop API
- Created `MacOSIntegration.java` using Java 24 features
- Proper About/Preferences/Quit menu integration
- Taskbar/Dock icon and progress support

### ✅ Architecture Modernization
- **Eliminated Environment class** - Functionality distributed to focused services
- **Modern dependency injection** - Full Guice DI architecture throughout
- **Service-oriented design** - AppConfig, UpdateManager, UserManager, AudioSystemManager, KeyboardManager, LookAndFeelManager
- **5-level configuration hierarchy** - System properties > User config > Platform config > Application config > Defaults
- **Comprehensive test suite** - Meaningful behavioral tests for all services

### ✅ Code Quality Improvements
- Removed all GPL headers and @author references
- Applied Google Java Format with Spotless
- Fixed API compatibility issues during library upgrades
- Added wildcarded dependency versions for automatic updates
- **Modernized JNA usage** - Eliminated deprecated Native.loadLibrary() calls
- **Added Lombok** - Reduced boilerplate with @NonNull, @Inject annotations
- **Comprehensive logging** - SLF4J with Logback for structured logging

### ✅ Project Structure Modernization  
- **Eliminated lib/ directory** - No more local JAR files
- **Eliminated native/ directory** - Moved to standard Gradle resources
- **Reorganized FMOD libraries** - Now in `src/main/resources/fmod/macos/`
- **Standard Gradle layout** - All resources follow conventions

## Project Structure (Modern & Clean)

```
Penn-TotalRecall/
├── src/main/java/                    # Standard Gradle Java sources
│   ├── audio/                        # FMOD JNA bindings & audio engine
│   ├── behaviors/                    # Action pattern implementations
│   ├── components/                   # Swing UI components
│   ├── control/                      # Application controllers & main
│   ├── di/                           # Guice dependency injection config
│   ├── env/                          # Core services (config, audio, user)
│   ├── info/                         # Constants & preferences
│   ├── shortcuts/                    # Integrated shortcut manager
│   └── util/                         # Utility classes
├── src/main/resources/               # Standard Gradle resources
│   ├── fmod/macos/                   # FMOD native libraries
│   ├── images/                       # Application icons
│   ├── actions.xml                   # Shortcut definitions
│   ├── application.properties        # Bundled configuration
│   ├── development.properties        # Development environment config
│   └── ci.properties                 # CI environment config
├── src/test/java/                    # JUnit 5 tests with Mockito
│   ├── audio/                        # Audio system tests
│   ├── components/                   # UI component tests
│   ├── env/                          # Service layer tests
│   ├── integration/                  # Integration tests
│   └── shortcuts/                    # Shortcut system tests
├── packaging/                        # Packaging assets
├── build.gradle                      # Modern build configuration
├── CLAUDE.md                         # This development guide
└── No lib/ or native/ directories!   # Pure Maven dependencies
```

## Current Dependencies (Minimal & Modern)

```gradle
// Core Runtime (7)
implementation 'net.java.dev.jna:jna:5.17.+'
implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.16.+'
implementation('de.dfki.mary:marytts-signalproc:5.2.+') {
    exclude group: 'gov.nist.math', module: 'Jampack'
    exclude group: 'com.twmacinta', module: 'fast-md5'
}
implementation 'com.formdev:flatlaf:3.5.+'
implementation 'org.slf4j:slf4j-api:2.0.+'
implementation 'ch.qos.logback:logback-classic:1.5.+'
implementation 'com.google.inject:guice:7.0.+'

// Compile-time (1)
compileOnly 'org.projectlombok:lombok:1.18.+'
annotationProcessor 'org.projectlombok:lombok:1.18.+'

// Testing (4)
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.+'
testImplementation 'org.mockito:mockito-core:5.14.+'
testImplementation 'org.mockito:mockito-junit-jupiter:5.14.+'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.+'
testCompileOnly 'org.projectlombok:lombok:1.18.+'
testAnnotationProcessor 'org.projectlombok:lombok:1.18.+'
```

## Development Commands

### Building & Testing
```bash
./gradlew compileJava          # Compile with ErrorProne analysis
./gradlew test                 # Run unit tests (excludes packaging tests)
./gradlew packageTest          # Run packaging integration tests
./gradlew test packageTest     # Run all tests (unit + integration)
./gradlew spotlessApply        # Format code with Google Java Format
./gradlew dependencyUpdates    # Check for dependency updates
./gradlew runDev               # Run application in development mode
```

### GitHub Actions Build Status

Check build status using GitHub CLI or web interface:

```bash
# Install GitHub CLI if not available
brew install gh

# Check workflow runs
gh run list --repo ldayton/Penn-TotalRecall

# View specific run details  
gh run view <run-id> --repo ldayton/Penn-TotalRecall

# Watch live logs
gh run watch --repo ldayton/Penn-TotalRecall
```

**Web interface:** https://github.com/ldayton/Penn-TotalRecall/actions

### Packaging
```bash
./gradlew packageMacApp        # Create self-contained macOS .app bundle
./gradlew packageMacDmg        # Create macOS .dmg installer for distribution
./gradlew jar                  # Create fat JAR with all dependencies
```

## Testing Strategy

### Test Categories
- **Unit Tests** (`./gradlew test`) - Fast tests that don't require built artifacts
  - Service layer tests with Mockito mocking
  - Configuration parsing and validation
  - Audio system logic (without real hardware)
  - Dependency injection wiring
- **Packaging Tests** (`./gradlew packageTest`) - Integration tests requiring built .app bundles
  - End-to-end FMOD loading in packaged environment
  - Native library resolution and loading
  - Complete application startup and audio integration
  - Real process execution and validation

### Test Annotations
- **`@AudioHardware`** - Tests requiring real audio devices (skipped on CI)
- **`@Windowing`** - Tests requiring window system (skipped in headless environments) 
- **`@Packaging`** - Integration tests requiring built artifacts (separate test run)

### Framework Features
- **JUnit 5** with comprehensive assertion and timeout support
- **Mockito integration** for service dependency mocking
- **Environment-aware testing** via configuration properties
- **Structured CI reporting** with separate unit/integration results

### Development Workflow
```bash
./gradlew test          # Quick feedback during development
./gradlew packageTest   # Full integration validation
./gradlew test packageTest  # Complete test suite
```

### Manual Testing
- Audio playback and seeking functionality
- Waveform rendering and filtering
- Annotation creation and editing
- Keyboard shortcuts and menu integration

## Key Files & Packages

### Core Services (`env/` package)
- `env/AppConfig.java` - 5-level configuration hierarchy system
- `env/AudioSystemManager.java` - FMOD library loading with modern JNA API
- `env/AudioSystemLoader.java` - Audio loading interface for dependency injection
- `env/UpdateManager.java` - GitHub Releases API integration for version checking
- `env/UserManager.java` - User directory and configuration management
- `env/Platform.java` - Cross-platform detection and path resolution
- `env/KeyboardManager.java` - System keyboard event handling
- `env/LookAndFeelManager.java` - UI theme management

### Event System (`util/` package)
- `util/EventDispatchBus.java` - Thread-safe event bus with EDT-only subscriber execution
- `util/Subscribe.java` - Annotation for marking event handler methods

**Threading Model**: Events can be published from any thread, but all subscribers execute on the Event Dispatch Thread (EDT) for Swing thread safety. If published from EDT, subscribers execute immediately; if published from other threads, subscribers are queued to EDT. This eliminates the need for manual `SwingUtilities.invokeLater()` calls in event handlers and prevents Swing threading violations.

### Dependency Injection (`di/` package)
- `di/GuiceBootstrap.java` - Guice module configuration and application bootstrap
- `di/AppModule.java` - Guice dependency binding configuration

### Audio System (`audio/` package)
- `audio/FmodCore.java` - Direct FMOD Core JNA interface
- `audio/PrecisionPlayer.java` - High-precision audio playback
- `audio/PrecisionEvent.java` - Timing event system
- `audio/NativeStatelessPlayer.java` - Stateless audio playback implementation
- `components/waveform/WaveformBuffer.java` - Uses MaryTTS for signal processing

### UI System (`components/` package)
- `components/MacOSIntegration.java` - Modern macOS Desktop API integration
- `components/MyFrame.java` - Main application window
- `components/MyMenu.java` - Application menu system
- `components/WindowManager.java` - Window state management

### Action System (`behaviors/` package)
- `behaviors/UpdatingAction.java` - Base action class with update support
- `behaviors/singleact/` - Single-execution actions (play, pause, etc.)
- `behaviors/multiact/` - Multi-parameter actions (seek, zoom, etc.)

### Shortcut System (`shortcuts/` package)
- `shortcuts/ShortcutManager.java` - Keyboard shortcut management
- `shortcuts/XAction.java` - Action wrapper for shortcuts
- `shortcuts/ModernKeyUtils.java` - Modern key event utilities

### Application Control (`control/` package)
- `control/Main.java` - Application entry point with Guice bootstrap
- `control/XActionManager.java` - Action registration and management
- `control/AudioMaster.java` - Audio system coordination

### Configuration & Resources
- `build.gradle` - Modern build configuration with plugins
- `src/main/resources/application.properties` - Bundled application configuration
- `src/main/resources/development.properties` - Development environment settings
- `src/main/resources/ci.properties` - CI environment settings
- `src/main/resources/actions.xml` - Keyboard shortcut definitions
- `src/main/resources/fmod/macos/` - FMOD native libraries
- `src/main/resources/images/` - Application icons and graphics
- `packaging/macos/` - DMG and app bundle assets
- `packaging/samples/` - Sample files included in distributions
- `CLAUDE.md` - This development guide

### Test Suite (`src/test/java/`)
- `env/AppConfigTest.java` - Comprehensive configuration hierarchy testing
- `env/AudioSystemManagerTest.java` - Audio system service testing
- `env/UpdateManagerTest.java` - Update checking with HTTP mocking
- `env/UserManagerTest.java` - User directory and configuration testing
- `audio/FmodCoreTest.java` - FMOD integration testing
- `audio/FmodConfigurationTest.java` - FMOD configuration testing
- `integration/GuiceBootstrapIntegrationTest.java` - Dependency injection testing

## TODO - Remaining Tasks

### Testing Issues
- [ ] **UpdateManager GUI dependency** - Fix "MyFrame not initialized" error in tests when UpdateManager tries to show notifications

### Build System
- [ ] **Debug version** - FMOD logging lib and higher logging levels

### Platform Support
- [ ] **Linux support** - Port from macOS-specific components
- [ ] **Windows support** - Cross-platform compatibility

### macOS Polish
- [ ] **Menu bar integration** - Native macOS menu behavior
- [ ] **Shortcut manager key names** - Improve obscure key name display

## Common Development Patterns

### Adding Dependencies
Use wildcarded patch versions for automatic updates:
```gradle
implementation 'group:artifact:major.minor.+'
```

### Audio Development
- Always test with `FMODTimingTest` for timing regressions
- Use synchronized access for thread-safe audio operations
- FMOD libraries are in `src/main/resources/fmod/macos/`
- **Modern JNA loading** - Use `Native.load()` with `NativeLibrary.addSearchPath()` for absolute paths
- **No deprecated APIs** - All `Native.loadLibrary()` calls eliminated

### Configuration Development
- **AppConfig service** - Inject for all configuration access
- **Environment-specific configs** - Use development.properties, ci.properties for overrides
- **5-level priority** - System properties always win, then user config, platform, application, defaults
- **Graceful parsing** - Always provide sensible defaults for type conversion failures

### Dependency Injection Patterns
- **Constructor injection** - Use `@Inject` on constructors, never field injection
- **Interface dependencies** - Depend on interfaces (AudioSystemLoader) not implementations
- **Singleton services** - Mark services with `@Singleton` for shared state
- **Testing with mocks** - Use Mockito to mock service dependencies in tests

### Viewing All Compiler Warnings
Gradle's incremental compilation hides warnings on unchanged files. To see all warnings:
```bash
./gradlew clean --no-build-cache --rerun-tasks compileJava
```

### DMG Packaging
- `packageMacDmg` creates professional DMG using pure jpackage approach
- Self-contained installer with embedded Java runtime
- No native system dependencies beyond Java/jpackage
- Uses `packaging/macos/headphones.icns` for app icon in DMG

### UI Development
- Follow existing Swing patterns in `components/`
- Use `UpdatingAction` for keyboard shortcut integration
- Test macOS integration with `MacOSIntegration.integrateWithMacOS()`

### Code Quality
- Run `./gradlew spotlessApply` before commits
- ErrorProne analysis runs automatically on compile
- Use Java 24 features (records, pattern matching, etc.)

## Notes for AI Assistants

### Code Style
- **Google Java Format** enforced via Spotless
- **No comments** unless specifically requested
- **Java 24 features** preferred (no legacy fallbacks needed)
- **Concise responses** - avoid explaining code unless asked

### Git Commits
- **No advertising in commit messages** - never include "Generated with Claude Code" or similar promotional text
- **Clean, descriptive commit messages** focusing on what changed and why

### Audio Testing Strategy
- **Hosted CI limitations**: No reliable audio device I/O on GitHub Actions/CircleCI/Buildkite hosted runners
- **Current approach**: `@AudioHardware` annotation skips audio tests entirely on CI (`CI=true`)
- **Recommended improvement**: Modify FMOD tests to use `NOSOUND_NRT` mode on CI for logic testing without devices
- **NOSOUND mode benefits**: Tests 80% of audio logic (file loading, seek accuracy, timing, memory management) without device I/O
- **Implementation**: Detect `CI=true` environment and call `setOutputMode(FMOD_OUTPUTTYPE_NOSOUND_NRT)` in test setup
- **What NOSOUND mode tests**: Audio processing logic, file parsing, positioning, API correctness, error handling
- **What it skips**: Device enumeration, sample rate switching, mic input, actual audio output
- **For real device testing**: Requires dedicated Mac (MacStadium/Scaleway) with self-hosted runner and one-time permission grants

### Build System
- **Pure Maven Central** dependencies only
- **No local JAR files** - everything managed via Gradle
- **Standard Gradle layout** - don't suggest custom structures  
- **Wildcarded versions** for patch updates

### Testing
- **Always run tests** after significant changes
- **FMOD timing is critical** - watch for regressions
- **ErrorProne warnings** should be addressed
- **Mockito for service testing** - Mock external dependencies like HTTP clients
- **Behavioral testing focus** - Test what services do, not how they're implemented
- **Environment-aware testing** - CI automatically disables audio/windowing via configuration
- **Test annotations** - Use `@AudioHardware` and `@Windowing` tags for environment-specific exclusions
- **Configuration-driven** - Tests read `ci.properties` vs `development.properties` for behavior

### Platform Considerations
- **macOS primary target** - bundled .app with embedded JVM
- **Java 24** - no version compatibility concerns
- **Modern APIs only** - no deprecated API usage

This codebase has been extensively modernized and follows current Java best practices.

## Modernization Impact Summary

**Before modernization:**
- 15+ JAR dependencies (13MB+ in lib/)
- Legacy C compilation required  
- Deprecated Apple EAWT APIs
- Ant-based build system
- 16-year-old signal processing library

**After modernization:**
- 10+ total dependencies (all from Maven Central)
- Pure Java + JNA implementation with Guice DI
- Modern Java 24 Desktop APIs
- Modern Gradle with auto-updating dependencies
- Current libraries with 14+ years of improvements
- Comprehensive test coverage with Mockito
- 5-level configuration hierarchy system

**Result:** Clean, maintainable, modern Java 24 codebase with comprehensive dependency injection and zero legacy dependencies.
- don't put claude ads in git commits
- use GitHub Releases API for dial-home version checking
