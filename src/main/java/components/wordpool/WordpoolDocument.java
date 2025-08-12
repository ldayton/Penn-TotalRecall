package components.wordpool;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

/**
 * A <code>PlainDocument</code> model for the <code>WordpoolTextField</code>. Guarantees that text
 * entered into the field is capitalized, regardless of how the text is entered or how much.
 */
public class WordpoolDocument extends PlainDocument implements DocumentListener {

    protected WordpoolDocument() {
        addDocumentListener(this);
    }

    /**
     * Default implementation aside from converting the <code>String</code> to upper case.
     *
     * <p>{@inheritDoc}
     *
     * <p>author http://java.sun.com/javase/6/docs/api/javax/swing/JTextField.html
     */
    @Override
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
        if (str == null) {
            return;
        }
        char[] upper = str.toCharArray();
        for (int i = 0; i < upper.length; i++) {
            upper[i] = Character.toUpperCase(upper[i]);
        }
        super.insertString(offs, new String(upper), a);
    }

    /** Empty implementation. {@inheritDoc} */
    public void changedUpdate(DocumentEvent e) {}

    /**
     * Handles insertion of text into the <code>WordpoolTextField</code>. Simply requests that the
     * <code>WordpoolListModel</code> hide from graphical view
     *
     * @param e The <code>DocumentEvent</code> provided by the trigger.
     */
    public void insertUpdate(DocumentEvent e) {
        String nWord = WordpoolTextField.getInstance().getText();
        WordpoolList.getInstance().getModel().hideWordsStartingWith(nWord);
    }

    /**
     * Handles the removal of text from the <code>WordpoolTextField</code>. Simply requests that the
     * <code>WordpoolListModel</code> return to graphical view those words that now match the <code>
     * WordpoolTextField</code>'s text.
     *
     * @param e The <code>DocumentEvent</code> provided by the trigger.
     */
    public void removeUpdate(DocumentEvent e) {
        WordpoolList.getInstance()
                .getModel()
                .restoreWordsStartingWith(WordpoolTextField.getInstance().getText());
    }
}
