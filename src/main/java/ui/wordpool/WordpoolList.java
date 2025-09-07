package ui.wordpool;

import core.dispatch.EventDispatchBus;
import core.events.FocusRequestedEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

/** <code>JList</code> that stores available wordpool word for the annotating open audio file. */
@Singleton
public class WordpoolList extends JList<WordpoolWord>
        implements FocusListener, MouseListener, KeyListener {

    private static WordpoolListModel model;

    private static WordpoolList instance;

    final WordpoolListCellRenderer render;
    private final EventDispatchBus eventBus;

    @SuppressWarnings("StaticAssignmentInConstructor")
    @Inject
    public WordpoolList(EventDispatchBus eventBus) {
        this.eventBus = eventBus;
        model = new WordpoolListModel();
        setModel(model);

        // set the cell renderer that will display lst words differently from regular words
        render = new WordpoolListCellRenderer();
        setCellRenderer(render);

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setLayoutOrientation(JList.VERTICAL);

        // focus listener makes the the containing WordpoolDisplay look focused at the appropriate
        // times
        addFocusListener(this);

        // normally JLists take focus on their own when clicked. however in this case we have made
        // the WordpoolList not focusable when it's empty
        // so in that case we give focus to the WordpoolTextField when the WordpoolList is clicked
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (isFocusable()) {
                            // automatically takes focus in this case
                        } else {
                            eventBus.publish(
                                    new FocusRequestedEvent(
                                            FocusRequestedEvent.Component.WORDPOOL_TEXT_FIELD));
                        }
                    }
                });

        addKeyListener(this);

        // clicking on wordpool words
        addMouseListener(this);

        // hitting enter can be used to enter a wordpool word to the text field
        // this code is duplicated in the mouse listener where double click has the same effect
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "insert_word");
        getActionMap()
                .put(
                        "insert_word",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                var selectedValues = getSelectedValuesList();
                                Object[] objs = selectedValues.toArray();
                                if (objs.length
                                        == 1) { // in case multiple selection mode is used in the
                                    // future
                                    WordpoolWord selectedWord = (WordpoolWord) objs[0];
                                    WordpoolDisplay.switchToFocusAndClobber(selectedWord.getText());
                                }
                            }
                        });

        // overrides JScrollPane key bindings for the benefit of SeekAction's key bindings
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK, false),
                        "none");
        getInputMap(JComponent.WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK, false),
                        "none");

        // Set the singleton instance after full initialization
        instance = this;
    }

    /**
     * Type-refined implementation that guarantees a <code>WordpoolListModel</code> instead of a
     * <code>ListModel</code>.
     *
     * @return The <code>WordpoolListModel</code> associated with the <code>WordpoolList</code>
     */
    @Override
    public WordpoolListModel getModel() {
        return model;
    }

    /**
     * Gets a reference to this object for use by a custom <code>FocusTraversalPolicy</code>.
     *
     * <p>Unfortunately this requires a break from the encapsulation strategy of <code>
     * WordpoolDisplay</code> containing all the <code>public</code> access. Please do NOT abuse
     * this method to access the <code>WordpoolDisplay</code> for purposes other than those
     * intended. Add new public features to <code>WordpoolDisplay</code> which can then use
     * {@linkplain #getInstance()} as needed.
     *
     * @return {@link #getInstance()}
     */
    public static WordpoolList getFocusTraversalReference() {
        return getInstance();
    }

    /**
     * Custom focusability condition that behaves in the default manner aside from rejecting focus
     * when this <code>WordpoolList</code> has no elements.
     *
     * @return Whether or nut this component should accept focus
     */
    @Override
    public boolean isFocusable() {
        return (super.isFocusable() && model.getSize() > 0);
    }

    /** Handler for the event that this <code>WordpoolList</code> gains focus. */
    @Override
    public void focusGained(FocusEvent e) {
        int anchor = getAnchorSelectionIndex();
        if (anchor >= 0) {
            setSelectedIndex(anchor);
        } else {
            setSelectedIndex(0);
        }
    }

    /**
     * Handler for the event this <code>AudioFileList</code> loses focus.
     *
     * <p>Asks the containing <code>AudioFileDisplay</code> to stop looking focused.
     */
    @Override
    public void focusLost(FocusEvent e) {
        clearSelection();
    }

    /**
     * Gets the first word from the model.
     *
     * @return The first WordpoolWord, or null if the model is empty
     */
    protected static WordpoolWord getFirstWord() {
        if (model.getSize() > 0) {
            return model.getElementAt(0);
        } else {
            return null;
        }
    }

    /**
     * Singleton accessor.
     *
     * <p>Many classes in this package require access to this object, so a singleton accessor
     * strategy is used to avoid the need to pass every class a reference to this object.
     *
     * @return The singleton <code>WordpoolList</code>
     */
    protected static WordpoolList getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "WordpoolList not initialized via DI. Ensure GuiceBootstrap.create() was called"
                            + " first.");
        }
        return instance;
    }

    /**
     * On double click adds enters the clicked-on word to the text field.
     *
     * @param e The MouseEvent provided by the trigger
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            int index = locationToIndex(e.getPoint());
            if (index >= 0) {
                WordpoolWord clickedWord = model.getElementAt(index);
                WordpoolDisplay.switchToFocusAndClobber(clickedWord.getText());
            }
        }
    }

    /** Empty implementation. */
    @Override
    public void mouseEntered(MouseEvent e) {}

    /** Empty implementation. */
    @Override
    public void mouseExited(MouseEvent e) {}

    /** Empty implementation. */
    @Override
    public void mousePressed(MouseEvent e) {}

    /** Empty implementation. */
    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_UP) {
            if (getSelectedIndex() == 0) {
                eventBus.publish(
                        new FocusRequestedEvent(FocusRequestedEvent.Component.WORDPOOL_TEXT_FIELD));
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}
}
