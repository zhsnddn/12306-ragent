package com.ming.agent12306.common.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Repairs common UTF-8 mojibake caused by mis-decoding text as single-byte characters. */
public final class TextEncodingRepairUtil {

    private TextEncodingRepairUtil() {
    }

    public static String repairUtf8Mojibake(String value) {
        if (value == null || value.isBlank() || !looksLikeMojibake(value)) {
            return value;
        }
        byte[] bytes = toOriginalByteSequence(value);
        if (bytes == null) {
            return value;
        }
        String repaired = new String(bytes, StandardCharsets.UTF_8);
        return isBetterText(value, repaired) ? repaired : value;
    }

    private static boolean looksLikeMojibake(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\u00C3' || current == '\u00C2' || current == '\u00E5' || current == '\u00E4' || current == '\u00E9') {
                return true;
            }
            if (current <= 0x00FF && current >= 0x0080) {
                return true;
            }
            if (reverseWindows1252Byte(current) != null) {
                return true;
            }
        }
        return false;
    }

    private static byte[] toOriginalByteSequence(String value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current <= 0x00FF) {
                output.write((byte) current);
                continue;
            }
            Byte mappedByte = reverseWindows1252Byte(current);
            if (mappedByte == null) {
                return null;
            }
            output.write(mappedByte);
        }
        return output.toByteArray();
    }

    private static boolean isBetterText(String original, String repaired) {
        return repaired != null
                && !repaired.isBlank()
                && repaired.indexOf('\uFFFD') < 0
                && countCjkCharacters(repaired) > countCjkCharacters(original);
    }

    private static int countCjkCharacters(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current >= '\u4E00' && current <= '\u9FFF') {
                count++;
            }
        }
        return count;
    }

    private static Byte reverseWindows1252Byte(char value) {
        return switch (value) {
            case '\u20AC' -> (byte) 0x80;
            case '\u201A' -> (byte) 0x82;
            case '\u0192' -> (byte) 0x83;
            case '\u201E' -> (byte) 0x84;
            case '\u2026' -> (byte) 0x85;
            case '\u2020' -> (byte) 0x86;
            case '\u2021' -> (byte) 0x87;
            case '\u02C6' -> (byte) 0x88;
            case '\u2030' -> (byte) 0x89;
            case '\u0160' -> (byte) 0x8A;
            case '\u2039' -> (byte) 0x8B;
            case '\u0152' -> (byte) 0x8C;
            case '\u017D' -> (byte) 0x8E;
            case '\u2018' -> (byte) 0x91;
            case '\u2019' -> (byte) 0x92;
            case '\u201C' -> (byte) 0x93;
            case '\u201D' -> (byte) 0x94;
            case '\u2022' -> (byte) 0x95;
            case '\u2013' -> (byte) 0x96;
            case '\u2014' -> (byte) 0x97;
            case '\u02DC' -> (byte) 0x98;
            case '\u2122' -> (byte) 0x99;
            case '\u0161' -> (byte) 0x9A;
            case '\u203A' -> (byte) 0x9B;
            case '\u0153' -> (byte) 0x9C;
            case '\u017E' -> (byte) 0x9E;
            case '\u0178' -> (byte) 0x9F;
            default -> null;
        };
    }
}
