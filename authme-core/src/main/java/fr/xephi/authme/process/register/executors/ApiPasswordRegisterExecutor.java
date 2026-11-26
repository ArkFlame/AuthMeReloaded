package fr.xephi.authme.process.register.executors;

/**
 * Executor for password registration via API call.
 */
class ApiPasswordRegisterExecutor extends AbstractPasswordRegisterExecutor<ApiPasswordRegisterParams> {

    @Override
    protected boolean performLoginAfterRegister(ApiPasswordRegisterParams params) {
        return params.getLoginAfterRegister();
    }
}
