package com.iitm.aero.taallocator.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HungarianAlgorithmTest {

    @Test
    public void testSimple2x2OptimalMatching() {
        // Slot 0 prefers Student 0 (100) and Student 1 (10)
        // Slot 1 prefers Student 0 (90) and Student 1 (20)
        // If greedy: Slot 0 takes Student 0 (100), Slot 1 gets Student 1 (20) -> total 120.
        // Hungarian optimal:
        // Slot 0 -> Student 0 (100) + Slot 1 -> Student 1 (20) = 120.
        double[][] weights = {
            { 100.0, 10.0 },
            { 90.0,  20.0 }
        };

        int[] match = HungarianAlgorithm.findMaxWeightMatching(weights);
        assertEquals(0, match[0]); // Slot 0 -> Student 0
        assertEquals(1, match[1]); // Slot 1 -> Student 1
    }

    @Test
    public void testGlobalOptimizationOverGreedy() {
        // Slot 0 prefers Student 0 (100) and Student 1 (80)
        // Slot 1 ONLY prefers Student 0 (90) and has 0 for Student 1
        // Greedy: Slot 0 takes Student 0 (100), leaving Slot 1 with NOTHING (0) -> total = 100.
        // Hungarian: Slot 0 takes Student 1 (80), Slot 1 takes Student 0 (90) -> total = 170!
        double[][] weights = {
            { 100.0, 80.0 },
            { 90.0,  0.0  }
        };

        int[] match = HungarianAlgorithm.findMaxWeightMatching(weights);
        assertEquals(1, match[0]); // Slot 0 gets Student 1 (80)
        assertEquals(0, match[1]); // Slot 1 gets Student 0 (90)
        // Total satisfaction: 170 vs 100!
    }

    @Test
    public void testMoreSlotsThanStudents() {
        // 3 slots, only 2 students available
        double[][] weights = {
            { 100.0, 50.0 },
            { 50.0,  100.0 },
            { 10.0,  10.0 }
        };

        int[] match = HungarianAlgorithm.findMaxWeightMatching(weights);
        assertEquals(0, match[0]); // Slot 0 -> Student 0
        assertEquals(1, match[1]); // Slot 1 -> Student 1
        assertEquals(-1, match[2]); // Slot 2 unmatched
    }
}
