package edu.seu.vcampus.server.user.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAliasResolverTest {
    @Test
    void resolvesShortLoginsToCanonicalAccounts() {
        assertThat(LoginAliasResolver.resolve("admin")).isEqualTo("ADMIN");
        assertThat(LoginAliasResolver.resolve("ADMIN")).isEqualTo("ADMIN");
        assertThat(LoginAliasResolver.resolve("stu")).isEqualTo("STUDENT");
        assertThat(LoginAliasResolver.resolve("stu_admin")).isEqualTo("STUDENT");
        assertThat(LoginAliasResolver.resolve("student")).isEqualTo("STUDENT");
        assertThat(LoginAliasResolver.resolve("course")).isEqualTo("COURSE");
        assertThat(LoginAliasResolver.resolve("course_admin")).isEqualTo("COURSE");
        assertThat(LoginAliasResolver.resolve("lib")).isEqualTo("LIBRARY");
        assertThat(LoginAliasResolver.resolve("library")).isEqualTo("LIBRARY");
        assertThat(LoginAliasResolver.resolve("shop")).isEqualTo("SHOP");
        assertThat(LoginAliasResolver.resolve("user")).isEqualTo("USER");
        assertThat(LoginAliasResolver.resolve("cs")).isEqualTo("CSADMIN");
        assertThat(LoginAliasResolver.resolve("math")).isEqualTo("MATHADMIN");
        assertThat(LoginAliasResolver.resolve("teacher")).isEqualTo("T001");
    }

    @Test
    void preservesUnknownLoginsAndHandlesNull() {
        assertThat(LoginAliasResolver.resolve(null)).isNull();
        assertThat(LoginAliasResolver.resolve("213260001")).isEqualTo("213260001");
        assertThat(LoginAliasResolver.resolve("SOME_UNKNOWN_USER")).isEqualTo("SOME_UNKNOWN_USER");
    }
}
