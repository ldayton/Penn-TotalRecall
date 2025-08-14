package components.annotations;

/** Note: this class has a natural ordering that is inconsistent with equals. */
public class Annotation implements Comparable<Annotation> {

    private final int wordNum;
    private final double time;
    private final String text;

    public Annotation(double time, int wordNum, String text) {
        this.time = time;
        this.wordNum = wordNum;
        this.text = text;
    }

    public double getTime() {
        return time;
    }

    public int getWordNum() {
        return wordNum;
    }

    public String getText() {
        return text;
    }

    @Override
    public int hashCode() {
        return (text + time + wordNum).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Annotation a) {
            if (getText().equals(a.getText())) {
                if (getTime() == a.getTime()) {
                    if (getWordNum() == a.getWordNum()) {
                        if (getWordNum() == a.getWordNum()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Annotation: " + text + " " + time + " ms " + " #" + wordNum;
    }

    @Override
    public int compareTo(Annotation o) {
        return Double.compare(getTime(), o.getTime());
    }
}
