package com.andrewlalis.mc_status_bot.server_prot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ServerProtocol {
    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    public static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;
        while (true) {
            currentByte = (byte) in.read();
            value |= (currentByte & SEGMENT_BITS) << position;
            if ((currentByte & CONTINUE_BIT) == 0) break;
            position += 7;
            if (position >= 32) throw new IOException("VarInt is too big.");
        }
        return value;
    }

    public static void writeVarInt(OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                out.write(value);
                return;
            }
            out.write((value & SEGMENT_BITS) | CONTINUE_BIT);
            value >>>= 7;
        }
    }

    public static String readString(InputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] data = new byte[length];
        int bytesRead = in.read(data);
        if (bytesRead != length) throw new IOException("Couldn't read full string of length " + length);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static void writeString(OutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}
