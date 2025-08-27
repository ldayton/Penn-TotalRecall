package waveform;

import java.awt.Image;

/** A chunk of waveform that renders itself. */
public final class WaveformChunk {

    private final int chunkNumber;
    private final Image image;

    /** Creates and renders a waveform chunk. */
    public WaveformChunk(Waveform waveform, int chunkNumber, int height) {
        this.chunkNumber = chunkNumber;
        this.image = waveform.renderChunk(chunkNumber, height);
    }

    /** Returns the chunk number of this part of the waveform. */
    public int getNum() {
        return chunkNumber;
    }

    /** Returns the rendered image of this waveform chunk. */
    public Image getImage() {
        return image;
    }
}
