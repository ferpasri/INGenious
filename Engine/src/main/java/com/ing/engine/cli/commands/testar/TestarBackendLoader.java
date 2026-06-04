package com.ing.engine.cli.commands.testar;

import com.ing.ingenious.api.contract.testar.TestarBackendApi;

import java.util.ServiceLoader;

public final class TestarBackendLoader {

    private TestarBackendLoader() {
    }

    public static TestarBackendApi load() {
        ServiceLoader<TestarBackendApi> loader = ServiceLoader.load(TestarBackendApi.class);
        for (TestarBackendApi candidate : loader) {
            return candidate;
        }
        return null;
    }
}
