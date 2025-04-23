package io.github.rsookram.jwiki;

import java.util.Arrays;

public class SearchResult {

    public final char[] key;
    public final long offset;

    public SearchResult(char[] key, long offset) {
        this.key = key;
        this.offset = offset;
    }

    public boolean startsWith(byte[] prefixBytes) {
        if (prefixBytes.length / 2 > key.length) {
            return false;
        }

        for (int i = 0; i < prefixBytes.length; i += 2) {
            char prefixChar = (char) ((prefixBytes[i + 1] & 0xFF) << 8 | (prefixBytes[i] & 0xFF));
            if (prefixChar != key[i / 2]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "key=" + Arrays.toString(key) +
                ", offset=" + offset +
                '}';
    }
}
