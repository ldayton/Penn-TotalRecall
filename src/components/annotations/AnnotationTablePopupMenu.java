package components.annotations;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import behaviors.singleact.DeleteAnnotationAction;


/**
 * Popup menu launched by right clicking on annotations. 
 * 
 */
public class AnnotationTablePopupMenu extends JPopupMenu {

	protected AnnotationTablePopupMenu(Annotation annToDelete, int rowIndex, AnnotationTable table, String rowRepr) {
		super();
		JMenuItem fakeTitle = new JMenuItem(rowRepr + "...");
		fakeTitle.setEnabled(false);
		JMenuItem del = new JMenuItem(
				new DeleteAnnotationAction(rowIndex));
		add(fakeTitle);
		addSeparator();
		add(del);
	}
}
