package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordCommandCleanupTest {
    @Test
    void loginCommandCopiesAndCanClearItsPassword() {
        char[] source = "Password1".toCharArray();
        LoginCommand command = new LoginCommand("ALICE", source, "client");
        char[] exposed = command.password();
        exposed[0] = 'X';

        assertThat(source).containsOnly('\0');
        assertThat(command.password()).containsExactly("Password1".toCharArray());

        command.clearPassword();
        command.clearPassword();

        assertThat(command.password()).containsOnly('\0');
    }

    @Test
    void teacherApplicationCopiesAndCanClearItsPassword() {
        char[] source = "Password1".toCharArray();
        TeacherAccountApplicationCommand command =
                new TeacherAccountApplicationCommand("TEACHER", source);
        char[] exposed = command.password();
        exposed[0] = 'X';

        assertThat(source).containsOnly('\0');
        assertThat(command.password()).containsExactly("Password1".toCharArray());

        command.clearPassword();
        command.clearPassword();

        assertThat(command.password()).containsOnly('\0');
    }

    @Test
    void changePasswordCommandCopiesAndCanClearBothPasswords() {
        char[] oldSource = "OldPass123".toCharArray();
        char[] newSource = "NewPass123".toCharArray();
        ChangePasswordCommand command = new ChangePasswordCommand(oldSource, newSource);

        assertThat(oldSource).containsOnly('\0');
        assertThat(newSource).containsOnly('\0');
        assertThat(command.oldPassword()).containsExactly("OldPass123".toCharArray());
        assertThat(command.newPassword()).containsExactly("NewPass123".toCharArray());

        command.clearPasswords();
        command.clearPasswords();

        assertThat(command.oldPassword()).containsOnly('\0');
        assertThat(command.newPassword()).containsOnly('\0');
    }
}
