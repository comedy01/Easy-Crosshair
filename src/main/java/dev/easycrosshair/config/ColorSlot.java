package dev.easycrosshair.config;

public enum ColorSlot {
    CROSSHAIR("crosshair"),
    HOSTILE("hostile"),
    PASSIVE("passive"),
    PLAYER("player");

    private final String key;

    ColorSlot(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public ColorSlot next() {
        ColorSlot[] slots = values();
        return slots[(ordinal() + 1) % slots.length];
    }
}
