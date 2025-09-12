package ui;

import core.env.Platform;
import core.env.ProgramName;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import lombok.NonNull;
import ui.layout.MainFrame;

/**
 * Service for managing all dialog operations in the application.
 *
 * <p>Centralizes dialog creation for better dependency injection and testability.
 */
@Singleton
public class DialogService {

    /** Title of all yes/no dialogs in the program. */
    private static final String YES_NO_DIALOG_TITLE = "Select an Option";

    /** Standard String asking whether or not user would like to see similar dialogs again. */
    private static final String DONT_SHOW_AGAIN_STRING = "Do not show this message again.";

    /** Title of all error dialogs in the program. */
    private static final String ERROR_DIALOG_TITLE = "Error";

    private final Provider<MainFrame> mainFrameProvider;
    private final ImageIcon appIcon;
    private final ProgramName programName;
    private final Platform platform;

    @Inject
    public DialogService(
            @NonNull Provider<MainFrame> mainFrameProvider,
            @NonNull ProgramName programName,
            @NonNull Platform platform) {
        this.mainFrameProvider = mainFrameProvider;
        this.programName = programName;
        this.platform = platform;
        this.appIcon = new ImageIcon(DialogService.class.getResource("/images/headphones48.png"));
        UIManager.put("OptionPane.sameSizeButtons", Boolean.TRUE);
    }

    /**
     * Shows an error dialog with the provided message.
     *
     * @param message The error message to display
     */
    public void showError(@NonNull String message) {
        JOptionPane.showMessageDialog(
                mainFrameProvider.get(),
                formatMessageAsHtml(message),
                ERROR_DIALOG_TITLE,
                JOptionPane.ERROR_MESSAGE,
                appIcon);
    }

    /**
     * Shows an info dialog with the provided message.
     *
     * @param message The info message to display
     */
    public void showInfo(@NonNull String message) {
        JOptionPane.showMessageDialog(
                mainFrameProvider.get(),
                formatMessageAsHtml(message),
                programName.toString(),
                JOptionPane.INFORMATION_MESSAGE,
                appIcon);
    }

    /**
     * Shows a confirmation dialog with the provided message.
     *
     * @param message The confirmation message
     * @return true if user clicked Yes, false otherwise
     */
    public boolean showConfirm(@NonNull String message) {
        JPanel content = buildConfirmContentPanel(null, message, null);
        var pane =
                new JOptionPane(content, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);

        if (platform.detect() == Platform.PlatformType.MACOS) {
            // Follow macOS convention: primary action on left, cancel on right
            pane.setOptions(new Object[] {"Yes", "No"});
            pane.setInitialValue("No");
        } else {
            // Windows/Linux convention: cancel on left, primary action on right
            pane.setOptions(new Object[] {"No", "Yes"});
            pane.setInitialValue("Yes");
        }

        Object val = showAsSheet(pane);
        return "Yes".equals(val);
    }

    /**
     * Shows a confirmation dialog with a "Don't show again" checkbox.
     *
     * @param message The confirmation message
     * @return ConfirmationResult containing the user's choice and checkbox state
     */
    public ConfirmationResult showConfirmWithDontShowAgain(@NonNull String message) {
        JCheckBox checkbox = new JCheckBox(DONT_SHOW_AGAIN_STRING);
        JPanel content = buildConfirmContentPanel(null, message, checkbox);

        var pane =
                new JOptionPane(content, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);

        if (platform.detect() == Platform.PlatformType.MACOS) {
            // Follow macOS convention: primary action on left, cancel on right
            pane.setOptions(new Object[] {"Yes", "No"});
            pane.setInitialValue("No");
        } else {
            // Windows/Linux convention: cancel on left, primary action on right
            pane.setOptions(new Object[] {"No", "Yes"});
            pane.setInitialValue("Yes");
        }

        Object val = showAsSheet(pane);
        boolean confirmed = "Yes".equals(val);
        boolean dontShowAgain = checkbox.isSelected();

        return new ConfirmationResult(confirmed, dontShowAgain);
    }

