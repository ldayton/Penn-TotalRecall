package core.env;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;

/**
 * Manages user-specific information.
 *
 * <p>Provides access to the user's home directory for file operations and preferences.
 */
@Singleton
public class UserHomeProvider {

    /** The user's home directory. */
    @Getter private final String userHomeDir;

    @Inject
    public UserHomeProvider() {
        this.userHomeDir = System.getProperty("user.home");
    }
}
