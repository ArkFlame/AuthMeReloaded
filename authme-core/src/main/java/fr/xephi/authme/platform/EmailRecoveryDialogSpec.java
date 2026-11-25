package fr.xephi.authme.platform;

import fr.xephi.authme.message.MessageKey;

import java.util.Collections;
import java.util.function.Function;

/**
 * Builds the follow-up dialog specs for the e-mail recovery flow. The dialogs submit the existing
 * recovery commands, so validation, expiry and rate limits stay with the regular recovery services.
 */
public final class EmailRecoveryDialogSpec {

    private static final int DEFAULT_MAX_INPUT_LENGTH = 100;
    private static final int RECOVERY_CODE_MAX_INPUT_LENGTH = 16;

    private EmailRecoveryDialogSpec() {
    }

    /** Dialog asking the player for the recovery code sent by e-mail. */
    public static DialogWindowSpec createCodeDialog(Function<MessageKey, String> text,
                                                    boolean showBody, boolean stackButtonsVertically) {
        return new DialogWindowSpec(
            text.apply(MessageKey.DIALOG_RECOVERY_CODE_TITLE),
            Collections.singletonList(new DialogInputSpec("code",
                text.apply(MessageKey.DIALOG_RECOVERY_CODE_INPUT), RECOVERY_CODE_MAX_INPUT_LENGTH)),
            text.apply(MessageKey.DIALOG_RECOVERY_CODE_BUTTON),
            text.apply(MessageKey.DIALOG_CANCEL_BUTTON),
            false,
            false,
            null,
            showBody ? text.apply(MessageKey.DIALOG_RECOVERY_CODE_BODY) : null,
            stackButtonsVertically);
    }

    /** Dialog asking the player for the new password after a valid recovery code. */
    public static DialogWindowSpec createNewPasswordDialog(Function<MessageKey, String> text,
                                                           boolean showBody, boolean stackButtonsVertically) {
        return new DialogWindowSpec(
            text.apply(MessageKey.DIALOG_NEW_PASSWORD_TITLE),
            Collections.singletonList(new DialogInputSpec("password",
                text.apply(MessageKey.DIALOG_NEW_PASSWORD_INPUT), DEFAULT_MAX_INPUT_LENGTH)),
            text.apply(MessageKey.DIALOG_NEW_PASSWORD_BUTTON),
            text.apply(MessageKey.DIALOG_CANCEL_BUTTON),
            false,
            false,
            null,
            showBody ? text.apply(MessageKey.DIALOG_NEW_PASSWORD_BODY) : null,
            stackButtonsVertically);
    }
}
