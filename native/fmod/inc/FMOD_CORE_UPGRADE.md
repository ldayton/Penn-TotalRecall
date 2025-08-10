# FMOD Core Header Upgrade Instructions

## Current State
The `inc/` directory contains FMOD Ex 4.32.03 headers from 2010:
- `fmod.h` - Main FMOD Ex header  
- `fmod_errors.h` - Error codes
- `fmod_*.h` - Various FMOD Ex modules

## Required Actions
When FMOD Core SDK is downloaded:

1. **Replace all FMOD headers** with FMOD Core versions:
   - `fmod.h` - Main FMOD Core header
   - `fmod_errors.h` - Updated error codes  
   - Remove obsolete headers (codec, dsp, memoryinfo, output, linux-specific)

2. **Reorganize library directories**:
   - Remove: `lib/linux32/`, `lib/linux64/`, `lib/osx/`, `lib/win32/`, `lib/win64/`
   - Create: `lib/linux/`, `lib/macos/`, `lib/windows/`
   - 64-bit only libraries in each platform directory

3. **Update version check** in C code:
   - Current: `#define FMOD_VERSION 0x00043203` (Ex 4.32.03)
   - New: Will be FMOD Core version (e.g., `0x00020216` for Core 2.02.16)

4. **Verify API compatibility**:
   - Check for any new required headers
   - Validate constant/enum changes
   - Test compilation

## Expected FMOD Core Headers
- `fmod.h` - Main API
- `fmod_errors.h` - Error handling
- `fmod_common.h` - Common definitions
- Platform-specific headers as needed

## Library Path Updates
Updated Makefile expects libraries in:
- Linux: `lib/linux/libfmod.so`  
- macOS: `lib/macos/libfmod.dylib`
- Windows: `lib/windows/fmod.dll`

## Notes
- FMOD Core has cleaner API with fewer headers than FMOD Ex
- Some FMOD Ex-specific headers may not have Core equivalents
- Current C code has been updated for Core API compatibility