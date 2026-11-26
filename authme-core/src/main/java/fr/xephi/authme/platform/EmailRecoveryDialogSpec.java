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
        return createDialog(text, "code", RECOVERY_CODE_MAX_INPUT_LENGTH,
            MessageKey.DIALOG_RECOVERY_CODE_TITLE, MessageKey.DIALOG_RECOVERY_CODE_INPUT,
            MessageKey.DIALOG_RECOVERY_CODE_BUTTON, MessageKey.DIALOG_RECOVERY_CODE_BODY,
            showBody, stackButtonsVertically);
    }

    /** Dialog asking the player for the new password after a valid recovery code. */
    public static DialogWindowSpec createNewPasswordDialog(Function<MessageKey, String> text,
                                                           boolean showBody, boolean stackButtonsVertically) {
        return createDialog(text, "password", DEFAULT_MAX_INPUT_LENGTH,
            MessageKey.DIALOG_NEW_PASSWORD_TITLE, MessageKey.DIALOG_NEW_PASSWORD_INPUT,
            MessageKey.DIALOG_NEW_PASSWORD_BUTTON, MessageKey.DIALOG_NEW_PASSWORD_BODY,
            showBody, stackButtonsVertically);
    }

    /** Shared single-input construction; message keys stay with the callers. */
    private static DialogWindowSpec createDialog(Function<MessageKey, String> text, String inputId, int maxLength,
                                                 MessageKey title, MessageKey inputLabel, MessageKey button,
                                                 MessageKey body, boolean showBody, boolean stackButtonsVertically) {
        return new DialogWindowSpec(
            text.apply(title),
            Collections.singletonList(new DialogInputSpec(inputId, text.apply(inputLabel), maxLength)),
            text.apply(button),
            text.apply(MessageKey.DIALOG_CANCEL_BUTTON),
            false, false, null,
            showBody ? text.apply(body) : null,
            stackButtonsVertically);
    }
}
