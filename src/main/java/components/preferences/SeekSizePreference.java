package components.preferences;

import components.AppMenuBar;
import di.GuiceBootstrap;
import env.PreferencesManager;
import java.text.DecimalFormat;
import java.text.ParseException;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import util.PreferenceKeys;

/**
 * Preference for the sizes of forward/backward jumps of <code>SeekActions</code> and <code>
 * Last200PlusMoveActions</code>.
 *
 * <p>Does not require restart or reload of audio file.
 */
public class SeekSizePreference extends AbstractPreferenceDisplay {

    private static final int minVal = 1;
    private static final int step = 5;

    private final JSpinner spinner;

    private final int defValue;

    public enum ShiftSize {
        SMALL_SHIFT,
        MEDIUM_SHIFT,
        LARGE_SHIFT
    }

    private final ShiftSize size;

    protected SeekSizePreference(String title, ShiftSize size) {
        super(title);
        this.size = size;
        defValue =
                switch (size) {
                    case SMALL_SHIFT -> PreferenceKeys.DEFAULT_SMALL_SHIFT;
                    case MEDIUM_SHIFT -> PreferenceKeys.DEFAULT_MEDIUM_SHIFT;
                    case LARGE_SHIFT -> PreferenceKeys.DEFAULT_LARGE_SHIFT;
                };
        spinner = new JSpinner();
        SpinnerNumberModel model = new SpinnerNumberModel();
        model.setStepSize(step);
        model.setMinimum(minVal);
        model.setMaximum(Integer.MAX_VALUE);

        model.setValue(getCurrentVal());
        spinner.setModel(model);

        add(spinner);
    }

    private int getCurrentVal() {
        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        return switch (size) {
            case SMALL_SHIFT ->
                    preferencesManager.getInt(
                            PreferenceKeys.SMALL_SHIFT, PreferenceKeys.DEFAULT_SMALL_SHIFT);
            case MEDIUM_SHIFT ->
                    preferencesManager.getInt(
                            PreferenceKeys.MEDIUM_SHIFT, PreferenceKeys.DEFAULT_MEDIUM_SHIFT);
            case LARGE_SHIFT ->
                    preferencesManager.getInt(
                            PreferenceKeys.LARGE_SHIFT, PreferenceKeys.DEFAULT_LARGE_SHIFT);
        };
    }

    @Override
    protected void graphicallyRevert() {
        spinner.setValue(getCurrentVal());
    }

    @Override
    protected boolean isChanged() {
        JSpinner.NumberEditor editor = (JSpinner.NumberEditor) spinner.getEditor();
        String curContents = editor.getTextField().getText();
        DecimalFormat format = editor.getFormat();
        Number num = null;
        try {
            num = format.parse(curContents);
        } catch (ParseException e) {
            // Ignore parse exception - num will remain null and be handled below
        }
        if (num == null) {
            return true;
        } else {
            return num.intValue() != getCurrentVal();
        }
    }

    @Override
    protected void restoreDefault() {
        spinner.setValue(defValue);
        saveVal(defValue);
        AppMenuBar.updateSeekActions();
    }

    @Override
    protected boolean save() {
        saveVal((Integer) spinner.getValue());
        AppMenuBar.updateSeekActions();
        return true;
    }

    private void saveVal(int nVal) {
        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");
        switch (size) {
            case SMALL_SHIFT -> preferencesManager.putInt(PreferenceKeys.SMALL_SHIFT, nVal);
            case MEDIUM_SHIFT -> preferencesManager.putInt(PreferenceKeys.MEDIUM_SHIFT, nVal);
            case LARGE_SHIFT -> preferencesManager.putInt(PreferenceKeys.LARGE_SHIFT, nVal);
        }
    }
}
