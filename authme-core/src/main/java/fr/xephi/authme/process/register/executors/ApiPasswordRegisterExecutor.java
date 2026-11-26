package fr.xephi.authme.process.register.executors;

import fr.xephi.authme.data.auth.PlayerAuth;

import static fr.xephi.authme.process.register.executors.PlayerAuthBuilderHelper.createPlayerAuth;

/**
 * Executor for password registration via API call.
 */
class ApiPasswordRegisterExecutor extends AbstractPasswordRegisterExecutor<ApiPasswordRegisterParams> {

    @Override
    protected PlayerAuth createPlayerAuthObject(ApiPasswordRegisterParams params) {
        return createPlayerAuth(params.getPlayer(), params.getHashedPassword(), null);
    }

    @Override
    protected boolean performLoginAfterRegister(ApiPasswordRegisterParams params) {
        return params.getLoginAfterRegister();
    }
}
