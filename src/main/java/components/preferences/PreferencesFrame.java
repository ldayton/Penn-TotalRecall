package components.preferences;

import components.MainFrame;
import env.KeyboardManager;
import env.PreferenceKeys;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import ui.DialogCentering;
import ui.MainWindowAccess;
import ui.UiConstants;

/**
 * A <code>JFrame</code> that will contain preferences choosers in a scrollable vertical column.
 *
 * <p>See package-level documentation for information on adding new preference choosers.
 */
@Singleton
public class PreferencesFrame extends JFrame implements WindowListener {

    private static PreferencesFrame instance;
    private final KeyboardManager keyboardManager;
    // Save/Restore actions are local UI actions owned by this frame to avoid DI cycles

    private JPanel prefPanel;
    private final JScrollPane prefScrollPane;
    private JPanel buttonPanel;

    /**
     * Constructs a <code>PreferencesFrame</code> and initializes spacing behavior of its internal
     * components.
     *
     * <p>Adds all the known <code>AbstractPreferenceDisplays</code>. Also adds "save" and "restore
     * defaults" buttons to the end of the frame that are hooked up to the individual preference
     * choosers.
     *
     * <p>See documentation in {@link preferences.AbstractPreferenceDisplay} for program-wide
     * policies on user preferences, as well as information on adding new preference choosers.
     */
    @Inject
    public PreferencesFrame(
            KeyboardManager keyboardManager,
            env.LookAndFeelManager lookAndFeelManager,
            MainWindowAccess windowService) {
        this.keyboardManager = keyboardManager;
        // Actions are constructed locally within initButtonsPanel to avoid circular DI

        // force handling by the WindowListener (this.windowClosing())
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(this);

        // Get preferences string from LookAndFeelManager
        setTitle(lookAndFeelManager.getPreferencesString());

        // the content pane will have two main areas, one for preference choosers
        // (AbstractPreferenceDisplays), and one for the save/restore defaults buttons
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        setContentPane(contentPane);

        initPrefPanel();
        // to ensure correct resizing behavior from all the AbstractPreferenceDisplays we make them
        // prefer a maximum width and their preferred height
        for (int i = 0; i < prefPanel.getComponentCount(); i++) {
            Component c = prefPanel.getComponent(i);
            if (c instanceof AbstractPreferenceDisplay) {
                c.setMaximumSize(
                        new Dimension(Integer.MAX_VALUE, (int) c.getPreferredSize().getHeight()));
            }
        }
        initButtonsPanel();

        // since there may be too many preferences in the prefPanel to fit in the PreferencesFrame,
        // we add prefPanel to a scroll pane
        prefScrollPane = new JScrollPane();
        prefScrollPane.setViewportView(prefPanel);

        prefScrollPane.getHorizontalScrollBar().setUnitIncrement(15);
        prefScrollPane.getVerticalScrollBar().setUnitIncrement(15);

        contentPane.add(prefScrollPane);
        contentPane.add(buttonPanel);

        // gets ride of the java icon in the top left corner of the frame (Windows, GNOME, KDE,
        // among others)
        setIconImage(
                Toolkit.getDefaultToolkit()
                        .getImage(MainFrame.class.getResource("/images/headphones16.png")));

        // the frame's width should be big enough for the button panel and big enough for even the
        // largest AbstractPreferenceDisplay
        int buttonPanelPrefferedSize = (int) buttonPanel.getPreferredSize().getWidth();
        int frameWidth =
                Math.max(
                        (int) prefScrollPane.getPreferredSize().getWidth(),
                        buttonPanelPrefferedSize);
        if (windowService == null) {
            throw new IllegalStateException("MainWindowAccess not available via DI");
        }
        int frameHeight = (int) ((2.0 / 3.0) * windowService.getSize().getHeight());
        setSize(frameWidth + 40, frameHeight);

        // let escape button be used to request preferences window closed
        contentPane
                .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "exit");
        contentPane
                .getActionMap()
                .put(
                        "exit",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                instance.windowClosing(
                                        new WindowEvent(instance, WindowEvent.WINDOW_CLOSING));
                            }
                        });

        // Set the singleton instance after full initialization
        instance = this;
    }

    /** Add all preference displays to the preferences panel. */
    private void initPrefPanel() {
        // the panel that the AbstractPreferenceDisplays will be added to
        prefPanel = new JPanel();
        prefPanel.setLayout(new BoxLayout(prefPanel, BoxLayout.Y_AXIS));

        //		prefPanel.add(new AudioSystemPreference());
        prefPanel.add(
                new SeekSizePreference(
                        "Small Seek (ms)", SeekSizePreference.ShiftSize.SMALL_SHIFT));
        prefPanel.add(
                new SeekSizePreference(
                        "Medium Seek (ms)", SeekSizePreference.ShiftSize.MEDIUM_SHIFT));
        prefPanel.add(
                new SeekSizePreference(
                        "Large Seek (ms)", SeekSizePreference.ShiftSize.LARGE_SHIFT));
        BooleanPreference warnExitPref =
                new BooleanPreference(
                        "Warn on Exit",
                        PreferenceKeys.WARN_ON_EXIT,
                        "Yes",
                        "No",
                        PreferenceKeys.DEFAULT_WARN_ON_EXIT);
        prefPanel.add(warnExitPref);
        BooleanPreference warnSwitchPref =
                new BooleanPreference(
                        "Warn on File Switch",
                        PreferenceKeys.WARN_FILE_SWITCH,
                        "Yes",
                        "No",
                        PreferenceKeys.DEFAULT_WARN_FILE_SWITCH);
        prefPanel.add(warnSwitchPref);
        if (keyboardManager.shouldShowEmacsKeybindingOption()) {
            BooleanPreference useEmacs =
                    new BooleanPreference(
                            "Use Emacs Keybindings",
                            PreferenceKeys.USE_EMACS,
                            "Yes",
                            "No",
                            PreferenceKeys.DEFAULT_USE_EMACS);
            prefPanel.add(useEmacs);
        }

        // excess space between the preference choosers and the buttons should not cause the
        // preferences to move
        prefPanel.add(Box.createVerticalGlue());
    }

    /** Initializes the restore defaults/save prefs button area. */
    private void initButtonsPanel() {
        // the panel that will contain the save/restore defaults buttons
        buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.add(Box.createHorizontalGlue()); // glue added before and after every button so
        // they will evenly take up all the width of the
        // frame

        JButton jbSavePrefs = new JButton("Save Preferences");
        jbSavePrefs.addActionListener(new SavePreferencesAction(this));
        buttonPanel.add(jbSavePrefs);
        buttonPanel.add(Box.createHorizontalGlue());

        JButton jbRestoreDefaults = new JButton("Restore Defaults");
        jbRestoreDefaults.addActionListener(new RestoreDefaultsAction(this));
        buttonPanel.add(jbRestoreDefaults);
        buttonPanel.add(Box.createHorizontalGlue());

        // Add spacing around button panel for clean FlatLaf appearance
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
    }

    /**
     * Every time the frame is set to visible, it should appear in the middle of MainFrame, the
     * app's main window.
     */
    @Override
    public void setVisible(boolean visible) {
        if (visible == true) {
            setLocation(DialogCentering.chooseLocation(this));
        }
        super.setVisible(visible);
    }

    /**
     * Getter for the displayed <code>AbstractPreferenceDisplays</code> for the benefit of <code>
     * SavePreferencesAction</code> and others.
     *
     * @return All the <code>AbstractPreferenceDisplays</code> that are nested in any level in this
     *     <code>PreferencesFrame</code>
     */
    protected List<AbstractPreferenceDisplay> getAbstractPreferences() {
        List<AbstractPreferenceDisplay> allPrefs = new ArrayList<>();
        Component[] allComps = prefPanel.getComponents();
        for (int i = 0; i < allComps.length; i++) {
            if (allComps[i] instanceof AbstractPreferenceDisplay) {
                allPrefs.add((AbstractPreferenceDisplay) allComps[i]);
            }
        }
        return allPrefs;
    }

    /**
     * Handler for a user request to close the window (e.g., clicking on the frame's x button.
     *
     * <p>After verifying that the user has not made any changes to the preferences, makes the frame
     * invisible. However, if changes are detected, the user will be warned that preferences will be
     * lost (reverted to saved values) if he/she decides to exit.
     */
    @Override
    public void windowClosing(WindowEvent e) {
        List<AbstractPreferenceDisplay> allPrefs = this.getAbstractPreferences();
        boolean somethingChanged = false;
        for (AbstractPreferenceDisplay pref : allPrefs) {
            if (pref.isChanged() == true) {
                somethingChanged = true;
            }
        }
        if (somethingChanged) {
            String message =
                    "One or more preferences have changed.\n"
                            + "Are you sure you want to exit and lose your changes?";
            int response =
                    JOptionPane.showConfirmDialog(
                            this, message, UiConstants.yesNoDialogTitle, JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.YES_OPTION) {
                for (AbstractPreferenceDisplay pref : allPrefs) {
                    pref.graphicallyRevert();
                }
                setVisible(false);
            } else {
                toFront(); // to make sure PreferenceFrame will be in foreground
                return;
            }
        } else {
            setVisible(false);
        }
    }

    /** empty implementation {@inheritDoc} */
    @Override
    public void windowActivated(WindowEvent e) {}

    /** empty implementation {@inheritDoc} */
    @Override
    public void windowClosed(WindowEvent e) {}

    /** empty implementation {@inheritDoc} */
    @Override
    public void windowDeactivated(WindowEvent e) {}

    /** empty implementation {@inheritDoc} */
    @Override
    public void windowDeiconified(WindowEvent e) {}

    /** empty implementation {@inheritDoc} */
    @Override
    public void windowIconified(WindowEvent e) {}

    /** empty implementation {@inheritDoc} */
    @Override
    public void windowOpened(WindowEvent e) {}

    /**
     * Singleton accessor
     *
     * @return The singleton <code>PreferencesFrame</code>
     */
    public static PreferencesFrame getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "PreferencesFrame not initialized via DI. Ensure GuiceBootstrap.create() was"
                            + " called first.");
        }
        return instance;
    }
}
