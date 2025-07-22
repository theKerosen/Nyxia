package com.ladyluh.nekoffee;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.gateway.client.RestClient;
import com.ladyluh.nekoffee.gateway.client.impl.OkHttpRestClientImpl;
import com.ladyluh.nekoffee.json.util.JsonEngine;
import com.ladyluh.nekoffee.json.util.impl.JacksonJsonEngineImpl;
import okhttp3.OkHttpClient;

public final class Nekoffee {
    static OkHttpClient sharedOkHttpClient = new OkHttpClient.Builder().build();
    RestClient restClient = new OkHttpRestClientImpl();

    private Nekoffee() {

    }

    /**
     * Cria uma nova instância padrão do NekoffeeClient.
     *
     * @return uma nova instância de NekoffeeClient.
     */
    public static NekoffeeClient createDefault() {

        JsonEngine jsonEngine = new JacksonJsonEngineImpl();
        RestClient restClient = new OkHttpRestClientImpl();

        NekoffeeClientImpl clientImpl = new NekoffeeClientImpl(restClient, jsonEngine, sharedOkHttpClient);
        return new NekoffeeClientImpl(restClient, jsonEngine, sharedOkHttpClient);
    }
}