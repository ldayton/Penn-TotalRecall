package core.audio.session;

import com.google.errorprone.annotations.ThreadSafe;
import core.audio.AudioEngine;
import core.audio.AudioHandle;
import core.audio.PlaybackHandle;
import core.audio.PlaybackListener;
import core.audio.PlaybackState;
import core.dispatch.EventDispatchBus;
import core.dispatch.Subscribe;
import core.events.PlayPauseEvent;
import core.events.SeekByAmountEvent;
import core.events.SeekEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Represents a single audio session with its state and operations. Manages state mutations through
 * commands for consistency and traceability.
 */
@ThreadSafe
@Slf4j
public class AudioSession implements PlaybackListener {

    private final EventDispatchBus eventBus;
    private final AudioEngine audioEngine;
    private final AudioSessionStateMachine stateMachine;
    private final File audioFile;

    // Immutable state managed through commands
    private final AtomicReference<AudioSessionContext> context;

    /** Create a new audio session for a loaded file. */
    public AudioSession(
            @NonNull EventDispatchBus eventBus,
            @NonNull AudioEngine audioEngine,
            @NonNull AudioSessionStateMachine stateMachine,
            @NonNull File audioFile,
            @NonNull AudioHandle audioHandle,
            long totalFrames,
            int sampleRate) {

        this.eventBus = eventBus;
        this.audioEngine = audioEngine;
        this.stateMachine = stateMachine;
        this.audioFile = audioFile;

        // Initialize context with loaded file
        var initialContext = AudioSessionContext.createInitial();
        var loadCommand =
                new AudioSessionCommand.LoadFile(audioFile, audioHandle, totalFrames, sampleRate);
        this.context = new AtomicReference<>(initialContext.apply(loadCommand));

        log.info("Loading audio file - frames: {}, sampleRate: {}", totalFrames, sampleRate);
        eventBus.subscribe(this);

        // Register as a playback listener to receive progress updates
        audioEngine.addPlaybackListener(this);

        log.debug("Created audio session for file: {}", audioFile.getName());
    }

    /** Apply a command to mutate the session state. */
    private void applyCommand(AudioSessionCommand command) {
        AudioSessionContext oldContext = context.get();
        AudioSessionContext newContext = context.updateAndGet(ctx -> ctx.apply(command));

        // Log command application
        String commandName = command.getClass().getSimpleName();
        if ("UpdatePosition".equals(commandName)) {
            log.trace("Applied command: {} at frame {}", commandName, oldContext.playheadFrame());
        } else {
            log.debug("Applied command: {} at frame {}", commandName, oldContext.playheadFrame());
        }
    }

    /** Start playback from current or pending position. */
    public void play() {
        var ctx = context.get();
        if (!ctx.hasAudio()) {
            log.warn("Cannot play - no audio loaded");
            return;
        }

        long startFrame = ctx.pendingStartFrame().orElse(0L);
        long endFrame = ctx.totalFrames();

        PlaybackHandle playback = audioEngine.play(ctx.audioHandle().get(), startFrame, endFrame);
        log.info("Starting playback from frame {} to {}", startFrame, endFrame);
        applyCommand(new AudioSessionCommand.StartPlayback(playback, startFrame));

        // Update state machine
        stateMachine.transitionToPlaying(getCurrentPositionSeconds());
    }

    /** Pause current playback. */
    public void pause() {
        var ctx = context.get();
        if (!ctx.hasPlayback()) {
            log.warn("Cannot pause - no active playback");
            return;
        }

        PlaybackHandle playback = ctx.playbackHandle().get();
        audioEngine.pause(playback);
        long position = audioEngine.getPosition(playback);
        log.info("Pausing playback at frame {}", position);

        applyCommand(new AudioSessionCommand.PausePlayback(position));
        stateMachine.transitionToPaused(getCurrentPositionSeconds());
    }

    /** Resume paused playback. */
    public void resume() {
        var ctx = context.get();
        if (!ctx.hasPlayback()) {
            log.warn("Cannot resume - no active playback");
            return;
        }

        PlaybackHandle playback = ctx.playbackHandle().get();
        audioEngine.resume(playback);
        long position = audioEngine.getPosition(playback);
        log.info("Resuming playback from frame {}", position);

        applyCommand(new AudioSessionCommand.ResumePlayback(position));
        stateMachine.transitionToPlaying(getCurrentPositionSeconds());
    }

    /** Stop playback and return to ready state. */
    public void stop() {
        var ctx = context.get();
        if (ctx.hasPlayback()) {
            audioEngine.stop(ctx.playbackHandle().get());
            ctx.playbackHandle().get().close();
            log.info("Stopping playback");
        }

        applyCommand(new AudioSessionCommand.StopPlayback());
        stateMachine.transitionToReady(getCurrentPositionSeconds());
    }

