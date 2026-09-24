package com.districtx.pacifica.service;

import com.districtx.pacifica.util.Items;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TagDefinition implements Tag {
    private static final Pattern HEX_TAG = Pattern.compile("</?#([A-Fa-f0-9]{6})>");
    private final String id;
    private String displayName;
    private TagRarity rarity;
    private TagSource source;

    public TagDefinition(String id, String displayName) {
        this(id, displayName, TagRarity.COMMON, TagSource.EVENT);
    }

    public TagDefinition(String id, String displayName, TagRarity rarity, TagSource source) {
        this.id = id;
        this.displayName = displayName;
        this.rarity = rarity;
        this.source = source;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public TagRarity getRarity() {
        return rarity;
    }

    public void setRarity(TagRarity rarity) {
        this.rarity = rarity;
    }

    public TagSource getSource() {
        return source;
    }

    public void setSource(TagSource source) {
        this.source = source;
    }

    public String displayText() {
        Matcher matcher = HEX_TAG.matcher(displayName);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            if (matcher.group().startsWith("</")) {
                matcher.appendReplacement(result, "");
                continue;
            }
            StringBuilder replacement = new StringBuilder("§x");
            for (char character : matcher.group(1).toCharArray()) replacement.append('§').append(character);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return Items.color(result.toString());
    }
}