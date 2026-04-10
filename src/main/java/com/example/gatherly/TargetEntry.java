package com.example.gatherly;

/**
 * A block/item collection target within a TodoItem.
 * blockId uses registry string form (e.g. "minecraft:oak_log").
 */
public class TargetEntry {
    public String blockId = "minecraft:stone";
    public int requiredCount = 64;
    public int currentCount = 0;

    public TargetEntry() {}

    public TargetEntry(String blockId, int requiredCount) {
        this.blockId = blockId;
        this.requiredCount = requiredCount;
    }

    public boolean isMet() {
        return currentCount >= requiredCount;
    }

    public float progress() {
        if (requiredCount <= 0) return 1.0f;
        return Math.min(1.0f, (float) currentCount / requiredCount);
    }
}
