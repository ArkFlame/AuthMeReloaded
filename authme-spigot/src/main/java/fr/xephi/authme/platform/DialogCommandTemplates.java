package fr.xephi.authme.platform;

import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;

/** Shared command template for the submit button of registration dialogs. */
final class DialogCommandTemplates {

    private DialogCommandTemplates() {
    }

    static String registerTemplate(RegistrationType type, RegisterSecondaryArgument secondArg) {
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
}
