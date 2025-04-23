package io.github.rsookram.jwiki;

import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Wiki {

    private static final String NAME = "wikipedia.wiki";

    private static Wiki instance;

    private final FileInputStream file;
    private final FirstLevelIndex firstLevelIndex;
    private final long secondLevelIndexOffset;

    private final byte[] buffer = new byte[512];

    public static Wiki getInstance() {
        if (instance == null) {
            instance = new Wiki();
        }
        return instance;
    }

    private Wiki() {
        File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), NAME);
        try {
            this.file = new FileInputStream(f);

            long length = file.getChannel().size();

            int firstLevelIndexSizeLength = 2;
            file.getChannel().position(length - firstLevelIndexSizeLength);

            file.readNBytes(buffer, 0, firstLevelIndexSizeLength);

            int firstLevelIndexSize = u16leToInt(buffer);
            if (firstLevelIndexSize < 2 || firstLevelIndexSize >= 1 << 16) {
                throw new RuntimeException("invalid first level index size " + firstLevelIndexSize);
            }

            int firstLevelIndexRowSize = 12;
            int numFirstLevelIndexEntries = (firstLevelIndexSize - firstLevelIndexSizeLength) / firstLevelIndexRowSize;

            int secondLevelIndexSizeLength = Integer.BYTES;
            file.getChannel().position(length - firstLevelIndexSize - secondLevelIndexSizeLength);

            BufferedInputStream bis = new BufferedInputStream(file, 1024 * 16);

            bis.readNBytes(buffer, 0, secondLevelIndexSizeLength);

            int secondLevelIndexSize = u32leToInt(buffer);
            if (secondLevelIndexSize < 4 || secondLevelIndexSize > 1 << 30) {
                throw new RuntimeException("invalid second level index size " + secondLevelIndexSize);
            }

            this.firstLevelIndex = FirstLevelIndex.read(bis, numFirstLevelIndexEntries, buffer);
            this.secondLevelIndexOffset = length - firstLevelIndexSize - secondLevelIndexSize;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<SearchResult> query(String prefix) {
        if (prefix.isEmpty()) {
            throw new RuntimeException("tried to query for an empty string");
        }

        int secondLevelIndex = firstLevelIndex.getOffset(prefix);
        if (secondLevelIndex < 0) {
            return Collections.emptyList();
        }

        seekToSecondLevelIndexOffset(secondLevelIndex);
        try {
            SearchResult result;

            BufferedInputStream bis = new BufferedInputStream(file, 1024 * 64);
            byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_16LE);
            while (true) {
                byte commonPrefixLen = (byte) bis.read();
                byte numRemainingChars = (byte) bis.read();
                int numKeyBytes = ((int) commonPrefixLen + numRemainingChars) * Character.BYTES;

                // Read string and offset at once.
                bis.readNBytes(buffer, commonPrefixLen * Character.BYTES, (numRemainingChars * Character.BYTES) + 5);

                if (compareTo(buffer, prefixBytes, numKeyBytes) >= 0) {
                    char[] key = decodeString(numKeyBytes);
                    long entryOffset = entryOffsetToLong(buffer, numKeyBytes);
                    result = new SearchResult(key, entryOffset);
                    break;
                }
            }

            int limit = 32;
            List<SearchResult> results = new ArrayList<>(limit);
            while (result.startsWith(prefixBytes) && results.size() < limit) {
                results.add(result);
                result = readSecondLevelIndex(bis);
            }

            return results;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private char[] decodeString(int numBytes) {
        char[] chars = new char[numBytes / Character.BYTES];

        for (int i = 0; i < numBytes; i += Character.BYTES) {
            char ch = (char) ((buffer[i + 1]) << 8 | (buffer[i] & 0xFF));
            chars[i / Character.BYTES] = ch;
        }

        return chars;
    }

    private int compareTo(byte[] buffer, byte[] prefixBytes, int numBufferBytes) {
        for (int i = 0; i < Math.min(numBufferBytes, prefixBytes.length); i += Character.BYTES) {
            int bufChar = ((buffer[i + 1] & 0xFF) << 8) | (buffer[i] & 0xFF);
            int prefixChar = ((prefixBytes[i + 1] & 0xFF) << 8) | (prefixBytes[i] & 0xFF);

            int cmp = bufChar - prefixChar;
            if (cmp != 0) {
                return cmp;
            }
        }

        return Integer.compare(numBufferBytes, prefixBytes.length);
    }

    public byte[] getEntry(long offset) {
        try {
            file.getChannel().position(offset);
            file.readNBytes(buffer, 0, 3);
            int compressedSize = entryLengthToInt(buffer);

            // TODO: Return a limited (by compressedSize) InputStream over the file
            byte[] buf = new byte[compressedSize];
            file.readNBytes(buf, 0, buf.length);
            return buf;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Needed for when clicking on a link
    public long getEntryOffset(String name) {
        int secondLevelIndex = firstLevelIndex.getOffset(name);
        if (secondLevelIndex < 0) {
            return -1;
        }

        seekToSecondLevelIndexOffset(secondLevelIndex);

        try {
            BufferedInputStream bis = new BufferedInputStream(file, 1024 * 64);

            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_16LE);
            while (true) {
                byte commonPrefixLen = (byte) bis.read();
                byte numRemainingChars = (byte) bis.read();
                int numKeyBytes = ((int) commonPrefixLen + numRemainingChars) * Character.BYTES;

                // Read string and offset at once.
                bis.readNBytes(buffer, commonPrefixLen * Character.BYTES, numRemainingChars * Character.BYTES + 5);

                int cmp = compareTo(buffer, nameBytes, numKeyBytes);
                if (cmp == 0) {
                    return entryOffsetToLong(buffer, numKeyBytes);
                } else if (cmp > 0) {
                    return -1;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SearchResult readSecondLevelIndex(BufferedInputStream is) throws IOException {
        byte commonPrefixLen = (byte) is.read();
        byte numRemainingChars = (byte) is.read();
        int numKeyBytes = ((int) commonPrefixLen + numRemainingChars) * Character.BYTES;

        // Read string and offset at once
        is.readNBytes(buffer, commonPrefixLen * Character.BYTES, numRemainingChars * Character.BYTES + 5);
        char[] key = decodeString(numKeyBytes);

        long entryOffset = entryOffsetToLong(buffer, numKeyBytes);

        return new SearchResult(key, entryOffset);
    }

    private void seekToSecondLevelIndexOffset(int offset) {
        try {
            file.getChannel().position(secondLevelIndexOffset + offset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int u16leToInt(byte[] bytes) {
        return ((bytes[1] & 0xFF) << 8) | (bytes[0] & 0xFF);
    }

    private int u32leToInt(byte[] bytes) {
        // Assuming that input is < 2^31 - 1
        return (bytes[3] << 24) | ((bytes[2] & 0xFF) << 16) | ((bytes[1] & 0xFF) << 8) | (bytes[0] & 0xFF);
    }

    private int entryLengthToInt(byte[] bytes) {
        return ((bytes[2] & 0xFF) << 16) | ((bytes[1] & 0xFF) << 8) | (bytes[0] & 0xFF);
    }

    private long entryOffsetToLong(byte[] bytes, int offset) {
        // 5 bytes is plenty for an offset. 2^40 B ~= 1 TB
        return (long) (bytes[4 + offset] & 0xFF) << 32 |
                (long) (bytes[3 + offset] & 0xFF) << 24 |
                (bytes[2 + offset] & 0xFF) << 16 |
                (bytes[1 + offset] & 0xFF) << 8 |
                bytes[offset] & 0xFF;
    }
}
