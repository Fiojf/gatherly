package com.example.gatherly;

import java.util.ArrayList;
import java.util.List;

public class SubTask {
    public String title = "New Sub-task";
    public boolean completed = false;
    public List<TargetEntry> targets = new ArrayList<>();
    public List<CoordinateEntry> coordinates = new ArrayList<>();

    public SubTask() {}

    public SubTask(String title) {
        this.title = title;
    }

    public boolean isCompleted() {
        if (targets.isEmpty()) return completed;
        for (TargetEntry t : targets) {
            if (!t.isMet()) return false;
        }
        return true;
    }
}
