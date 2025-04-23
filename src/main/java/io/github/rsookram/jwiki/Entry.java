package io.github.rsookram.jwiki;

public class Entry {
    public final long id;
    public final String word;
    public final String variants;
    public final String reading;
    public final String definitions;

    public Entry(long id, String word, String variants, String reading, String definitions) {
        this.id = id;
        this.word = word;
        this.variants = variants;
        this.reading = reading;
        this.definitions = definitions;
    }
}
