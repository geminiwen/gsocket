package xyz.geminiwen.gsocket;

import android.util.SparseArray;

import java.util.HashMap;
import java.util.Map;

import static java.lang.System.arraycopy;

/**
 * Created by geminiwen on 2016/12/3.
 */

public class Parser {

    private static final int MAX_INT_CHAR_LENGTH = String.valueOf(Integer.MAX_VALUE).length();

    public static final int PROTOCOL = 3;

    private static final Map<String, Integer> sPacketsType = new HashMap<String, Integer>() {{
        put(Packet.OPEN, 0);
        put(Packet.CLOSE, 1);
        put(Packet.PING, 2);
        put(Packet.PONG, 3);
        put(Packet.MESSAGE, 4);
        put(Packet.UPGRADE, 5);
        put(Packet.NOOP, 6);
    }};

    private static final SparseArray<String> sPacketsList = new SparseArray<>();
    static {
        for (Map.Entry<String, Integer> entry : sPacketsType.entrySet()) {
            sPacketsList.put(entry.getValue(), entry.getKey());
        }
    }

    private static Packet<String> sErrorPacket = new Packet<>(Packet.ERROR, "parser error");


    private Parser() {}

    public static String encodePacketString(Packet<String> packet) throws UTF8Exception {
        return encodePacketString(packet, false);
    }

    public static byte[] encodePacketBinary(Packet<byte[]> packet) throws UTF8Exception {
        byte[] buffer = new byte[1 + packet.data.length];
        buffer[0] = sPacketsType.get(packet.type).byteValue();
        arraycopy(buffer, 0, buffer, 1, buffer.length);
        return buffer;
    }

    public static String encodePacketString(Packet<String> packet, boolean utf8encode) throws UTF8Exception {
        String encoded = String.valueOf(sPacketsType.get(packet.type));

        if (null != packet.data) {
            encoded += utf8encode ? UTF8.encode(packet.data) : packet.data;
        }

        return encoded;
    }


    public static Packet<String> decodePacket(String data) {
        return decodePacket(data, false);
    }

    public static Packet<String> decodePacket(String data, boolean utf8decode) {
        int type;
        try {
            type = Character.getNumericValue(data.charAt(0));
        } catch (IndexOutOfBoundsException e) {
            type = -1;
        }

        if (utf8decode) {
            try {
                data = UTF8.decode(data);
            } catch (UTF8Exception e) {
                return sErrorPacket;
            }
        }

        if (type < 0 || type >= sPacketsType.size()) {
            return sErrorPacket;
        }

        if (data.length() > 1) {
            return new Packet<>(sPacketsList.get(type), data.substring(1));
        } else {
            return new Packet<>(sPacketsList.get(type));
        }
    }

    public static Packet<byte[]> decodePacket(byte[] source) {
        int type = source[0];
        byte[] buffer = new byte[source.length - 1];
        arraycopy(source, 1, buffer, 0, buffer.length);
        return new Packet<>(sPacketsList.get(type), buffer);
    }
}
