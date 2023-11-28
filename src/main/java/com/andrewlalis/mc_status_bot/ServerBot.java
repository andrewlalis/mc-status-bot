package com.andrewlalis.mc_status_bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ServerBot implements Runnable {
    private final JDA jda;
    private final String serverIp;
    private final long channelId;
    private final ServerStatusFetcher serverStatusFetcher;

    private int lastPlayerCount = -1;
    private final Set<String> lastPlayerNames = new HashSet<>();

    public ServerBot(JDA jda, String serverIp, long channelId, ServerStatusFetcher serverStatusFetcher) {
        this.jda = jda;
        this.serverIp = serverIp;
        this.channelId = channelId;
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
        for (JsonNode node : serversArray) {
            String token = node.get("discord-token").asText();
            String serverIp = node.get("server-ip").asText();
            long channelId = node.get("channel-id").asLong();
            JDABuilder builder = JDABuilder.create(token, Collections.emptyList());
            builder.disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE);
            JDA jda = builder.build();
            bots.add(new ServerBot(jda, serverIp, channelId, serverStatusFetcher));
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
                ServerStatus status = serverStatusFetcher.fetch(serverIp);
                displayStatus(status);
            } catch (IOException e) {
                handleError(e);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
                Thread.currentThread().interrupt();
            }
        }
    }

    private void displayStatus(ServerStatus status) {
        String text = String.format("%d/%d players", status.playersOnline(), status.maxPlayers());
        OnlineStatus onlineStatus = jda.getPresence().getStatus();
        OnlineStatus newOnlineStatus = status.playersOnline() > 0 ? OnlineStatus.ONLINE : OnlineStatus.IDLE;
        Activity activity = jda.getPresence().getActivity();
        Activity newActivity = Activity.customStatus(text);

        boolean shouldUpdate = onlineStatus != newOnlineStatus ||
                activity == null ||
                !activity.getName().equals(newActivity.getName());
        if (shouldUpdate) {
            jda.getPresence().setPresence(newOnlineStatus, newActivity);
        }

        if (lastPlayerCount != -1 && lastPlayerCount != status.playersOnline()) {
            final TextChannel channel = jda.getTextChannelById(channelId);
            for (String name : getPlayersJoined(status.playerNames())) {
                channel.sendMessage(name + " joined the server.").queue();
            }
            for (String name : getPlayersLeft(status.playerNames())) {
                channel.sendMessage(name + " left the server.").queue();
            }
            lastPlayerCount = status.playersOnline();
            lastPlayerNames.clear();
            lastPlayerNames.addAll(status.playerNames());
        }
    }

    private Set<String> getPlayersJoined(Set<String> current) {
        Set<String> set = new HashSet<>(current);
        set.removeAll(lastPlayerNames);
        return set;
    }

    private Set<String> getPlayersLeft(Set<String> current) {
        Set<String> set = new HashSet<>(lastPlayerNames);
        set.removeAll(current);
        return set;
    }

    private void handleError(IOException e) {
        if (jda.getPresence().getStatus() != OnlineStatus.DO_NOT_DISTURB) {
            jda.getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
            jda.getPresence().setActivity(Activity.customStatus("Error."));
        }
        e.printStackTrace(System.err);
        lastPlayerCount = -1;
        lastPlayerNames.clear();
    }
}
