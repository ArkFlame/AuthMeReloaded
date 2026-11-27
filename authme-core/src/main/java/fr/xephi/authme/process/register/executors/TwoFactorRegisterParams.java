package fr.xephi.authme.process.register.executors;

import org.bukkit.entity.Player;

/**
 * Parameters for registration with two-factor authentication.
 */
public class TwoFactorRegisterParams extends AbstractPasswordRegisterParams {

    protected TwoFactorRegisterParams(Player player) {
        super(player, null);
    }

    /**
     * Creates a parameters object.
     */
    public static TwoFactorRegisterParams of(Player player) {
        return new TwoFactorRegisterParams(player);
    }
}
