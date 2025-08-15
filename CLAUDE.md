# CLAUDE.md - Penn TotalRecall Development Guide

## Project Overview

Penn TotalRecall is an audio annotation tool for research, developed by the Computational Memory Lab at UPenn. This guide provides context for AI assistants working on the codebase.

## Architecture & Technologies

### Core Technologies
- **Java 23** - Latest runtime (bundled with app)
- **Gradle 9.0.0** - Build system with modern plugins
- **FMOD Core** - High-performance audio engine via JNA
- **Swing** - Desktop GUI framework
- **JUnit 5** - Testing framework

### Audio System
- **Direct FMOD Core JNA binding** - Eliminates C compilation entirely
- **Thread-safe audio playback** - `LibPennTotalRecall.java` with synchronized access
- **Precision timing** - Comprehensive timing tests in `FMODTimingTest.java`
- **Waveform rendering** - Uses MaryTTS SignalProc for filtering

### Build System
- **Modern Gradle structure** - Standard `src/main/java`, `src/test/java` layout
- **Self-contained macOS app** - Uses `jpackage` with custom JVM runtime
- **Dependency management** - All dependencies from Maven Central
- **Code quality** - ErrorProne static analysis, Spotless formatting

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
- Created `MacOSIntegration.java` using Java 23 features
- Proper About/Preferences/Quit menu integration
- Taskbar/Dock icon and progress support

### ✅ Code Quality Improvements
- Removed all GPL headers and @author references  
- Applied Google Java Format with Spotless
- Fixed API compatibility issues during library upgrades
- Added wildcarded dependency versions for automatic updates

### ✅ Project Structure Modernization  
- **Eliminated lib/ directory** - No more local JAR files
- **Eliminated native/ directory** - Moved to standard Gradle resources
- **Reorganized FMOD libraries** - Now in `src/main/resources/fmod/macos/`
- **Standard Gradle layout** - All resources follow conventions

## Project Structure (Modern & Clean)

```
Penn-TotalRecall/
├── src/main/java/                    # Standard Gradle Java sources
│   ├── audio/                        # FMOD JNA bindings & tests
│   ├── components/                   # Swing UI components  
│   ├── control/                      # Application controllers
│   ├── shortcuts/                    # Integrated shortcut manager
│   └── util/                         # Utility classes
├── src/main/resources/               # Standard Gradle resources
│   ├── fmod/macos/                   # FMOD native libraries
│   ├── images/                       # Application icons
│   └── actions.xml                   # Shortcut definitions
├── src/test/java/                    # JUnit 5 tests
├── deploy/                           # Packaging assets
├── build.gradle                      # Modern build configuration
├── CLAUDE.md                         # This development guide
└── No lib/ or native/ directories!   # Pure Maven dependencies
```

## Current Dependencies (Minimal & Modern)

```gradle
// Core Runtime (3)
implementation 'net.java.dev.jna:jna:5.17.+'
implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.16.+'  
implementation('de.dfki.mary:marytts-signalproc:5.2.+') {
    exclude group: 'gov.nist.math', module: 'Jampack'
    exclude group: 'com.twmacinta', module: 'fast-md5'
}

// Testing (2)
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.+'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.+'

// Development Tools (1)  
errorprone 'com.google.errorprone:error_prone_core:2.41.+'
```

## Development Commands

### Building & Testing
```bash
./gradlew compileJava          # Compile with ErrorProne analysis
./gradlew test                 # Run JUnit 5 tests + FMOD timing tests
./gradlew spotlessApply        # Format code with Google Java Format
./gradlew dependencyUpdates    # Check for dependency updates
```

### Packaging
```bash
./gradlew packageMacApp        # Create self-contained macOS .app bundle
./gradlew packageMacDmg        # Create macOS .dmg installer for distribution
./gradlew jar                  # Create fat JAR with all dependencies
```

## Testing Strategy

### Automated Tests
- **FMOD timing tests** - Verify audio system precision and consistency
- **JUnit 5 framework** - Modern testing with comprehensive output

### Manual Testing
- Audio playback and seeking functionality
- Waveform rendering and filtering
- Annotation creation and editing
- Keyboard shortcuts and menu integration

## Key Files & Packages

### Audio System
- `audio/LibPennTotalRecall.java` - Direct FMOD Core JNA interface
- `audio/FMODTimingTest.java` - Comprehensive timing validation
- `components/waveform/WaveformBuffer.java` - Uses MaryTTS for signal processing

### UI System  
- `components/MacOSIntegration.java` - Modern macOS Desktop API integration
- `components/MyFrame.java` - Main application window
- `control/XActionManager.java` - Keyboard shortcut management
- `shortcuts/` - Integrated shortcut manager (former JAR dependency)

### Resources & Configuration
- `build.gradle` - Modern build configuration with plugins
- `src/main/resources/actions.xml` - Keyboard shortcut definitions  
- `src/main/resources/fmod/macos/` - FMOD native libraries
- `src/main/resources/images/` - Application icons and graphics
- `deploy/mac/` - DMG presentation assets (background, volume icon, DS_Store layout)
- `deploy/all/` - Sample files included in distributions
- `CLAUDE.md` - This development guide

## TODO - Remaining Tasks

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

### Viewing All Compiler Warnings
Gradle's incremental compilation hides warnings on unchanged files. To see all warnings:
```bash
./gradlew clean --no-build-cache --rerun-tasks compileJava
```

### DMG Packaging
- `packageMacDmg` creates professional DMG using pure jpackage approach
- Self-contained installer with embedded Java runtime
- No native system dependencies beyond Java/jpackage
- Uses `deploy/mac/headphones.icns` for app icon in DMG

### UI Development
- Follow existing Swing patterns in `components/`
- Use `UpdatingAction` for keyboard shortcut integration
- Test macOS integration with `MacOSIntegration.integrateWithMacOS()`

### Code Quality
- Run `./gradlew spotlessApply` before commits
- ErrorProne analysis runs automatically on compile
- Use Java 23 features (records, pattern matching, etc.)

## Notes for AI Assistants

### Code Style
- **Google Java Format** enforced via Spotless
- **No comments** unless specifically requested
- **Java 23 features** preferred (no legacy fallbacks needed)
- **Concise responses** - avoid explaining code unless asked

### Build System
- **Pure Maven Central** dependencies only
- **No local JAR files** - everything managed via Gradle
- **Standard Gradle layout** - don't suggest custom structures  
- **Wildcarded versions** for patch updates

### Testing
- **Always run tests** after significant changes
- **FMOD timing is critical** - watch for regressions
- **ErrorProne warnings** should be addressed

### Platform Considerations
- **macOS primary target** - bundled .app with embedded JVM
- **Java 23** - no version compatibility concerns
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
- 6 total dependencies (all from Maven Central)  
- Pure Java + JNA implementation
- Modern Java 23 Desktop APIs
- Modern Gradle with auto-updating dependencies
- Current libraries with 14+ years of improvements

**Result:** Clean, maintainable, modern Java 23 codebase with zero legacy dependencies.
- don't put claude ads in git commits
- use GitHub Releases API for dial-home version checking
