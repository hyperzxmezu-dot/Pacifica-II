package com.districtx.pacifica.api.tag;

/** Read-only public representation of a Pacifica-II cosmetic tag. */
public interface Tag {
    /** @return stable tag identifier */
    String getId();

    /** @return configured display name */
    String getDisplayName();

    /** @return tag rarity */
    TagRarity getRarity();

    /** @return tag source */
    TagSource getSource();

    /** @return whether the Pacifica-II tag feature is enabled */
    boolean isEnabled();
}