    /**
     * Shows a JFileChooser dialog for opening files or directories.
     *
     * @param title The dialog title
     * @param initialDirectory The initial directory to show
     * @param selectionMode The file selection mode
     * @param fileFilter Optional file filter
     * @return The selected file, or null if cancelled
     */
    public File showFileChooser(
            @NonNull String title,
            @NonNull String initialDirectory,
            int selectionMode,
            FileFilter fileFilter) {
        JFileChooser jfc = new JFileChooser(initialDirectory);
        jfc.setDialogTitle(title);
        jfc.setFileSelectionMode(selectionMode);

        if (fileFilter != null) {
            jfc.setFileFilter(fileFilter);
        }

        int result = jfc.showOpenDialog(mainFrameProvider.get());

        if (result == JFileChooser.APPROVE_OPTION) {
            return jfc.getSelectedFile();
        }

        return null;
    }

    /** Result of a confirmation dialog with "Don't show again" checkbox. */
    public static class ConfirmationResult {
        private final boolean confirmed;
        private final boolean dontShowAgain;

        public ConfirmationResult(boolean confirmed, boolean dontShowAgain) {
            this.confirmed = confirmed;
            this.dontShowAgain = dontShowAgain;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public boolean isDontShowAgain() {
            return dontShowAgain;
        }
    }

    // ===== Private helpers =====

    private void enableMacSheet(@NonNull JDialog dialog) {
        if (platform.detect() == Platform.PlatformType.MACOS) {
            dialog.getRootPane().putClientProperty("apple.awt.documentModalSheet", Boolean.TRUE);
            // Sheets must be document-modal to attach to the parent window
            dialog.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
            dialog.setResizable(false);
        }
    }

    /** Shows the given option pane as a dialog (sheet on macOS) and returns the selected value. */
    private Object showAsSheet(@NonNull JOptionPane pane) {
        var owner = mainFrameProvider.get();
        var dialog = new JDialog(owner, programName.toString(), Dialog.ModalityType.DOCUMENT_MODAL);
        enableMacSheet(dialog); // must be set before packing
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(pane);
        dialog.setMinimumSize(new Dimension(420, dialog.getMinimumSize().height));

        // Close the dialog when the pane value changes (Yes/No clicked or Esc/Enter pressed)
        pane.addPropertyChangeListener(
                evt -> {
                    String p = evt.getPropertyName();
                    if ((JOptionPane.VALUE_PROPERTY.equals(p)
                                    || JOptionPane.INPUT_VALUE_PROPERTY.equals(p))
                            && dialog.isVisible()
                            && evt.getSource() == pane) {
                        Object v = pane.getValue();
                        if (v != JOptionPane.UNINITIALIZED_VALUE) {
                            dialog.setVisible(false);
                        }
                    }
                });

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        pane.selectInitialValue();
        dialog.setVisible(true);
        Object value = pane.getValue();
        dialog.dispose();
        return value;
    }

    private JPanel buildConfirmContentPanel(
            String headerText, @NonNull String message, JCheckBox dontShowAgain) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        if (headerText != null && !headerText.isEmpty()) {
            JLabel header = new JLabel(headerText);
            header.setFont(header.getFont().deriveFont(Font.BOLD));
            header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
            panel.add(header, BorderLayout.NORTH);
        }

        JLabel body = new JLabel(formatMessageAsHtml(message));
        panel.add(body, BorderLayout.CENTER);

        if (dontShowAgain != null) {
            JPanel south = new JPanel(new BorderLayout());
            south.setOpaque(false);
            south.add(dontShowAgain, BorderLayout.WEST);
            panel.add(south, BorderLayout.SOUTH);
        }

        return panel;
    }

    private String formatMessageAsHtml(@NonNull String message) {
        return "<html><div style='width:360px; line-height:1.35;'>"
                + message.replace("\n", "<br>")
                + "</div></html>";
    }
}
