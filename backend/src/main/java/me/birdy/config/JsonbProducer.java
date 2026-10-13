package me.birdy.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/** Shares one JSON-B instance with the code that serialises outside Jakarta REST (the chat endpoint). */
@ApplicationScoped
public class JsonbProducer {

    @Produces
    @ApplicationScoped
    public Jsonb jsonb() {
        return JsonbBuilder.create();
    }

    public void close(@Disposes Jsonb jsonb) throws Exception {
        jsonb.close();
    }
}