    /** Seek to a specific position. */
    public void seek(long targetFrame) {
        var ctx = context.get();

        if (ctx.hasPlayback()) {
            // Active playback - seek immediately
            audioEngine.seek(ctx.playbackHandle().get(), targetFrame);
            log.info(
                    "Seeking to frame {} ({}s)",
                    targetFrame,
                    ctx.sampleRate() > 0
                            ? String.format("%.2f", targetFrame / (double) ctx.sampleRate())
                            : "0");
            // Keep explicit playhead in sync with engine position
            applyCommand(new AudioSessionCommand.UpdatePosition(targetFrame));
        } else if (stateMachine.getCurrentState() == AudioSessionStateMachine.State.READY) {
            // No playback - set pending position
            applyCommand(new AudioSessionCommand.SetPendingSeek(targetFrame));
            log.info(
                    "Set pending seek to frame {} ({}s)",
                    targetFrame,
                    ctx.sampleRate() > 0
                            ? String.format("%.2f", targetFrame / (double) ctx.sampleRate())
                            : "0");
        }
    }

    /** Seek by a relative amount. */
    public void seekByAmount(SeekByAmountEvent.Direction direction, int milliseconds) {
        var ctx = context.get();

        // Determine base position
        long currentFrame = 0;
        if (ctx.hasPlayback()) {
            currentFrame = audioEngine.getPosition(ctx.playbackHandle().get());
        } else {
            currentFrame = ctx.pendingStartFrame().orElse(0L);
        }

        // Calculate target
        long shiftFrames = (long) ((milliseconds / 1000.0) * ctx.sampleRate());
        long targetFrame =
                direction == SeekByAmountEvent.Direction.FORWARD
                        ? currentFrame + shiftFrames
                        : currentFrame - shiftFrames;

        // Clamp to valid range
        targetFrame = Math.max(0, Math.min(targetFrame, ctx.totalFrames() - 1));

        seek(targetFrame);
    }

    /** Get current playback position in seconds. */
    public double getCurrentPositionSeconds() {
        long frames = getCurrentPositionFrames();
        var ctx = context.get();
        return ctx.sampleRate() > 0 ? (double) frames / ctx.sampleRate() : 0.0;
    }

    /** Get current playback position in frames. */
    public long getCurrentPositionFrames() {
        var ctx = context.get();

        // If actively playing, get real-time position from audio engine
        if (ctx.hasPlayback() && getState() == AudioSessionStateMachine.State.PLAYING) {
            return audioEngine.getPosition(ctx.playbackHandle().get());
        }

        // Otherwise return stored position (paused, stopped, or seeking)
        return ctx.playheadFrame();
    }

    /** Dispose of this session and release resources. */
    public void dispose() {
        stop();
        eventBus.unsubscribe(this);
        audioEngine.removePlaybackListener(this);
        log.debug("Disposed audio session for file: {}", audioFile.getName());
    }

    // Event handlers

    @Subscribe
    public void onPlayPause(@NonNull PlayPauseEvent event) {
        var state = stateMachine.getCurrentState();
        switch (state) {
            case READY -> play();
            case PLAYING -> pause();
            case PAUSED -> resume();
            default -> log.warn("PlayPause in state: {}", state);
        }
    }

    @Subscribe
    public void onSeek(@NonNull SeekEvent event) {
        seek(event.frame());
    }

    @Subscribe
    public void onSeekByAmount(@NonNull SeekByAmountEvent event) {
        seekByAmount(event.direction(), event.milliseconds());
    }

    // Getters

    public AudioSessionContext getContext() {
        return context.get();
    }

    public File getAudioFile() {
        return audioFile;
    }

    public AudioSessionStateMachine.State getState() {
        return stateMachine.getCurrentState();
    }

    // PlaybackListener implementation

    @Override
    public void onProgress(
            @NonNull PlaybackHandle playback, long positionFrames, long totalFrames) {
        // Update the playhead position as playback progresses
        applyCommand(new AudioSessionCommand.UpdatePosition(positionFrames));
    }

    @Override
    public void onStateChanged(
            @NonNull PlaybackHandle playback,
            @NonNull PlaybackState newState,
            @NonNull PlaybackState oldState) {
        log.debug("Playback state changed from {} to {}", oldState, newState);
    }

    @Override
    public void onPlaybackComplete(@NonNull PlaybackHandle playback) {
        log.info("Playback completed");
        applyCommand(new AudioSessionCommand.StopPlayback());
        stateMachine.transitionToReady();
    }

    @Override
    public void onPlaybackError(PlaybackHandle playback, @NonNull String error) {
        log.error("Playback error: {}", error);
        applyCommand(new AudioSessionCommand.SetLoadError(error));
        stateMachine.transitionToError(0.0); // Error occurred at current position
    }
}
