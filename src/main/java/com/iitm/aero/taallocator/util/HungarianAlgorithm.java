package com.iitm.aero.taallocator.util;

import java.util.Arrays;

/**
 * Maximum Weight Bipartite Matching using the Hungarian (Kuhn-Munkres) Algorithm.
 * Optimizes total preference satisfaction across courses and students in O(N^2 * M) time.
 */
public class HungarianAlgorithm {

    /**
     * Finds the maximum weight matching on a bipartite graph.
     * 
     * @param weights 2D matrix where weights[i][j] is the weight/satisfaction score 
     *                of assigning slot i (row) to student j (column).
     * @return int[] array of length N (number of slots), where result[i] is the matched student index j,
     *         or -1 if slot i could not be matched with a positive-weight student.
     */
    public static int[] findMaxWeightMatching(double[][] weights) {
        int n = weights.length;
        if (n == 0) return new int[0];
        int m = weights[0].length;
        if (m == 0) {
            int[] empty = new int[n];
            Arrays.fill(empty, -1);
            return empty;
        }

        // We pad columns with dummy nodes if slots > students so cols >= rows always holds
        int cols = Math.max(n, m);
        int rows = n;

        // Find maximum weight to convert maximization into a minimization problem:
        // cost = maxW - weight (so minimum cost <=> maximum weight)
        double maxW = 0.0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < m; j++) {
                if (weights[i][j] > maxW) {
                    maxW = weights[i][j];
                }
            }
        }

        // Ensure maxW is positive so valid edges have lower costs than dummy/zero edges
        if (maxW <= 0) {
            maxW = 1.0;
        }

        // Build 1-indexed cost matrix: rows x cols
        double[][] cost = new double[rows + 1][cols + 1];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double w = (j < m) ? weights[i][j] : 0.0;
                cost[i + 1][j + 1] = maxW - w;
            }
        }

        // Hungarian algorithm for min-cost matching (1-indexed)
        double[] u = new double[rows + 1];
        double[] v = new double[cols + 1];
        int[] p = new int[cols + 1]; // p[j] = matched row for column j
        int[] way = new int[cols + 1];

        for (int i = 1; i <= rows; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[cols + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            boolean[] used = new boolean[cols + 1];

            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.POSITIVE_INFINITY;
                int j1 = 0;

                for (int j = 1; j <= cols; j++) {
                    if (!used[j]) {
                        double cur = cost[i0][j] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }

                for (int j = 0; j <= cols; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);

            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }

        // Extract matched column for each row
        int[] result = new int[rows];
        Arrays.fill(result, -1);
        for (int j = 1; j <= cols; j++) {
            if (p[j] > 0 && p[j] <= rows) {
                int rowIdx = p[j] - 1;
                int colIdx = j - 1;
                // Only consider matched if within real columns and has a strictly positive weight
                if (colIdx < m && weights[rowIdx][colIdx] > 0) {
                    result[rowIdx] = colIdx;
                } else {
                    result[rowIdx] = -1;
                }
            }
        }

        return result;
    }
}
