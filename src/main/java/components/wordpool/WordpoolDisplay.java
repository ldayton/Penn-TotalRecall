package components.wordpool;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A custom interface component for displaying wordpool and lst words to the user and a text field
 * in which to enter annotations.
 *
 * <p>The component supports tab auto-complete of words being entered in the text field, using words
 * from the wordpool list display. The user is forced to choose only words from the list, to do
 * otherwise requires marking the word as an intrusion with a special keystroke. The display is
 * self-sorting, with lst words above regular wordpool words, and alphabetical otherwise.
 *
 * <p>Note: Access to this component from outside the package is limited to the public static
 * methods provided in this class. Code outside the package cannot and should not try to access the
 * internal list, model, or other components directly.
 */
@Singleton
public class WordpoolDisplay extends JPanel {

    private static final String title = "Wordpool";
    private static final Dimension PREFERRED_SIZE = new Dimension(250, Integer.MAX_VALUE);

    private static JTextField field;
    private final WordpoolTextField wordpoolTextField;
    private final WordpoolList wordpoolList;

    private static WordpoolDisplay instance;

    private static WordpoolScrollPane pane;

    /**
     * Creates a new instance of the component, initializing internal components, listeners, and
     * various aspects of appearance.
     */
    @SuppressWarnings("StaticAssignmentInConstructor")
    @Inject
    public WordpoolDisplay(WordpoolTextField wordpoolTextField, WordpoolList wordpoolList) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.wordpoolTextField = wordpoolTextField;
        this.wordpoolList = wordpoolList;
        field = wordpoolTextField;
        pane = new WordpoolScrollPane(wordpoolList);

        add(field);
        add(pane);

        setPreferredSize(PREFERRED_SIZE);
        setMaximumSize(PREFERRED_SIZE);

        setBorder(BorderFactory.createTitledBorder(title));

        // since WordpoolDisplay is a clickable area, we must write focus handling code for the
        // event it is clicked on
        // this case is rare, since only a very small amount of this component is exposed (the area
        // around the border title),
        // the rest being obscured by the WordpoolList and WordpoolTextField
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        field.requestFocusInWindow();
                    }
                });

        // Set the singleton instance after full initialization
        instance = this;
    }

    /**
     * Public accessor to the <code>WordpoolTextField</code>'s text.
     *
     * @return wordpoolTextField.getText()
     */
    public String getFieldText() {
        return wordpoolTextField.getText();
    }

    /** Sets the <code>WordpoolTextField</code>'s text to the empty string. */
    public void clearText() {
        wordpoolTextField.setText("");
    }

    public void distinguishAsLst(List<WordpoolWord> lstWords) {
        wordpoolList.getModel().distinguishAsLst(lstWords);
    }

    public void undistinguishAllWords() {
        wordpoolList.getModel().undistinguishAllWords();
    }

    /**
     * Removes all <code>WordpoolWords</code> from the component, whether or not they are present
     * graphically or hidden (because of auto-complete filtering).
     */
    public void removeAllWords() {
        wordpoolList.getModel().removeAllWords();
    }

    /**
     * Adds the provided <code>WordpoolWords</code> to the component for display.
     *
     * @param words The list of WordpoolWords to add for display
     */
    public void addWordpoolWords(List<WordpoolWord> words) {
        clearText();
        wordpoolList.getModel().addElements(words);
    }

    /**
     * Finds the alphabetically first <code>WordpoolWord</code> that matches the provided <code>
     * String</code>.
     *
     * @param str The <code>String</code> to be matched, often the contents of the <code>
     *     WordpoolTextField</code>
     * @return The first alphabetical matching <code>WordpoolWord</code>, or <code>null</code> if
     *     there is no match
     */
    public WordpoolWord findMatchingWordpooWord(String str) {
        return wordpoolList.getModel().findMatchingWordpoolWord(str);
    }

    /**
     * Called by outside key listeners when the user types alphanumeric characters. The idea is to
     * pass focus to the text field and enter the string programmatically that the user had started
     * typing before the field had focus. This is a convenience feature so the user doesn't have to
     * manually give the field focus to enter something.
     *
     * <p>Does nothing if the <code>WordpoolTextField</code> already has focus.
     *
     * @param str The String the user typed, possibly before the <code>WordpoolTextField</code> had
     *     focus.
     */
    public static void switchToFocus(String str) {
        if (field.hasFocus() == false) { // don't double-add strings that will be added by the field
            // automatically!
            field.setText(field.getText() + str);
            field.requestFocusInWindow();
        }
    }

    public static void switchToFocusAndClobber(String str) {
        if (field.hasFocus() == false) { // don't double-add strings that will be added by the field
            // automatically!
            field.setText(str);
            field.requestFocusInWindow();
        }
    }

    /**
     * Singleton accessor
     *
     * @return The singleton <code>WordpoolDisplay</code>
     */
    public static WordpoolDisplay getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "WordpoolDisplay not initialized via DI. Ensure GuiceBootstrap.create() was"
                            + " called first.");
        }
        return instance;
    }

    public void setInputEnabled(boolean enabled) {
        wordpoolTextField.setEnabled(enabled);
    }
}
