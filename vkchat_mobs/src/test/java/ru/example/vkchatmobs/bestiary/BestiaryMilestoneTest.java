package ru.example.vkchatmobs.bestiary;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BestiaryMilestoneTest {

    @Test
    void milestoneThresholdsAreOrdered() {
        Map<Integer, Map<String, Object>> milestones = new TreeMap<>();
        milestones.put(10, Map.of("hp-bonus", 2, "damage-bonus", 1, "rep-reward", 100));
        milestones.put(50, Map.of("hp-bonus", 4, "damage-bonus", 2, "rep-reward", 250));
        milestones.put(100, Map.of("hp-bonus", 6, "damage-bonus", 3, "rep-reward", 500));
        milestones.put(250, Map.of("hp-bonus", 10, "damage-bonus", 5, "rep-reward", 1000));
        milestones.put(500, Map.of("hp-bonus", 15, "damage-bonus", 8, "rep-reward", 2000));
        milestones.put(1000, Map.of("hp-bonus", 25, "damage-bonus", 12, "rep-reward", 5000));

        List<Integer> thresholds = new ArrayList<>(milestones.keySet());
        for (int i = 1; i < thresholds.size(); i++) {
            assertTrue(thresholds.get(i) > thresholds.get(i - 1),
                    "Milestone " + thresholds.get(i) + " should be greater than " + thresholds.get(i - 1));
        }
    }

    @Test
    void progressTowardsMilestone() {
        int kills = 37;
        Map<Integer, String> milestones = Map.of(
                10, "Новичок",
                50, "Охотник",
                100, "Ветеран"
        );

        int nextThreshold = -1;
        for (int threshold : new TreeSet<>(milestones.keySet())) {
            if (kills < threshold) {
                nextThreshold = threshold;
                break;
            }
        }

        assertEquals(50, nextThreshold, "Next milestone for 37 kills should be 50");
    }

    @Test
    void allMilestonesCompleted() {
        int kills = 1200;
        int[] thresholds = {10, 50, 100, 250, 500, 1000};
        for (int t : thresholds) {
            assertTrue(kills >= t, "Kills " + kills + " should complete milestone " + t);
        }
    }

    @Test
    void noMilestonesCompleted() {
        int kills = 5;
        int[] thresholds = {10, 50, 100, 250, 500, 1000};
        for (int t : thresholds) {
            assertFalse(kills >= t, "Kills " + kills + " should NOT complete milestone " + t);
        }
    }
}
