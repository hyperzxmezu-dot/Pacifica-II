package com.districtx.pacifica.service;

public final class BountyDropProbabilityTest {
    private BountyDropProbabilityTest() { }

    public static void run() {
        require(BountyManager.shouldDropHead(0.10D, 0.000999D), "roll below 0.001 must drop");
        require(!BountyManager.shouldDropHead(0.10D, 0.001D), "0.10 percent must equal 0.001 probability");
        require(!BountyManager.shouldDropHead(0.10D, 0.5D), "roll above the configured probability must not drop");
        require(!BountyManager.shouldDropHead(0.0D, 0.0D), "zero chance must never drop");
        require(!BountyManager.shouldDropHead(Double.NaN, 0.0D), "invalid chance must never drop");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}