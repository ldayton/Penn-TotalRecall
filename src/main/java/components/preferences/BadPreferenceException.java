package components.preferences;

/** Exception thrown when an attempt is made to store in an ill-formatted or illegal preference. */
public class BadPreferenceException extends Exception {

    private String prefName;

    /**
     * Creates a new <code>BadPreferenceException</code>.
     *
     * @param prefName The name of the preference associated with this exception
     * @param message A message explaining why the preference is illegal
     */
    protected BadPreferenceException(String prefName, String message) {
        super(message);
        this.prefName = prefName;
    }

    /**
     * Getter for the name of the preference associated with this exception.
     *
     * @return The name of the preference associated with this exception
     */
    protected String getPrefName() {
        return prefName;
    }
}
