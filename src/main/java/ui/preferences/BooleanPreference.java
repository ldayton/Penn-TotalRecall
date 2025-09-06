package ui.preferences;

import app.di.GuiceBootstrap;
import java.awt.GridLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/**
 * An <code>AbstractPreferenceDisplay</code> for choosing between one of two options.
 *
 * <p>It is easy to extend use this class to add a new preference chooser to support new features.
 * See {@link PreferencesFrame} for examples of this class in use.
 *
 * <p>This class automatically uses the correct {@link java.util.prefs.Preferences} object, in
 * keeping with program policy.
 */
public class BooleanPreference extends AbstractPreferenceDisplay {

    private boolean lastPref = false;

    private final JRadioButton trueButton;
    private final JRadioButton falseButton;

    private final boolean defValue;

    private final String prefKey;
    private final String prefTitle;

    /**
     * Creates a new <code>BooleanPreferences</code> hooked up to the provided Preferences object,
     * key, and default value.
     *
     * @param prefTitle The title of the preference, will be displayed graphically for the user
     * @param prefKey The key for the <code>java.util.prefs.Preferences object</code>, should be
     *     stored in <code>info.PreferenceKeys</code>
     * @param truePrefName The name of the option corresponding to <code>true</code>, will be
     *     displayed graphically for the user
     * @param falsePrefName The name of the option corresponding to <code>false</code>, will be
     *     displayed graphically for the user
     * @param defValue The default value, should be stored in <code>info.PreferenceKeys</code>
     */
    protected BooleanPreference(
            String prefTitle,
            String prefKey,
            String truePrefName,
            String falsePrefName,
            boolean defValue) {
        super(prefTitle);
        this.prefKey = prefKey;
        this.prefTitle = prefTitle;
        this.defValue = defValue;
        trueButton = new JRadioButton(truePrefName);
        falseButton = new JRadioButton(falsePrefName);
        ButtonGroup group = new ButtonGroup();
        group.add(falseButton);
        group.add(trueButton);

        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        if (preferencesManager.getBoolean(prefKey, defValue) == false) {
            preferencesManager.putBoolean(prefKey, false);
            falseButton.setSelected(true);
            lastPref = false;
        } else {
            preferencesManager.putBoolean(prefKey, true);
            trueButton.setSelected(true);
            lastPref = true;
        }

        JPanel radioPanel = new JPanel(new GridLayout(0, 1));
        radioPanel.add(trueButton);
        radioPanel.add(falseButton);
        add(radioPanel);
    }

    /** {@inheritDoc} */
    @Override
    protected boolean save() {
        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        if (trueButton.isSelected()) {
            lastPref = true;
            preferencesManager.putBoolean(prefKey, true);
        } else {
            lastPref = false;
            preferencesManager.putBoolean(prefKey, false);
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean isChanged() {
        if (lastPref == false) {
            if (trueButton.isSelected()) {
                return true;
            } else {
                return false;
            }
        } else {
            if (trueButton.isSelected()) {
                return false;
            } else {
                return true;
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void graphicallyRevert() {
        if (lastPref == true) {
            trueButton.setSelected(true);
        } else {
            falseButton.setSelected(true);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void restoreDefault() {
        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        preferencesManager.putBoolean(prefKey, defValue);
        if (defValue == true) {
            trueButton.setSelected(true);
            lastPref = true;
        } else {
            falseButton.setSelected(true);
            lastPref = false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return getClass().getName() + ": " + prefTitle;
    }
}
