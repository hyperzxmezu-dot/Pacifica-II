package com.districtx.pacifica.api.tag;

/** Immutable implementation of the public Tag view. */
public final class TagSnapshot implements Tag {
    private final String id;
    private final String displayName;
    private final TagRarity rarity;
    private final TagSource source;
    private final boolean enabled;

    /**
     * Creates an immutable tag snapshot.
     *
     * @param id stable tag identifier
     * @param displayName configured display name
     * @param rarity tag rarity
     * @param source tag source
     * @param enabled whether the tag feature is enabled
     */
    public TagSnapshot(String id, String displayName, TagRarity rarity, TagSource source, boolean enabled) {
        this.id = id;
        this.displayName = displayName;
        this.rarity = rarity;
        this.source = source;
        this.enabled = enabled;
    }

    /** @return stable tag identifier */
    @Override public String getId() { return id; }

    /** @return configured display name */
    @Override public String getDisplayName() { return displayName; }

    /** @return tag rarity */
    @Override public TagRarity getRarity() { return rarity; }

    /** @return tag source */
    @Override public TagSource getSource() { return source; }

    /** @return whether the Pacifica-II tag feature is enabled */
    @Override public boolean isEnabled() { return enabled; }
}