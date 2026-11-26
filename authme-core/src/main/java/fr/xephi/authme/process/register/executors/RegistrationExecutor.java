package fr.xephi.authme.process.register.executors;

import fr.xephi.authme.data.auth.PlayerAuth;

/**
 * Performs the registration action.
 *
 * @param <P> the registration parameters type
 */
public interface RegistrationExecutor<P extends RegistrationParameters> {

    /**
     * Returns whether the registration may take place. Use this method to execute
     * checks specific to the registration method.
     * <p>
     * If this method returns {@code false}, it is expected that the executor inform
     * the player about the error within this method call.
     */
    boolean isRegistrationAdmitted(P params);

    /**
     * Constructs the PlayerAuth object to persist into the database.
     */
    PlayerAuth buildPlayerAuth(P params);

    /**
     * Follow-up method called after the player auth could be added into the database.
     */
    void executePostPersistAction(P params);

}
