package com.andrewlalis.mc_status_bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ServerBot implements Runnable {
    private final JDA jda;
    private final String serverIp;
    private final short serverPort;
    private final ServerStatusFetcher serverStatusFetcher;

    public ServerBot(JDA jda, String serverIp, short serverPort, ServerStatusFetcher serverStatusFetcher) {
        this.jda = jda;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.serverStatusFetcher = serverStatusFetcher;
    }

    public String getServerIp() {
        return serverIp;
    }

    public static Collection<ServerBot> read(Path jsonConfigFile) throws Exception {
        if (Files.notExists(jsonConfigFile)) throw new IOException("File " + jsonConfigFile + " doesn't exist.");
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode configData;
        try (var in = Files.newInputStream(jsonConfigFile)) {
            configData = mapper.readValue(in, ObjectNode.class);
        }

        ArrayNode serversArray = configData.withArray("servers");
        List<ServerBot> bots = new ArrayList<>(serversArray.size());
        ServerStatusFetcher serverStatusFetcher = new ServerStatusFetcher();
        var virtualPool = Executors.newVirtualThreadPerTaskExecutor();
        for (JsonNode node : serversArray) {
            String token = node.get("discord-token").asText();
            String serverIp = node.get("server-ip").asText();
            short serverPort = node.get("server-port").shortValue();
            JDABuilder builder = JDABuilder.create(token, Collections.emptyList());
            builder.disableCache(
                    CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI,
                    CacheFlag.STICKER, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS,
                    CacheFlag.SCHEDULED_EVENTS
            );
            builder.setCallbackPool(virtualPool);
            builder.setEventPool(virtualPool);
            builder.setRateLimitElastic(virtualPool, true);
            JDA jda = builder.build();
            bots.add(new ServerBot(jda, serverIp, serverPort, serverStatusFetcher));
        }
        return bots;
    }

    @Override
    public void run() {
        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while awaiting ready status.", e);
        }
        while (true) {
            try {
                ServerStatus status = serverStatusFetcher.fetchViaSocket(serverIp, serverPort);
                displayStatus(status);
            } catch (IOException e) {
                handleError(e);
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
                Thread.currentThread().interrupt();
            }
        }
    }

    private void displayStatus(ServerStatus status) {
        OnlineStatus onlineStatus = jda.getPresence().getStatus();
        OnlineStatus newOnlineStatus = status.playersOnline() > 0 ? OnlineStatus.ONLINE : OnlineStatus.IDLE;
        Activity activity = jda.getPresence().getActivity();
        Activity newActivity = Activity.customStatus(formatStatusText(status));

        boolean shouldUpdate = onlineStatus != newOnlineStatus ||
                activity == null ||
                !activity.getName().equals(newActivity.getName());
        if (shouldUpdate) {
            jda.getPresence().setPresence(newOnlineStatus, newActivity);
        }
    }

    private String formatStatusText(ServerStatus status) {
        if (status.playerNames().isEmpty()) {
            return "0 players online.";
        }
        String playerNames = "\uD83C\uDFAE " + status.playerNames().stream().sorted().collect(Collectors.joining(", "));
        if (playerNames.length() > Activity.MAX_ACTIVITY_NAME_LENGTH) {
            return playerNames.substring(0, Activity.MAX_ACTIVITY_NAME_LENGTH - 3) + "...";
        }
        return playerNames;
    }

    private void handleError(IOException e) {
        if (jda.getPresence().getStatus() != OnlineStatus.DO_NOT_DISTURB) {
            jda.getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
            jda.getPresence().setActivity(Activity.customStatus("Error."));
        }
        e.printStackTrace(System.err);
    }
}
