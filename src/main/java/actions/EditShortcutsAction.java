package actions;

import components.ShortcutFrame;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.awt.event.ActionEvent;

@Singleton
public class EditShortcutsAction extends BaseAction {

    private final ShortcutFrame shortcutFrame;

    @Inject
    public EditShortcutsAction(ShortcutFrame shortcutFrame) {
        super("Edit Shortcuts", "Open shortcut editor");
        this.shortcutFrame = shortcutFrame;
    }

    @Override
    protected void performAction(ActionEvent e) {
        shortcutFrame.showShortcutEditor();
    }

    @Override
    public void update() {
        // Always enabled
    }
}
