/*  
	This file is part of Penn TotalRecall <http://memory.psych.upenn.edu/TotalRecall>.

    TotalRecall is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 only.

    TotalRecall is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with TotalRecall.  If not, see <http://www.gnu.org/licenses/>.
*/

/*
 * Implementation of libpenntotalrecall using FMOD Core.
 *
 * Author: Yuvi Masory
 * Updated for FMOD Core API
 *
 * WARNING streamPosition() must be called frequently in order to cause FMOD's system to update.
 */
 

#include "../inc/libpenntotalrecall.h"

#include "../inc/wincompat.h"
#include "../inc/fmod.h"
#include "../inc/fmod_errors.h"

#include <stdio.h>
//needed on Windows to get memset()
#include <memory.h>

//library info
const unsigned int revisionNumber = 2;
const char* libName = "FMOD Core implementation of LibPennTotalRecall";

//playback state
typedef enum {
    STATE_UNINITIALIZED,
    STATE_SYSTEM_CREATED,
    STATE_SYSTEM_INITIALIZED,
    STATE_SOUND_LOADED,
    STATE_PLAYING,
    STATE_ERROR
} PlaybackState;

FMOD_SYSTEM *fmsystem = NULL;
FMOD_SOUND *sound = NULL;
FMOD_CHANNEL *channel = NULL;
FMOD_CREATESOUNDEXINFO soundInfo;
int lastStartFrame = 0;
PlaybackState currentState = STATE_UNINITIALIZED;

static void printError(FMOD_RESULT result);
static void cleanupResources(void);
static int validateState(PlaybackState expected);




EXPORT_DLL int startPlayback(char* filename, long long startFrame, long long endFrame)
{
    unsigned int hiclock = 0, loclock = 0, hitime, lotime, startDelayFrames, endDelayFrames;
    int outputRate;
    float inputRate;
	FMOD_RESULT result = FMOD_OK;

	if (currentState != STATE_UNINITIALIZED) {
		fprintf(stderr, "startPlayback() called while already initialized, cleaning up first\n");
		cleanupResources();
	}

    if (startFrame < 0) {
      fprintf(stderr, "startPlayback() given a negative startFrame (%lld)! Correcting to 0\n", startFrame);
      startFrame = 0;
    }

    if (endFrame <= startFrame) {
    	fprintf(stderr, "startPlayback() given an endFrame (%lld) <= startFrame (%lld)\n", endFrame, startFrame);
		cleanupResources();
		return -1;
    }

	lastStartFrame = startFrame;

	result = FMOD_System_Create(&fmsystem, FMOD_VERSION);
	if (result != FMOD_OK) {
		fprintf(stderr, "exceptional return value for FMOD::System_Create() in startPlayback()\n");
		printError(result);
		cleanupResources();
		return -1;
	}
	currentState = STATE_SYSTEM_CREATED;

	result = FMOD_System_Init(fmsystem, 32, FMOD_INIT_NORMAL, NULL);
	if (result != FMOD_OK) {
		fprintf(stderr, "exceptional return value for FMOD::System.init() in startPlayback()\n");
		printError(result);
		cleanupResources();
		return -1;
	}
	currentState = STATE_SYSTEM_INITIALIZED;

	memset(&soundInfo, 0, sizeof(FMOD_CREATESOUNDEXINFO));
	soundInfo.cbsize = sizeof(FMOD_CREATESOUNDEXINFO);
	soundInfo.initialseekposition = startFrame;
	soundInfo.initialseekpostype = FMOD_TIMEUNIT_PCM;

	result = FMOD_System_CreateSound(fmsystem, filename, FMOD_CREATESTREAM | FMOD_LOOP_OFF, &soundInfo, &sound);
	if (result != FMOD_OK) {
		fprintf(stderr, "exceptional return value for FMOD::System.createSound() in startPlayback()\n");
		printError(result);
		cleanupResources();
		return -3;
	}
	currentState = STATE_SOUND_LOADED;

    result = FMOD_Sound_GetDefaults(sound, &inputRate, NULL, NULL, NULL);
	if (result != FMOD_OK) {
		fprintf(stderr, "exceptional return value for FMOD::System.getDefaults() in startPlayback()\n");
		printError(result);
		cleanupResources();
		return -3;
	}

	result = FMOD_System_PlaySound(fmsystem, sound, NULL, 1, &channel);
	if (result != FMOD_OK) {
		fprintf(stderr, "exceptional return value for FMOD::System.playSound() in startPlayback()\n");
		printError(result);
		cleanupResources();
		return -1;
	}
	currentState = STATE_PLAYING;

    result = FMOD_System_GetDSPBufferSize(fmsystem, &startDelayFrames, NULL);
    if (result != FMOD_OK) {
      fprintf(stderr, "FMOD error: (%d) %s\n", result, FMOD_ErrorString(result));
      fprintf(stderr, "cannot determine buffer size\n");
      cleanupResources();
      return -1;
    }
    startDelayFrames *= 2;

    result = FMOD_System_GetSoftwareFormat(fmsystem, &outputRate, NULL, NULL, NULL, NULL, NULL);
    if (result != FMOD_OK) {
      fprintf(stderr, "FMOD error: (%d) %s\n", result, FMOD_ErrorString(result));
      fprintf(stderr, "cannot determine output format\n");
      cleanupResources();
      return -1;
    }

    FMOD_System_GetDSPClock(fmsystem, &hitime, &lotime);

    hiclock = hitime;
    loclock = lotime;
    FMOD_64BIT_ADD(hiclock, loclock, 0, startDelayFrames);
    result = FMOD_Channel_SetDelay(channel, hiclock, loclock, 1);
	if (result != FMOD_OK) {
        fprintf(stderr, "exceptional return value for FMOD::Channel.setDelay() [start] in startPlayback()\n");
        printError(result);
		cleanupResources();
        return -1;
    }

    endDelayFrames = startDelayFrames + (int) (outputRate * ((endFrame - startFrame) / (double)(inputRate)));
    /* endDelayFrames = startDelayFrames + (endFrame - startFrame); */

    hiclock = hitime;
    loclock = lotime;
    FMOD_64BIT_ADD(hiclock, loclock, 0, endDelayFrames);
    result = FMOD_Channel_SetDelay(channel, hiclock, loclock, 0);
    if (result != FMOD_OK) {
      fprintf(stderr, "FMOD error: (%d) %s\n", result, FMOD_ErrorString(result));
      fprintf(stderr, "exceptional return value for FMOD::Channel.setDelay() [end] in startPlayback()\n");
      cleanupResources();
      return -1;
    }


	result = FMOD_Channel_SetVolume(channel, 1.0f);
	if ((result != FMOD_OK) && (result != FMOD_ERR_INVALID_HANDLE) && (result != FMOD_ERR_CHANNEL_STOLEN)) {
		fprintf(stderr, "exceptional return value for FMOD::Channel.setVolume() in startPlayback()\n");
		printError(result);
		cleanupResources();
		return -1;
	}

	result = FMOD_Channel_SetPaused(channel, 0);
	if ((result != FMOD_OK) && (result != FMOD_ERR_INVALID_HANDLE) && (result != FMOD_ERR_CHANNEL_STOLEN)) {
		fprintf(stderr, "exceptional return value for FMOD::Channel.setPaused() in startPlayback()\n");
		printError(result);
		cleanupResources();
		return -1;
	}

	FMOD_System_Update(fmsystem);

    return 0;
}

