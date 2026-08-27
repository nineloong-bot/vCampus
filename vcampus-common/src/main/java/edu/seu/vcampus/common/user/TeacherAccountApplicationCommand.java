package edu.seu.vcampus.common.user;

import java.io.Serial;
import java.io.Serializable;

/** Public request for applying for a teacher account without choosing a role. */
public record TeacherAccountApplicationCommand(
        String loginId,
        char[] password) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
