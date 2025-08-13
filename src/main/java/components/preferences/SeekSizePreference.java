package components.preferences;

import components.MyMenu;
import info.UserPrefs;
import java.text.DecimalFormat;
import java.text.ParseException;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Preference for the sizes of forward/backward jumps of <code>SeekActions</code> and <code>
 * Last200PlusMoveActions</code>.
 *
 * <p>Does not require restart or reload of audio file.
 */
public class SeekSizePreference extends AbstractPreferenceDisplay {

    private static final int minVal = 1;
    private static final int step = 5;

    private JSpinner spinner;

    private int defValue;

    public enum ShiftSize {
        SMALL_SHIFT,
        MEDIUM_SHIFT,
        LARGE_SHIFT
    };

    private ShiftSize size;

    protected SeekSizePreference(String title, ShiftSize size) {
        super(title);
        this.size = size;
        defValue =
                switch (size) {
                    case SMALL_SHIFT -> UserPrefs.defaultSmallShift;
                    case MEDIUM_SHIFT -> UserPrefs.defaultMediumShift;
                    case LARGE_SHIFT -> UserPrefs.defaultLargeShift;
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
        return switch (size) {
            case SMALL_SHIFT -> UserPrefs.getSmallShift();
            case MEDIUM_SHIFT -> UserPrefs.getMediumShift();
            case LARGE_SHIFT -> UserPrefs.getLargeShift();
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
        MyMenu.updateSeekActions();
    }

    @Override
    protected boolean save() throws BadPreferenceException {
        saveVal((Integer) spinner.getValue());
        MyMenu.updateSeekActions();
        return true;
    }

    private void saveVal(int nVal) {
        switch (size) {
            case SMALL_SHIFT -> UserPrefs.setSmallShift(nVal);
            case MEDIUM_SHIFT -> UserPrefs.setMediumShift(nVal);
            case LARGE_SHIFT -> UserPrefs.setLargeShift(nVal);
        }
    }
}
