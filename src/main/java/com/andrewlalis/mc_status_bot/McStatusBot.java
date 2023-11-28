package com.andrewlalis.mc_status_bot;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class McStatusBot {
    public static void main(String[] args) throws Exception {
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        for (ServerBot bot : ServerBot.read(Path.of("config.json"))) {
            System.out.println("Starting server status bot for " + bot.getServerIp());
            executor.execute(bot);
        }
    }
}