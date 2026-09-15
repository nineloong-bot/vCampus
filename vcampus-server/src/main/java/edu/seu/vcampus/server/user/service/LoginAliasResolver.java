package edu.seu.vcampus.server.user.service;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves convenient short administrator and role login identifiers
 * to their corresponding canonical database account identifiers.
 */
public final class LoginAliasResolver {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("ADMIN", "ADMIN"),
            Map.entry("SUPER_ADMIN", "ADMIN"),
            Map.entry("ADMIN_SUPER", "ADMIN"),
            Map.entry("STU", "STUDENT_ADMIN"),
            Map.entry("STU_ADMIN", "STUDENT_ADMIN"),
            Map.entry("STUDENT_ADMIN", "STUDENT_ADMIN"),
            Map.entry("COURSE", "COURSE_ADMIN"),
            Map.entry("COURSE_ADMIN", "COURSE_ADMIN"),
            Map.entry("LIB", "LIBRARY_ADMIN"),
            Map.entry("LIBRARY", "LIBRARY_ADMIN"),
            Map.entry("LIBRARY_ADMIN", "LIBRARY_ADMIN"),
            Map.entry("SHOP", "SHOP_ADMIN"),
            Map.entry("SHOP_ADMIN", "SHOP_ADMIN"),
            Map.entry("USER", "USER_ADMIN"),
            Map.entry("USER_ADMIN", "USER_ADMIN"),
            Map.entry("CS", "CS_COLLEGE_ADMIN"),
            Map.entry("CS_ADMIN", "CS_COLLEGE_ADMIN"),
            Map.entry("MATH", "MATH_COLLEGE_ADMIN"),
            Map.entry("MATH_ADMIN", "MATH_COLLEGE_ADMIN"),
            Map.entry("EE", "EE_COLLEGE_ADMIN"),
            Map.entry("EE_ADMIN", "EE_COLLEGE_ADMIN"),
            Map.entry("FL", "FL_COLLEGE_ADMIN"),
            Map.entry("FL_ADMIN", "FL_COLLEGE_ADMIN"),
            Map.entry("TEACHER", "DEMO_TEACHER"),
            Map.entry("STUDENT", "213242478")
    );

    private LoginAliasResolver() { }

    /**
     * Resolves a login identifier alias to its canonical database loginId,
     * or returns the normalized original login identifier if no alias exists.
     *
     * @param loginId the submitted login identifier
     * @return canonical login identifier
     */
    public static String resolve(String loginId) {
        if (loginId == null) {
            return null;
        }
        String normalized = loginId.trim().toUpperCase(Locale.ROOT);
        return ALIASES.getOrDefault(normalized, normalized);
    }
}