EXPORT_DLL long long stopPlayback(void)
{
	long long toReturn = 0;

	if (currentState == STATE_UNINITIALIZED) {
		fprintf(stderr, "stopPlayback() called but playback not active\n");
		return 0;
	}

	// Get current position before cleanup
	if (currentState >= STATE_PLAYING && channel != NULL) {
		toReturn = streamPosition();
	}

	cleanupResources();
	return toReturn;
}

EXPORT_DLL long long streamPosition(void)
{
	FMOD_RESULT result = FMOD_OK;
	unsigned int frames = 0;

	if (!validateState(STATE_PLAYING)) {
		return -1;
	}

	FMOD_System_Update(fmsystem);

	result = FMOD_Channel_GetPosition(channel, &frames, FMOD_TIMEUNIT_PCM);
	if ((result != FMOD_OK) && (result != FMOD_ERR_INVALID_HANDLE) && (result != FMOD_ERR_CHANNEL_STOLEN)) {
		fprintf(stderr, "exceptional return value for FMOD::Channel.getPosition() in streamPosition()\n");
		printError(result);
		return -1;
	}

	return frames - lastStartFrame;
}

EXPORT_DLL int playbackInProgress(void)
{
	FMOD_RESULT result = FMOD_OK;
	int playing = 0;

	if (currentState < STATE_PLAYING || channel == NULL) {
		return 0;
	}

	result = FMOD_Channel_IsPlaying(channel, &playing);
	if ((result != FMOD_OK) && (result != FMOD_ERR_INVALID_HANDLE) && (result != FMOD_ERR_CHANNEL_STOLEN)) {
		fprintf(stderr, "exceptional return value for FMOD::Channel.isPlaying() in playbackInProgress()\n");
		printError(result);
		return 0; // Assume not playing on error
	}

	return playing;
}

EXPORT_DLL int getLibraryRevisionNumber(void)
{
	return revisionNumber;
}

EXPORT_DLL const char* getLibraryName(void)
{
	return libName;
}

static void printError(FMOD_RESULT result)
{
    fprintf(stderr, "FMOD error: (%d) %s\n", result, FMOD_ErrorString(result));
}

static void cleanupResources(void)
{
    FMOD_RESULT result;

    // Clean up in reverse order of creation
    if (sound != NULL) {
        result = FMOD_Sound_Release(sound);
        if (result != FMOD_OK) {
            fprintf(stderr, "Warning: FMOD_Sound_Release failed during cleanup\n");
            printError(result);
        }
        sound = NULL;
    }

    if (fmsystem != NULL) {
        result = FMOD_System_Close(fmsystem);
        if (result != FMOD_OK) {
            fprintf(stderr, "Warning: FMOD_System_Close failed during cleanup\n");
            printError(result);
        }
        
        result = FMOD_System_Release(fmsystem);
        if (result != FMOD_OK) {
            fprintf(stderr, "Warning: FMOD_System_Release failed during cleanup\n");
            printError(result);
        }
        fmsystem = NULL;
    }

    // Clear all state
    channel = NULL;
    lastStartFrame = 0;
    currentState = STATE_UNINITIALIZED;
}

static int validateState(PlaybackState expected)
{
    if (currentState < expected) {
        fprintf(stderr, "Invalid state: expected at least %d, current is %d\n", expected, currentState);
        return 0;
    }
    
    // Verify pointers match state expectations
    if (expected >= STATE_SYSTEM_CREATED && fmsystem == NULL) {
        fprintf(stderr, "State inconsistency: expected system created but fmsystem is NULL\n");
        currentState = STATE_ERROR;
        return 0;
    }
    
    if (expected >= STATE_SOUND_LOADED && sound == NULL) {
        fprintf(stderr, "State inconsistency: expected sound loaded but sound is NULL\n");
        currentState = STATE_ERROR;
        return 0;
    }
    
    if (expected >= STATE_PLAYING && channel == NULL) {
        fprintf(stderr, "State inconsistency: expected playing but channel is NULL\n");
        currentState = STATE_ERROR;
        return 0;
    }
    
    return 1;
}
