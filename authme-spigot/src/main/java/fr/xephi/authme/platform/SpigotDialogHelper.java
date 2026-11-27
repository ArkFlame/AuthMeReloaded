package fr.xephi.authme.platform;

import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.md_5.bungee.api.dialog.Dialog;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.dialog.DialogBase;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import net.md_5.bungee.api.dialog.action.ActionButton;
import net.md_5.bungee.api.dialog.action.RunCommandAction;
import net.md_5.bungee.api.dialog.body.DialogBody;
import net.md_5.bungee.api.dialog.body.PlainMessageBody;
import net.md_5.bungee.api.dialog.input.DialogInput;
import net.md_5.bungee.api.dialog.input.TextInput;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the BungeeCord dialog API calls for Spigot 1.21.6+.
 *
 * <p>This class is intentionally isolated so that it is only loaded by the JVM when dialog
 * functionality is actually invoked. On servers that do not ship the BungeeCord dialog API
 * (Spigot &lt; 1.21.6), this class is never loaded, preventing {@link NoClassDefFoundError}.</p>
 */
final class SpigotDialogHelper {

    /**
     * Paper's Player API replaces Spigot's showDialog/clearDialog overloads, and this module compiles against
     * paper-api, so the Spigot-only methods are resolved once by reflection. They are only ever called on Spigot.
     */
    private static final ClassValue<Method> SHOW_DIALOG = methodLookup("showDialog", Dialog.class);
    private static final ClassValue<Method> CLEAR_DIALOG = methodLookup("clearDialog");

    private static ClassValue<Method> methodLookup(String name, Class<?>... parameters) {
        return new ClassValue<Method>() {
            @Override
            protected Method computeValue(Class<?> type) {
                try {
                    return type.getMethod(name, parameters);
                } catch (NoSuchMethodException e) {
                    return null;
                }
            }
        };
    }

    private static void show(Player player, Dialog dialog) {
        invoke(SHOW_DIALOG, player, dialog);
    }

    private static void invoke(ClassValue<Method> lookup, Player player, Object... arguments) {
        Method method = lookup.get(player.getClass());
        if (method == null) {
            throw new UnsupportedOperationException("This server has no Spigot dialog API");
        }
        try {
            method.invoke(player, arguments);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private SpigotDialogHelper() {
    }

    static void showLoginDialog(Player player, DialogWindowSpec dialog) {
        List<ActionButton> buttons = new ArrayList<>(2);
        buttons.add(new ActionButton(toTextComponent(dialog.primaryButtonLabel()),
            new RunCommandAction("login $(password)")));
        if (dialog.showSecondaryButton() && dialog.secondaryButtonCommand() != null) {
            buttons.add(new ActionButton(toTextComponent(dialog.secondaryButtonLabel()),
                new RunCommandAction(dialog.secondaryButtonCommand())));
        }
        show(player, new MultiActionDialog(newDialogBase(dialog), buttons.toArray(new ActionButton[0]))
            .columns(dialog.buttonColumns()));
    }

    static void showTotpDialog(Player player, DialogWindowSpec dialog) {
        show(player, newSingleActionDialog(dialog, "2fa code $(code)"));
    }

    static void showRecoveryCodeDialog(Player player, DialogWindowSpec dialog) {
        show(player, newSingleActionDialog(dialog, "email code $(code)").columns(dialog.buttonColumns()));
    }

    static void showNewPasswordDialog(Player player, DialogWindowSpec dialog) {
        show(player, newSingleActionDialog(dialog, "email setpassword $(password)").columns(dialog.buttonColumns()));
    }

    static void showRegisterDialog(Player player, RegistrationType type, RegisterSecondaryArgument secondArg,
                                   DialogWindowSpec dialog) {
        show(player, newSingleActionDialog(dialog, DialogCommandTemplates.registerTemplate(type, secondArg)));
    }

    /** Builds the common single-button dialog used by every dialog except the multi-button login dialog. */
    private static MultiActionDialog newSingleActionDialog(DialogWindowSpec dialog, String commandTemplate) {
        return new MultiActionDialog(newDialogBase(dialog),
            new ActionButton(toTextComponent(dialog.primaryButtonLabel()), new RunCommandAction(commandTemplate)));
    }

    static void closeDialog(Player player) {
        invoke(CLEAR_DIALOG, player);
    }

    /** Builds the dialog shell shared by every dialog type in this renderer. */
    private static DialogBase newDialogBase(DialogWindowSpec dialog) {
        return new DialogBase(toTextComponent(dialog.title()))
            .inputs(createInputs(dialog))
            .body(createBody(dialog))
            .afterAction(DialogBase.AfterAction.CLOSE);
    }

    private static List<DialogInput> createInputs(DialogWindowSpec dialog) {
        List<DialogInput> inputs = new ArrayList<>(dialog.inputs().size());
        for (DialogInputSpec input : dialog.inputs()) {
            inputs.add(new TextInput(input.id(), toTextComponent(input.label())).maxLength(input.maxLength()));
        }
        return inputs;
    }

    private static List<DialogBody> createBody(DialogWindowSpec dialog) {
        return dialog.body() == null
            ? List.of()
            : List.<DialogBody>of(new PlainMessageBody(toTextComponent(dialog.body())));
    }

    private static TextComponent toTextComponent(String text) {
        return new TextComponent(TextComponent.fromLegacyText(text));
    }
}
