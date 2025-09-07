package audio.fmod;

/**
 * Exception intentionally thrown by test listeners to verify error handling. This class exists
 * solely to make test exceptions unambiguous - when the FmodListenerManager catches this specific
 * exception type, it knows it's an expected test condition and can suppress the stack trace.
 */
class TestListenerException extends RuntimeException {
    TestListenerException(String message) {
        super(message);
    }
}
