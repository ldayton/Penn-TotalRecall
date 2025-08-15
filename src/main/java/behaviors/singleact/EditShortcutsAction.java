package behaviors.singleact;

import components.ShortcutFrame;
import java.awt.event.ActionEvent;

public class EditShortcutsAction extends IdentifiedSingleAction {

    @Override
    public void actionPerformed(ActionEvent e) {
        ShortcutFrame.showShortcutEditor();
    }

    @Override
    public void update() {}
}
