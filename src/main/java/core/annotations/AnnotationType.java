package core.annotations;

/** Types of annotations that can be created in the system. */
public enum AnnotationType {
    /** Regular word annotation from the word pool */
    REGULAR,

    /** Intrusion marking for false recall */
    INTRUSION,

    /** Custom annotation not in the word pool */
    CUSTOM
}
