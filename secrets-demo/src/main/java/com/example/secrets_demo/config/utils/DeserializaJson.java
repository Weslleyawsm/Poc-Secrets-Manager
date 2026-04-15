package com.example.secrets_demo.config.utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class DeserializaJson {

    public static Map<String, String> getDadosAcesso(String secretJson) throws JsonProcessingException {
        Map<String, String> dados = new HashMap<>();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode secret = mapper.readTree(secretJson);

        dados.put("username", secret.get("username").asText());
        dados.put("password", secret.get("password").asText());
        dados.put("host", secret.get("host").asText());
        dados.put("port", secret.get("port").asText());
        dados.put("dbname", secret.get("dbname").asText());

        return dados;
    }
}
