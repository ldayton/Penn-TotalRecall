package core.waveform;

public record ScreenDimension(int x, int y, int width, int height) {

    public static ScreenDimension atOrigin(int width, int height) {
        return new ScreenDimension(0, 0, width, height);
    }

    public boolean contains(int pointX, int pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }
}
