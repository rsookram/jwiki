package io.github.rsookram.jwiki;

import java.io.IOException;
import java.io.InputStream;

public class FirstLevelIndex {

    private static final int KEY_LENGTH = 4;

    private char[] keyChars;
    private int[] offsets;

    // TODO: Binary search might be worth it
    public int getOffset(String s) {
        char[] chars = s.toCharArray();

        for (int i = 0; i < offsets.length; i++) {
            if (compareTo(i * KEY_LENGTH, chars) > 0) {
                return i == 0 ? -1 : offsets[i - 1];
            }
        }

        // s is after the last key
        return offsets[offsets.length - 1];
    }

    private int compareTo(int keyStart, char[] chars) {
        for (int i = 0; i < Math.min(chars.length, KEY_LENGTH); i++) {
            int cmp = keyChars[keyStart + i] - chars[i];
            if (cmp != 0) {
                return cmp;
            }
        }

        return KEY_LENGTH - chars.length;
    }

    public static FirstLevelIndex read(InputStream is, int numEntries, byte[] buf) throws IOException {
        FirstLevelIndex index = new FirstLevelIndex();
        index.keyChars = new char[numEntries * KEY_LENGTH];
        index.offsets = new int[numEntries];

        for (int i = 0; i < numEntries; i++) {
            is.readNBytes(buf, 0, KEY_LENGTH * Character.BYTES);

            int offsetIntoKeyChars = i * KEY_LENGTH;
            for (int j = 0; j < KEY_LENGTH; j++) {
                char ch = getChar(buf, j * Character.BYTES);
                index.keyChars[offsetIntoKeyChars + j] = ch;
            }
        }

        for (int i = 0; i < numEntries; i++) {
            is.readNBytes(buf, 0, Integer.BYTES);

            int offset = (buf[3] << 24) | ((buf[2] & 0xFF) << 16) | ((buf[1] & 0xFF) << 8) | (buf[0] & 0xFF);
            index.offsets[i] = offset;
        }

        return index;
    }

    private static char getChar(byte[] buf, int offset) {
        return (char) ((buf[offset + 1]) << 8 | (buf[offset] & 0xFF));
    }
}
