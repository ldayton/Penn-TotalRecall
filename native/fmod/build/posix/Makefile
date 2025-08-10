# Makefile for FMOD Core shared library used by Penn TotalRecall
# Compiles libpenntotalrecall.c with FMOD Core API
#
# Supports Linux 64-bit, Mac OSX 64-bit (Intel/Apple Silicon)
# For Windows support see Visual Studio project in ../windows
#
# Author: Yuvi Masory
# Updated for FMOD Core API
#
# Distributed under the terms of the GNU General Public License, version 3.
# See LICENSE file
#
# Note: This Makefile assumes a very particular directory structure. Be careful
# changing the directory structure from what is in the repository!
# The unusual choice of build target location, and the lack of a clean option,
# have to do with Ant integration.
#
# C compilers tested:
#	i686-apple-darwin10-gcc-4.2.1 (GCC) 4.2.1 (Apple Inc. build 5646) (dot 1)
#	gcc (Ubuntu 4.4.1-4ubuntu9) 4.4.1
#   gcc (Ubuntu 4.4.3-4ubuntu5) 4.4.3

###############################
# Set variables
###############################
PLATFORM = $(shell uname)
SOURCE = ../../src/libpenntotalrecall.c
GENERAL_CFLAGS = -Wall -O3 -std=c99 -pedantic
BUILD_DIR = ../../../../tmp/
INSTALL_DIR = /usr/lib/

###############################
# Linux (64-bit only)
###############################
ifeq ($(PLATFORM), Linux)

SHARED_OPT = -shared
COMMON = -pthread -fPIC
PLATFORM_CFLAGS64 = $(COMMON) -m64

# FMOD Core libraries (update version as needed)
LIBRARIES64 = -lfmod -L../../lib/linux/
FMOD_LIB64 = ../../lib/linux/libfmod.so*
BUILD_NAME64 = libpenntotalrecall64.so

endif

###############################
# macOS (64-bit Intel/Apple Silicon)
###############################
ifeq ($(PLATFORM), Darwin)

BUILD_NAME = libpenntotalrecall.dylib
LIBRARIES = -lfmod -L../../lib/macos/
FMOD_LIB = ../../lib/macos/libfmod.dylib
SHARED_OPT = -dynamiclib
# Support both Intel and Apple Silicon, minimum macOS 10.15
PLATFORM_CFLAGS = -arch x86_64 -arch arm64 -mmacosx-version-min=10.15

endif

###############################
# All Platforms
###############################
all: $(PLATFORM)_all

Darwin_all:
	 mkdir -p ../../../../tmp
	 gcc $(SHARED_OPT) $(GENERAL_CFLAGS) $(PLATFORM_CFLAGS) -o $(BUILD_DIR)$(BUILD_NAME) $(SOURCE) $(LIBRARIES)

Linux_all:
	 mkdir -p ../../../../tmp
	 gcc $(SHARED_OPT) $(GENERAL_CFLAGS) $(PLATFORM_CFLAGS64) -o $(BUILD_DIR)$(BUILD_NAME64) $(SOURCE) $(LIBRARIES64)

# New target for 64-bit only Linux (for Gradle build)
Linux_64_only:
	 mkdir -p ../../../../tmp
	 gcc $(SHARED_OPT) $(GENERAL_CFLAGS) $(PLATFORM_CFLAGS64) -o $(BUILD_DIR)$(BUILD_NAME64) $(SOURCE) $(LIBRARIES64)


install: $(PLATFORM)_install

Darwin_install:
	sudo mv $(BUILD_DIR)$(BUILD_NAME) $(INSTALL_DIR)
	sudo chmod ugo+rx $(INSTALL_DIR)$(BUILD_NAME)
	sudo cp -p $(FMOD_LIB) $(INSTALL_DIR)

Linux_install:
	sudo mv $(BUILD_DIR)$(BUILD_NAME64) $(INSTALL_DIR)
	sudo chmod ugo+rx $(INSTALL_DIR)$(BUILD_NAME64)
	sudo cp -p $(FMOD_LIB64) $(INSTALL_DIR)


uninstall: $(PLATFORM)_uninstall

Darwin_uninstall:
	sudo rm -f $(INSTALL_DIR)$(BUILD_NAME)
	sudo rm -f $(INSTALL_DIR)libfmod.dylib

Linux_uninstall:
	sudo rm -f $(INSTALL_DIR)$(BUILD_NAME64)
	sudo rm -f $(INSTALL_DIR)libfmod.so*
