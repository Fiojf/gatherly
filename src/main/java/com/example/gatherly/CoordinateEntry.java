package com.example.gatherly;

/**
 * A saved coordinate/location bookmark within a TodoItem.
 */
public class CoordinateEntry {
    public int x = 0;
    public int y = 64;
    public int z = 0;
    public String label = "";

    public CoordinateEntry() {}

    public CoordinateEntry(int x, int y, int z, String label) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.label = label;
    }
}
