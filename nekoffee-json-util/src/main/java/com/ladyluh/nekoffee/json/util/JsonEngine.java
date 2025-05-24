package com.ladyluh.nekoffee.json.util;

// import com.nekoffee.api.exception.NekoffeeException; // Se quiser lançar exceções da API

import com.fasterxml.jackson.core.type.TypeReference;

public interface JsonEngine {
    /**
     * Serializa um objeto para uma string JSON.
     * @param object O objeto a ser serializado.
     * @return A representação JSON do objeto.
     * @throws RuntimeException // ou NekoffeeException se preferir
     */
    String toJsonString(Object object);

    /**
     * Desserializa uma string JSON para um tipo genérico complexo (ex: List<MyObject>).
     * @param jsonString A string JSON.
     * @param typeReference O TypeReference que descreve o tipo de destino.
     * @param <T> O tipo do objeto.
     * @return O objeto desserializado.
     */
    <T> T fromJsonString(String jsonString, TypeReference<T>  typeReference); // NOVO MÉTODO
    <T> T fromJsonString(String jsonString, Class<T> clazz);

}