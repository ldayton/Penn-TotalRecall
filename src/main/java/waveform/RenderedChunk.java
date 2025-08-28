package waveform;

import java.awt.Image;

/** Rendered waveform chunk with its identifying number. */
public record RenderedChunk(int chunkNumber, Image image) {}
