package com.districtx.pacifica.service;

import com.districtx.pacifica.ConfigStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;

public final class WarpManager {
    private final ConfigStore store;
    public WarpManager(ConfigStore store) { this.store = store; }
    public List<String> names() {
        List<String> names = new ArrayList<>(store.warpNames());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
    public Location get(String name) { return store.warp(name); }
    public void set(String name, Location location) { store.setWarp(name, location); }
    public String randomName() {
        List<String> names = names();
        return names.isEmpty() ? null : names.get(ThreadLocalRandom.current().nextInt(names.size()));
    }
    public Location random() { String name = randomName(); return name == null ? null : get(name); }
}