package components.wordpool;

import behaviors.multiact.AnnotateAction;
import env.KeyboardManager;
import info.UserPrefs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.AWTKeyStroke;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;

/**
 * Custom <code>JTextField</code> for entering annotations.
 *
 * <p>Includes features to aid in annotation speed and accuracy that were added to PyParse over the
 * years.
 */
@Singleton
public class WordpoolTextField extends JTextField implements KeyListener, FocusListener {

    private static WordpoolTextField instance;

    private String clipboard = "";
    private final KeyboardManager keyboardManager;

    @Inject
    public WordpoolTextField(KeyboardManager keyboardManager) {
        this.keyboardManager = keyboardManager;
        setPreferredSize(new Dimension(Integer.MAX_VALUE, 30));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "annotate regular");
        getActionMap().put("annotate regular", new AnnotateAction(AnnotateAction.Mode.REGULAR));

        Set<AWTKeyStroke> keys = new HashSet<AWTKeyStroke>();
        keys.add(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK, false));
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, keys);

        addKeyListener(this);

        // emacs key bindings
        if (UserPrefs.prefs.getBoolean(UserPrefs.useEmacs, UserPrefs.defaultUseEmacs)) {
            JTextComponent.KeyBinding[] newBindings = {
                new JTextComponent.KeyBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK, false),
                        DefaultEditorKit.beginLineAction),
                new JTextComponent.KeyBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, false),
                        DefaultEditorKit.endLineAction),
                new JTextComponent.KeyBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK, false),
                        DefaultEditorKit.backwardAction),
                new JTextComponent.KeyBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK, false),
                        DefaultEditorKit.forwardAction),
                new JTextComponent.KeyBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, false),
                        DefaultEditorKit.deleteNextCharAction)
            };

            Keymap k = getKeymap();
            JTextComponent.loadKeymap(k, newBindings, getActions());
        }
        addFocusListener(this);
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, false),
                        "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, false),
                        "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_RIGHT, keyboardManager.getMenuKey(), false),
                        "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_LEFT, keyboardManager.getMenuKey(), false),
                        "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_RIGHT,
                                keyboardManager.getMenuKey() + InputEvent.SHIFT_DOWN_MASK,
                                false),
                        "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_LEFT,
                                keyboardManager.getMenuKey() + InputEvent.SHIFT_DOWN_MASK,
                                false),
                        "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "clear");
        getActionMap()
                .put(
                        "clear",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                setText("");
                                getParent().requestFocusInWindow();
                            }
                        });

        // Set the singleton instance after full initialization
        instance = this;
    }

    @Override
    protected Document createDefaultModel() {
        WordpoolDocument doc = new WordpoolDocument();
        doc.initialize();
        return doc;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getModifiersEx() == 0) { // no modifiers
            if (e.getKeyCode() == KeyEvent.VK_TAB) {
                if (getText().length() == 0) {
                    getFocusCycleRootAncestor()
                            .getFocusTraversalPolicy()
                            .getComponentAfter(getFocusCycleRootAncestor(), this)
                            .requestFocusInWindow();
                    return;
                }
                WordpoolWord firstWord = WordpoolList.getFirstWord();
                if (firstWord == null) {
                    return;
                }
                String candidate = firstWord.getText();
                if (candidate != null) {
                    setText(candidate);
                }
            }
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                e.consume();
            }
            if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                if (WordpoolList.getInstance().getModel().getSize() > 0) {
                    WordpoolList.getInstance().requestFocusInWindow();
                }
            }
            // this assumes backspace will actually remove the previous element, but we'll make that
            // bet
            // alternative is WordpoolDocument.removeUpdate(), but that is called by programmatic
            // changes to textfield too
            if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                if (getText().length() <= 1) {
                    getParent().requestFocusInWindow();
                }
            }
        }
        // emacs key bindings
        if (e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK) {
            if (e.getKeyCode() == KeyEvent.VK_K) {
                emacsKillLine();
            } else if (e.getKeyCode() == KeyEvent.VK_Y) {
                emacsYank();
            }
        }
    }

    private void emacsYank() {
        setText(getText() + clipboard);
    }

    private void emacsKillLine() {
        int pos = getCaretPosition();
        clipboard = getText().substring(pos, getText().length());
        setText(getText().substring(0, pos));
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}

    protected static WordpoolTextField getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "WordpoolTextField not initialized via DI. Ensure GuiceBootstrap.create() was"
                            + " called first.");
        }
        return instance;
    }

    public static WordpoolTextField getFocusTraversalReference() {
        return getInstance();
    }

    @Override
    public void focusGained(FocusEvent e) {
        setSelectionStart(0);
        setSelectionEnd(0);
        setCaretPosition(getText().length());
    }

    @Override
    public void focusLost(FocusEvent e) {}
}
