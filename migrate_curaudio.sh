#!/bin/bash

# Comprehensive CurAudio to AudioState Migration Script
# This script updates all remaining CurAudio usages to use AudioState injection

echo "Starting comprehensive CurAudio to AudioState migration..."

# Update WaveformDisplay.java - replace all CurAudio calls with audioState calls
echo "Updating WaveformDisplay.java..."
sed -i '' 's/CurAudio\.firstFrameOfChunk/audioState.firstFrameOfChunk/g' src/main/java/components/waveform/WaveformDisplay.java
sed -i '' 's/CurAudio\.lastChunkNum/audioState.lastChunkNum/g' src/main/java/components/waveform/WaveformDisplay.java
sed -i '' 's/CurAudio\.getMaster/audioState.getMaster/g' src/main/java/components/waveform/WaveformDisplay.java
sed -i '' 's/CurAudio\.getPlayer/audioState.getPlayer/g' src/main/java/components/waveform/WaveformDisplay.java
sed -i '' 's/CurAudio\.audioOpen/audioState.audioOpen/g' src/main/java/components/waveform/WaveformDisplay.java
sed -i '' 's/CurAudio\.getAudioProgress/audioState.getAudioProgress/g' src/main/java/components/waveform/WaveformDisplay.java
sed -i '' 's/CurAudio\.lookupChunkNum/audioState.lookupChunkNum/g' src/main/java/components/waveform/WaveformDisplay.java

# Update AudioFileDisplay.java - replace all CurAudio calls with audioState calls
echo "Updating AudioFileDisplay.java..."
sed -i '' 's/CurAudio\.audioOpen/audioState.audioOpen/g' src/main/java/components/audiofiles/AudioFileDisplay.java
sed -i '' 's/CurAudio\.getCurrentAudioFileAbsolutePath/audioState.getCurrentAudioFileAbsolutePath/g' src/main/java/components/audiofiles/AudioFileDisplay.java
sed -i '' 's/CurAudio\.switchFile/audioState.switchFile/g' src/main/java/components/audiofiles/AudioFileDisplay.java

# Update WaveformBuffer.java - replace all CurAudio calls with audioState calls
echo "Updating WaveformBuffer.java..."
sed -i '' 's/CurAudio\.lastChunkNum/audioState.lastChunkNum/g' src/main/java/components/waveform/WaveformBuffer.java
sed -i '' 's/CurAudio\.getMaster/audioState.getMaster/g' src/main/java/components/waveform/WaveformBuffer.java
sed -i '' 's/CurAudio\.getAudioProgress/audioState.getAudioProgress/g' src/main/java/components/waveform/WaveformBuffer.java
sed -i '' 's/CurAudio\.lookupChunkNum/audioState.lookupChunkNum/g' src/main/java/components/waveform/WaveformBuffer.java
sed -i '' 's/CurAudio\.lastFrameOfChunk/audioState.lastFrameOfChunk/g' src/main/java/components/waveform/WaveformBuffer.java
sed -i '' 's/CurAudio\.firstFrameOfChunk/audioState.firstFrameOfChunk/g' src/main/java/components/waveform/WaveformBuffer.java
sed -i '' 's/CurAudio\.getCurrentAudioFileAbsolutePath/audioState.getCurrentAudioFileAbsolutePath/g' src/main/java/components/waveform/WaveformBuffer.java

echo "Migration script completed!"
echo "Note: You may need to manually inject AudioState into components that don't have it yet."
