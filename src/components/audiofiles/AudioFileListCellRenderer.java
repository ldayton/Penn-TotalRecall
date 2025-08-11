package components.audiofiles;

import java.awt.Component;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Map;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

import control.CurAudio;




/**
 * A <code>DefaultListCellRenderer</code> whose appearance is determined by whether the {@link components.audiofiles.AudioFile} it is displaying is done being annotated or not.
 * 
 * <code>AudioFiles</code> that are done are displayed using a disabled <code>JComponent</code> with the program's strike-through <code>Font</code>.
 * <code>AudioFiles</code> that are incomplete are displayed using an enabled <code>JComponent</code> with the program's bold <code>Font</code>.
 * 
 */
public class AudioFileListCellRenderer extends DefaultListCellRenderer {
	
	private final Font strikethrough;
	private final Font bold;
	private final Font plain;
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	public AudioFileListCellRenderer() {
		plain = getFont();
		Map attributes = plain.getAttributes();
		attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
		strikethrough = new Font(attributes);
		bold = plain.deriveFont(Font.BOLD);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index,
			boolean isSelected, boolean cellHasFocus) {
		super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
		if(((AudioFile)value).isDone()) {
			setEnabled(false);
			setFont(strikethrough);
		}
		else {
			if(CurAudio.audioOpen()) {
				if(((AudioFile)value).getAbsolutePath().equals(CurAudio.getCurrentAudioFileAbsolutePath())) {
					setFont(bold);
				}
				else {
					setFont(plain);
				}
			}
			else {
				setFont(plain);
			}
		}
		return this;
	}
}
