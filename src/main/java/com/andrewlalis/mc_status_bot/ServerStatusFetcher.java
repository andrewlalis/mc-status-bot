package com.andrewlalis.mc_status_bot;

import com.andrewlalis.mc_status_bot.server_prot.ServerProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.net.Socket;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ServerStatusFetcher {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ServerStatus fetchViaSocket(String ip, short port) throws IOException {
//        long start = System.currentTimeMillis();
        try (var socket = new Socket(ip, port)) {
            OutputStream sOut = socket.getOutputStream();
            InputStream sIn = socket.getInputStream();

            // Send the handshake request.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ServerProtocol.writeVarInt(out, 0x00); // Handshake packet id.
            ServerProtocol.writeVarInt(out, 764); // Protocol version for 1.20.2.
            ServerProtocol.writeString(out, ip);
            new DataOutputStream(out).writeShort(25565);
            ServerProtocol.writeVarInt(out, 1); // Next-state enum: 1 for Status.

            ServerProtocol.writeVarInt(sOut, out.size());
            sOut.write(out.toByteArray());
            sOut.flush();

            // Immediately send status request.
            out.reset();
            ServerProtocol.writeVarInt(out, 0x00);
            ServerProtocol.writeVarInt(sOut, out.size());
            sOut.write(out.toByteArray());
            sOut.flush();

            // Receive the status response.
            int responsePacketSize = ServerProtocol.readVarInt(sIn);
            byte[] packetIdAndData = new byte[responsePacketSize];
            int bytesRead = 0;
            int attempts = 0;
            while (bytesRead < responsePacketSize) {
                bytesRead += sIn.read(packetIdAndData, bytesRead, packetIdAndData.length - bytesRead);
                attempts++;
                if (attempts > 100) break;
            }
            if (bytesRead != responsePacketSize) throw new IOException("Couldn't read full packet. Read " + bytesRead + " instead of " + responsePacketSize);
            ByteArrayInputStream in = new ByteArrayInputStream(packetIdAndData);
            int packetId = ServerProtocol.readVarInt(in);
            if (packetId != 0x00) throw new IOException("Received invalid packetId when receiving status response: " + packetId);
            String jsonData = ServerProtocol.readString(in);
//            long dur = System.currentTimeMillis() - start;
//            System.out.println("Received server status in " + dur + " ms.");
            ObjectNode obj = objectMapper.readValue(jsonData, ObjectNode.class);

            return new ServerStatus(
                    obj.get("players").get("online").asInt(),
                    obj.get("players").get("max").asInt(),
                    StreamSupport.stream(obj.get("players").withArray("sample").spliterator(), false)
                            .map(node -> node.get("name").asText())
                            .collect(Collectors.toSet())
            );
        }
    }
}
