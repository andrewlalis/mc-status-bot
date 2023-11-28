package com.andrewlalis.mc_status_bot;

import java.util.Set;

public record ServerStatus(
        int playersOnline,
        int maxPlayers,
        Set<String> playerNames
) {}
