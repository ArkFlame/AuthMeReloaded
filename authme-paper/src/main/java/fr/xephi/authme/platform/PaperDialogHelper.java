package fr.xephi.authme.platform;

import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Encapsulates the Paper-derived dialog API calls used by Paper and Folia.
 */
public final class PaperDialogHelper {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private PaperDialogHelper() {
    }

    static void closeDialog(Player player) {
        player.closeDialog();
    }

    static void showLoginDialog(Player player, DialogWindowSpec dialog) {
        showCommandDialog(player, dialog, "login $(password)");
    }

    static void showTotpDialog(Player player, DialogWindowSpec dialog) {
        showCommandDialog(player, dialog, "2fa code $(code)");
    }

    static void showRegisterDialog(Player player, RegistrationType type, RegisterSecondaryArgument secondArg,
                                   DialogWindowSpec dialog) {
        showCommandDialog(player, dialog, createRegisterTemplate(type, secondArg));
    }

    static void showRecoveryCodeDialog(Player player, DialogWindowSpec dialog) {
        showCommandDialog(player, dialog, "email code $(code)");
    }

    static void showNewPasswordDialog(Player player, DialogWindowSpec dialog) {
        showCommandDialog(player, dialog, "email setpassword $(password)");
    }

    private static void showCommandDialog(Player player, DialogWindowSpec dialog, String commandTemplate) {
        player.showDialog(createInGameCommandDialog(dialog, commandTemplate));
    }

    public static Dialog createPreJoinLoginDialog(DialogWindowSpec dialog) {
        // Recovery button if the spec includes a secondary command, otherwise a plain cancel button
        Key secondaryKey = dialog.secondaryButtonCommand() != null
            ? PaperDialogActionKeys.PRE_JOIN_LOGIN_RECOVERY
            : PaperDialogActionKeys.PRE_JOIN_LOGIN_CANCEL;
        return createPreJoinDialog(dialog, dialog.canCloseWithEscape(),
            PaperDialogActionKeys.PRE_JOIN_LOGIN_SUBMIT, secondaryKey);
    }

    public static Dialog createPreJoinRecoveryDialog(DialogWindowSpec dialog) {
        return createPreJoinDialog(dialog, false, PaperDialogActionKeys.PRE_JOIN_RECOVERY_SUBMIT,
            PaperDialogActionKeys.PRE_JOIN_RECOVERY_CANCEL);
    }

    public static Dialog createPreJoinRegisterDialog(DialogWindowSpec dialog) {
        return createPreJoinDialog(dialog, dialog.canCloseWithEscape(),
            PaperDialogActionKeys.PRE_JOIN_REGISTER_SUBMIT, PaperDialogActionKeys.PRE_JOIN_REGISTER_CANCEL);
    }

    private static Dialog createPreJoinDialog(DialogWindowSpec dialog, boolean canCloseWithEscape,
                                              Key submitKey, Key cancelKey) {
        DialogBase base = DialogBase.builder(legacyComponent(dialog.title()))
            .inputs(createInputs(dialog))
            .body(createBody(dialog))
            .canCloseWithEscape(canCloseWithEscape)
            .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
            .build();

        ActionButton submitButton = ActionButton.builder(legacyComponent(dialog.primaryButtonLabel()))
            .action(DialogAction.customClick(submitKey, null))
            .build();

        List<ActionButton> buttons = createButtons(dialog, submitButton, () -> ActionButton.builder(
            legacyComponent(dialog.secondaryButtonLabel()))
            .action(DialogAction.customClick(cancelKey, null))
            .build());
        return createButtonDialog(dialog, base, buttons);
    }

    private static Dialog createInGameCommandDialog(DialogWindowSpec dialog, String commandTemplate) {
        DialogBase base = DialogBase.builder(legacyComponent(dialog.title()))
            .inputs(createInputs(dialog))
            .body(createBody(dialog))
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .build();

        ActionButton primaryButton = ActionButton.builder(legacyComponent(dialog.primaryButtonLabel()))
            .action(DialogAction.commandTemplate(commandTemplate))
            .build();

        List<ActionButton> buttons = createButtons(dialog, primaryButton, () -> {
            String command = dialog.secondaryButtonCommand();
            return command == null ? null : ActionButton.builder(legacyComponent(dialog.secondaryButtonLabel()))
                .action(DialogAction.commandTemplate(command))
                .build();
        });

        return createButtonDialog(dialog, base, buttons);
    }

    private static Dialog createButtonDialog(DialogWindowSpec dialog, DialogBase base, List<ActionButton> buttons) {
        return Dialog.create(factory -> factory.empty()
            .base(base)
            .type(DialogType.multiAction(buttons).columns(dialog.buttonColumns()).build()));
    }

    /**
     * Assembles the buttons, creating the secondary one only when the spec asks for it. A supplier
     * returning {@code null} leaves just the primary button.
     */
    private static List<ActionButton> createButtons(DialogWindowSpec dialog, ActionButton primaryButton,
                                                    Supplier<ActionButton> secondaryButton) {
        List<ActionButton> buttons = new ArrayList<>(2);
        buttons.add(primaryButton);
        if (dialog.showSecondaryButton()) {
            ActionButton extra = secondaryButton.get();
            if (extra != null) {
                buttons.add(extra);
            }
        }
        return buttons;
    }

    private static List<DialogInput> createInputs(DialogWindowSpec dialog) {
        return dialog.inputs().stream()
            .map(input -> DialogInput.text(input.id(), legacyComponent(input.label()))
                .maxLength(input.maxLength())
                .build())
            .collect(Collectors.toList());
    }

    private static List<DialogBody> createBody(DialogWindowSpec dialog) {
        return dialog.body() == null
            ? List.of()
            : List.of(DialogBody.plainMessage(legacyComponent(dialog.body())));
    }

    private static String createRegisterTemplate(RegistrationType type, RegisterSecondaryArgument secondArg) {
        boolean email = type == RegistrationType.EMAIL;
        String template = "register " + (email ? "$(email)" : "$(password)");
        if (secondArg == RegisterSecondaryArgument.CONFIRMATION) {
            return template + " $(confirm)";
        }
        return !email && (secondArg == RegisterSecondaryArgument.EMAIL_MANDATORY
            || secondArg == RegisterSecondaryArgument.EMAIL_OPTIONAL)
            ? template + " $(email)"
            : template;
    }

    private static Component legacyComponent(String text) {
        return LEGACY_SERIALIZER.deserialize(text);
    }
}
