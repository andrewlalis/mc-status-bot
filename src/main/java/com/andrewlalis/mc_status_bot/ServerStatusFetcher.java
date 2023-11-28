package com.andrewlalis.mc_status_bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public class ServerStatusFetcher {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ServerStatus fetch(String ip) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mcsrvstat.us/3/" + ip))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IOException("Non-200 status code: " + response.statusCode());
            ObjectNode data = objectMapper.readValue(response.body(), ObjectNode.class);
            Set<String> playerNames = new HashSet<>();
            ArrayNode playersArray = data.get("players").withArray("list");
            for (JsonNode node : playersArray) {
                playerNames.add(node.get("name").asText());
            }
            return new ServerStatus(
                    data.get("players").get("online").asInt(),
                    data.get("players").get("max").asInt(),
                    playerNames
            );
        } catch (IOException | InterruptedException e) {
            throw new IOException("Failed to get server status.", e);
        }
    }
}
