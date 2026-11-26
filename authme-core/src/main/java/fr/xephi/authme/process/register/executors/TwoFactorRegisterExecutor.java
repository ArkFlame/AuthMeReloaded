package fr.xephi.authme.process.register.executors;

import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.security.crypts.TwoFactor;
import fr.xephi.authme.service.CommonService;
import org.bukkit.Bukkit;

import javax.inject.Inject;

/**
 * Executor for two-factor registration.
 */
class TwoFactorRegisterExecutor extends AbstractPasswordRegisterExecutor<TwoFactorRegisterParams> {

    @Inject
    private CommonService commonService;

    @Override
    public boolean isRegistrationAdmitted(TwoFactorRegisterParams params) {
        // nothing to check
        return true;
    }

    @Override
    public void executePostPersistAction(TwoFactorRegisterParams params) {
        super.executePostPersistAction(params);

        // Only invoked when the hash method is two-factor, so the hashed password is the TwoFactor result
        // (which contains the TOTP secret).
        String hash = params.getHashedPassword().getHash();
        String qrCodeUrl = TwoFactor.getQrBarcodeUrl(params.getPlayerName(), Bukkit.getIp(), hash);
        commonService.send(params.getPlayer(), MessageKey.TWO_FACTOR_CREATE, hash, qrCodeUrl);
    }
}
