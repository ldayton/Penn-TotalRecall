package behaviors.multiact;

import audio.AudioPlayer;
import control.CurAudio;
import di.GuiceBootstrap;
import env.PreferencesManager;
import info.PreferenceKeys;
import java.awt.event.ActionEvent;
import java.util.Map;
import util.WindowService;

/**
 * Sets the audio position forward/backward one of several pre-defined amounts, in response to user
 * request.
 *
 * <p>Afterward sends update to all <code>UpdatingActions</code>.
 */
public class SeekAction extends IdentifiedMultiAction {

    /** Defines the seek direction and magnitude of a <code>SeekAction</code> instance. */
    public enum SeekAmount {
        FORWARD_SMALL,
        BACKWARD_SMALL,
        FORWARD_MEDIUM,
        BACKWARD_MEDIUM,
        FORWARD_LARGE,
        BACKWARD_LARGE
    }

    private int shift;

    private static Map<SeekAmount, Integer> timeMap;

    private final SeekAmount amount;

    /**
     * Create an action with the direction and amount presets given by the provided <code>Enum
     * </code>.
     *
     * <p>Since the waveform display autonomously decides when to paint itself, this action may not
     * result in an instant visual change.
     *
     * @param amount An <code>Enum</code> defined in this class which the class maps to the correct
     *     direction and magnitude of the seek.
     * @see behaviors.multiact.IdentifiedMultiAction#IdentifiedMultiAction(Enum)
     */
    public SeekAction(SeekAmount amount) {
        super(amount);
        this.amount = amount;
        updateSeekAmount();
    }

    /**
     * Performs the <code>SeekAction</code>, intelligently boundaries to make sure the player isn't
     * taken outside of the audio data.
     *
     * <p>Afterward sends an update to all <code>UpdatingActions</code>.
     *
     * @param e The <code>ActionEvent</code> provided by the trigger
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        long curFrame = CurAudio.getAudioProgress();
        long frameShift = CurAudio.getMaster().millisToFrames(shift);
        long naivePosition = curFrame + frameShift;
        long frameLength = CurAudio.getMaster().durationInFrames();

        long finalPosition = naivePosition;

        if (naivePosition < 0) {
            finalPosition = 0;
        } else if (naivePosition >= frameLength) {
            finalPosition = frameLength - 1;
        }

        CurAudio.setAudioProgressAndUpdateActions(finalPosition);
        CurAudio.getPlayer().playAt(finalPosition);
        WindowService windowService = GuiceBootstrap.getInjectedInstance(WindowService.class);
        if (windowService == null) {
            throw new IllegalStateException("WindowService not available via DI");
        }
        windowService.requestFocus();
    }

    /**
     * A forward (backward) <code>SeekAction</code> should be enabled only when audio is open, not
     * playing, and not at the end (beginning) of the audio.
     */
    @Override
    public void update() {
        if (CurAudio.audioOpen()) {
            if (CurAudio.getPlayer().getStatus() == AudioPlayer.Status.PLAYING) {
                setEnabled(false);
            } else {
                boolean canSkipForward = true;
                if (CurAudio.getAudioProgress() <= 0) {
                    if (canSkipForward
                            && (amount == SeekAmount.FORWARD_SMALL
                                    || amount == SeekAmount.FORWARD_MEDIUM
                                    || amount == SeekAmount.FORWARD_LARGE)) {
                        setEnabled(true);
                    } else {
                        setEnabled(false);
                    }
                } else if (CurAudio.getAudioProgress()
                        == CurAudio.getMaster().durationInFrames() - 1) {
                    if (amount == SeekAmount.FORWARD_SMALL
                            || amount == SeekAmount.FORWARD_MEDIUM
                            || amount == SeekAmount.FORWARD_LARGE) {
                        setEnabled(false);
                    } else {
                        setEnabled(true);
                    }
                } else {
                    if (amount == SeekAmount.FORWARD_SMALL
                            || amount == SeekAmount.FORWARD_MEDIUM
                            || amount == SeekAmount.FORWARD_LARGE) {
                        setEnabled(canSkipForward);
                    } else {
                        setEnabled(true);
                    }
                }
            }
        } else {
            setEnabled(false);
        }
    }

    /**
     * Queries the map associating the behavior-defining enum of this class and its associated
     * (positive or negative) integer seek amount.
     *
     * <p>However, unlike direct access to the map, this method returns 0 instead of <code>null
     * </code> for unknown keys.
     *
     * @param sa The <code>Enum</code> defining this SeekAction's behavior
     * @return The integer shift corresponding to <code>sa</code>, or 0 if <code>sa</code> is not in
     *     the map.
     */
    public int lookup(SeekAmount sa) {
        Integer shift = timeMap.get(sa);
        if (shift == null) {
            return 0;
        } else {
            return shift;
        }
    }

    /**
     * Getter for the <code>Enum</code> defining this <code>Action</code>'s behavior, can be
     * converted into an integer seek amount using {@link #lookup(SeekAmount)}.
     *
     * @return The <code>Enum</code> defining this <code>Action</code>'s behavior.
     */
    public SeekAction.SeekAmount getAmount() {
        return amount;
    }

    public void updateSeekAmount() {
        var preferencesManager =
                GuiceBootstrap.getRequiredInjectedInstance(
                        PreferencesManager.class, "PreferencesManager");

        switch (amount) {
            case FORWARD_SMALL:
                shift =
                        preferencesManager.getInt(
                                PreferenceKeys.SMALL_SHIFT, PreferenceKeys.DEFAULT_SMALL_SHIFT);
                break;
            case BACKWARD_SMALL:
                shift =
                        preferencesManager.getInt(
                                        PreferenceKeys.SMALL_SHIFT,
                                        PreferenceKeys.DEFAULT_SMALL_SHIFT)
                                * -1;
                break;
            case FORWARD_MEDIUM:
                shift =
                        preferencesManager.getInt(
                                PreferenceKeys.MEDIUM_SHIFT, PreferenceKeys.DEFAULT_MEDIUM_SHIFT);
                break;
            case BACKWARD_MEDIUM:
                shift =
                        preferencesManager.getInt(
                                        PreferenceKeys.MEDIUM_SHIFT,
                                        PreferenceKeys.DEFAULT_MEDIUM_SHIFT)
                                * -1;
                break;
            case FORWARD_LARGE:
                shift =
                        preferencesManager.getInt(
                                PreferenceKeys.LARGE_SHIFT, PreferenceKeys.DEFAULT_LARGE_SHIFT);
                break;
            case BACKWARD_LARGE:
                shift =
                        preferencesManager.getInt(
                                        PreferenceKeys.LARGE_SHIFT,
                                        PreferenceKeys.DEFAULT_LARGE_SHIFT)
                                * -1;
                break;
        }
    }
}
