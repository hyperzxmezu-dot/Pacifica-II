package com.districtx.pacifica.service;

import com.districtx.pacifica.api.taxi.TaxiReason;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public final class SafeSpawnLogicTest {
    private SafeSpawnLogicTest() { }

    public static void run() {
        UUID configuredWorld = UUID.randomUUID();
        World world = world(configuredWorld);
        Location inside = new Location(world, 10.5D, 64.5D, -2.5D);
        require(SafeSpawnRegion.containsCoordinates(inside, configuredWorld, 10.0D, 11.0D,
                64.0D, 65.0D, -3.0D, -2.0D), "XYZ inside bounds must be included");
        require(!SafeSpawnRegion.containsCoordinates(new Location(world, 11.0D, 64.5D, -2.5D), configuredWorld,
                10.0D, 11.0D, 64.0D, 65.0D, -3.0D, -2.0D), "maximum boundary must be exclusive");
        require(!SafeSpawnRegion.containsCoordinates(inside, UUID.randomUUID(), 10.0D, 11.0D,
                64.0D, 65.0D, -3.0D, -2.0D), "another world must be outside");
        require(!SafeSpawnRegion.containsCoordinates(inside, null, 10.0D, 11.0D,
                64.0D, 65.0D, -3.0D, -2.0D), "unconfigured region must be outside");

        require(SafeSpawnRegion.exited(true, false), "inside to outside must activate protection");
        require(!SafeSpawnRegion.exited(false, true), "outside to inside must not activate protection");
        require(!SafeSpawnRegion.exited(true, true), "inside to inside must not activate protection");
        require(!SafeSpawnRegion.exited(false, false), "outside to outside must not activate protection");
        require(TeleportManager.countdownSeconds(TaxiReason.QUICK_PLAY, 5, 10) == 5,
                "Quick Play must use five seconds");
        require(TeleportManager.countdownSeconds(TaxiReason.TPA, 5, 10) == 10,
                "other teleport types must retain ten seconds");
    }

    private static World world(UUID worldId) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("getUID") ? worldId : null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}