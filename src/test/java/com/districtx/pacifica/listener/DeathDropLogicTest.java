package com.districtx.pacifica.listener;

public final class DeathDropLogicTest {
    private DeathDropLogicTest() { }

    public static void run() {
        require(PacificaListener.shouldDropHead(0.10D, 0.000999D), "roll below 0.001 must drop");
        require(!PacificaListener.shouldDropHead(0.10D, 0.001D), "0.10 percent must be a 0.001 probability");
        require(!PacificaListener.shouldDropHead(0.10D, 0.5D), "roll above the configured probability must not drop");
        require(!PacificaListener.shouldDropHead(0.0D, 0.0D), "zero chance must never drop");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}