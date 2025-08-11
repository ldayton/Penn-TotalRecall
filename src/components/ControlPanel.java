package components;

import info.MyColors;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import components.annotations.AnnotationDisplay;
import components.audiofiles.AudioFileDisplay;
import components.wordpool.WordpoolDisplay;

/**
 * Custom <code>JPanel</code> that is used as the bottom half of <code>MySplitPane</code>, containing control components such as wordpool display, file list, etc.
 *
 */
public class ControlPanel extends JPanel {

	private static ControlPanel instance;

	/**
	 * Creates a new instance, initializing listeners and appearance.
	 */
	private ControlPanel() {
		setOpaque(false);	

		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

		add(Box.createHorizontalGlue());
//		add(Box.createRigidArea(new Dimension(30, 0)));
//		add(VolumeSliderDisplay.getInstance());
		add(Box.createRigidArea(new Dimension(30, 0)));
		add(AudioFileDisplay.getInstance());
		add(Box.createRigidArea(new Dimension(30, 0)));
		add(WordpoolDisplay.getInstance());
		add(Box.createRigidArea(new Dimension(30, 0)));
		add(AnnotationDisplay.getInstance());
		add(Box.createRigidArea(new Dimension(30, 0)));
		add(DoneButton.getInstance());
		add(Box.createRigidArea(new Dimension(30, 0)));
		add(Box.createHorizontalGlue());
		
		//since ControlPanel is a clickable area, we must write focus handling code for the event it is clicked on
		//passes focus to frame	
		addMouseListener(new MouseAdapter(){
			@Override
			public void mousePressed(MouseEvent e) {
				MyFrame.getInstance().requestFocusInWindow();
			}
		});
		
		setBorder(BorderFactory.createEmptyBorder(10, 3, 3, 3));
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.setColor(MyColors.unfocusedColor);
		g.drawLine(0, 0, getWidth() - 1, 0);
	}

	/**
	 * Singleton accessor.
	 * 
	 * @return The singleton <code>ControlPanel</code>
	 */
	public static ControlPanel getInstance() {
		if (instance == null) {
			instance = new ControlPanel();
		}
		return instance;
	}
}
