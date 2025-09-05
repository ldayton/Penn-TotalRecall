package events;

import lombok.Getter;

/** Request to seek playback to an absolute frame. */
@Getter
public class AudioSeekRequestedEvent {
    private final long frame;

    public AudioSeekRequestedEvent(long frame) {
        this.frame = frame;
    }
}
