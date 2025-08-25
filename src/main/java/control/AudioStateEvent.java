package control;

import audio.AudioEvent;

/**
 * Event wrapper for audio state changes that can be safely published to EventDispatchBus.
 *
 * <p>This event wraps AudioEvent instances and ensures they are processed on the Event Dispatch
 * Thread (EDT) for Swing thread safety.
 */
public record AudioStateEvent(AudioEvent audioEvent) {

    /**
     * Creates a new AudioStateEvent wrapping the given AudioEvent.
     *
     * @param audioEvent The audio event to wrap
     */
    public AudioStateEvent(AudioEvent audioEvent) {
        this.audioEvent = audioEvent;
    }

    /**
     * Gets the wrapped AudioEvent.
     *
     * @return The wrapped audio event
     */
    public AudioEvent getAudioEvent() {
        return audioEvent;
    }
}
