package env;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Service that provides the application name from configuration. */
@Singleton
public class ProgramName {
    private final String programName;

    @Inject
    public ProgramName(AppConfig appConfig) {
        this.programName = appConfig.getProperty(AppConfig.APP_NAME_KEY);
    }

    @Override
    public String toString() {
        return programName;
    }
}
