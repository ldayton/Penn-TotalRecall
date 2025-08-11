package components.annotations;

import info.GUIConstants;
import info.MyShapes;
import info.SysInfo;

import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

import components.MyFrame;
import control.CurAudio;

/**
 * A custom interface component for displaying committed annotations to the user.
 * 
 */
public class AnnotationDisplay extends JScrollPane {
	
	private static final String title = "Annotations";
	
	private static AnnotationDisplay instance;
	private static AnnotationTable table;

	/**
	 * Creates a new instance of the component, initializing internal components, key bindings, listeners, 
	 * and various aspects of appearance.
	 */
	private AnnotationDisplay() {		
		table = AnnotationTable.getInstance();
		getViewport().setView(table);
		setPreferredSize(GUIConstants.annotationDisplayDimension);
		setMaximumSize(GUIConstants.annotationDisplayDimension);
		
		setBorder(MyShapes.createMyUnfocusedTitledBorder(title));
		
		//since AnnotationDisplay is a clickable area, we must write focus handling code for the event it is clicked on
		//passes focus to the table if it is focusable (not empty), otherwise giving focus to the frame
		addMouseListener(new MouseAdapter(){
			@Override
			public void mousePressed(MouseEvent e) {
				if(table.isFocusable()) {
					table.requestFocusInWindow();
				}
				else {
					MyFrame.getInstance().requestFocusInWindow();
				}
			}
		});
		
		//overrides JScrollPane key bindings for the benefit of SeekAction's key bindings
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "none");
		getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false), "none");
	}
	
	
	public static Annotation[] getAnnotationsInOrder() {
		return table.getModel().toArray();
	}
	
	public static void addAnnotation(Annotation ann) {
		if(ann == null) {
			throw new IllegalArgumentException("annotation/s cannot be null");
		}
		if(SysInfo.sys.forceListen) {
			CurAudio.getListener().offerGreatestProgress(CurAudio.getMaster().millisToFrames(ann.getTime()));
		}
		table.getModel().addElement(ann);
	}
	
	public static void addAnnotations(Iterable<Annotation> anns) {
		if(anns == null) {
			throw new IllegalArgumentException("annotations cannot be null");
		}
		if(SysInfo.sys.forceListen) {
			for(Annotation a: anns) {
				CurAudio.getListener().offerGreatestProgress(CurAudio.getMaster().millisToFrames(a.getTime()));
			}
		}
		table.getModel().addElements(anns);
	}
	
	public static void removeAnnotation(int rowIndex) {
		table.getModel().removeElementAt(rowIndex);
	}
	
	public static void removeAllAnnotations() {
		table.getModel().removeAllElements();
	}
	
	
	
	
	

	public static AnnotationDisplay getInstance() {
		if (instance == null) {
			instance = new AnnotationDisplay();
		}
		return instance;
	}


	public static int getNumAnnotations() {
		return table.getModel().size();
	}
}
