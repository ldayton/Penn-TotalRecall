package ui.annotations;

import core.annotations.Annotation;
import lombok.NonNull;

/** Centralized column metadata for the annotations table. */
public enum AnnotationColumn {
    TIME("Time (ms)", Double.class) {
        @Override
        public Object value(@NonNull Annotation a) {
            return a.time();
        }
    },
    WORD("Word", String.class) {
        @Override
        public Object value(@NonNull Annotation a) {
            return a.text();
        }
    },
    WORD_NUM("Word #", Integer.class) {
        @Override
        public Object value(@NonNull Annotation a) {
            return a.wordNum();
        }
    };

    private final String header;
    private final Class<?> columnClass;

    AnnotationColumn(String header, Class<?> columnClass) {
        this.header = header;
        this.columnClass = columnClass;
    }

    public int index() {
        return ordinal();
    }

    public String header() {
        return header;
    }

    public Class<?> columnClass() {
        return columnClass;
    }

    public abstract Object value(@NonNull Annotation a);

    public static AnnotationColumn fromIndex(int index) {
        var values = values();
        if (index < 0 || index >= values.length) {
            throw new IllegalArgumentException("column index out of range");
        }
        return values[index];
    }
}
