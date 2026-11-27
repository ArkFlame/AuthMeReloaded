package fr.xephi.authme.process.register.executors;

import fr.xephi.authme.security.crypts.HashedPassword;
import org.bukkit.entity.Player;

/**
 * Common params type for implementors of {@link AbstractPasswordRegisterExecutor}.
 * Password must be supplied on creation and cannot be changed later on. The {@link HashedPassword}
 * is stored on the params object for later use.
 */
public abstract class AbstractPasswordRegisterParams extends RegistrationParameters {

    private final String password;
    private HashedPassword hashedPassword;

    public AbstractPasswordRegisterParams(Player player, String password) {
        super(player);
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    void setHashedPassword(HashedPassword hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    HashedPassword getHashedPassword() {
        return hashedPassword;
    }
}
