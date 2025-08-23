package behaviors.singleact;

import components.ShortcutFrame;
import jakarta.inject.Inject;
import java.awt.event.ActionEvent;

public class EditShortcutsAction extends IdentifiedSingleAction {

    private final ShortcutFrame shortcutFrame;

    @Inject
    public EditShortcutsAction(ShortcutFrame shortcutFrame) {
        this.shortcutFrame = shortcutFrame;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        shortcutFrame.showShortcutEditor();
    }

    @Override
    public void update() {}
}
