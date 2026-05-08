package com.hunor.classicmodelsbackend.service;

/**
 * Static helpers for fuzzy string matching used by C12's customer
 * merge / duplicate-detection feature.
 *
 * <h3>Why Levenshtein</h3>
 *
 * <p>Levenshtein distance counts the minimum number of single-character
 * edits (insert, delete, substitute) needed to turn one string into
 * another. It's the simplest fuzzy-match algorithm that handles real
 * typos, capitalisation differences, and small punctuation variations
 * after normalisation. Trigram similarity (PostgreSQL's
 * {@code pg_trgm}) handles the same cases in a different way and is
 * faster at scale, but MySQL doesn't ship with it — and at our scale
 * (122 customers) the per-pair Levenshtein cost is negligible.</p>
 *
 * <h3>Normalisation</h3>
 *
 * <p>Before measuring, both strings are normalised: lowercased and
 * stripped of every non-alphanumeric character. So
 * "ATELIER GRAPHIQUE" and "atelier-graphique" reduce to the same
 * canonical form and score 100% similarity.</p>
 *
 * <h3>Why a static utility</h3>
 *
 * <p>The functions are pure (no state, no side effects, no Spring
 * dependencies) so making them a Spring bean would add ceremony for
 * no benefit. A package-private static utility class keeps the call
 * sites concise: {@code StringSimilarity.score("foo", "bar")}.</p>
 */
public final class StringSimilarity {

    private StringSimilarity() { /* utility class */ }

    /**
     * Returns a similarity score between 0.0 (totally different) and
     * 1.0 (identical after normalisation).
     *
     * <p>Score is {@code 1 - distance / max(len_a, len_b)} after
     * normalisation. Two empty strings score 1.0 (vacuously equal).
     * One empty + one non-empty scores 0.0.</p>
     */
    public static double score(String a, String b) {
        if (a == null || b == null) return 0.0;
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() && nb.isEmpty()) return 1.0;
        int maxLen = Math.max(na.length(), nb.length());
        if (maxLen == 0) return 1.0;
        int distance = levenshtein(na, nb);
        return 1.0 - (double) distance / maxLen;
    }

    /**
     * Levenshtein distance — the minimum number of single-character
     * edits (insertions, deletions, substitutions) needed to turn
     * {@code a} into {@code b}.
     *
     * <p>Classic dynamic-programming implementation with an
     * {@code (n+1) x (m+1)} table. Each cell {@code dp[i][j]} holds
     * the edit distance between the first {@code i} chars of {@code a}
     * and the first {@code j} chars of {@code b}.</p>
     *
     * <p>Time complexity: O(n × m). Space: O(n × m). For very long
     * strings the rolling-window optimisation reduces space to O(min(n, m))
     * but for customer-name-sized inputs the simple version is fine.</p>
     */
    static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();

        int[][] dp = new int[n + 1][m + 1];

        // Base cases: turning empty into a prefix of length k requires
        // k insertions.
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int substituteCost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,            // deletion
                                dp[i][j - 1] + 1),           // insertion
                        dp[i - 1][j - 1] + substituteCost);  // substitution
            }
        }

        return dp[n][m];
    }

    /**
     * Lowercase + strip non-alphanumerics. The cheapest normalisation
     * that handles capitalisation, hyphenation, and stray punctuation
     * (commas, periods, parentheses) without changing the
     * meaningful characters.
     */
    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
