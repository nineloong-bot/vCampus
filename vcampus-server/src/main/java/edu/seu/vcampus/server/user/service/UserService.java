package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.routing.ClientContext;

/** Application boundary for user-management use cases implemented so far. */
public interface UserService {
    /** Applies for a pending teacher account using a public registration command. */
    UserView applyForTeacherAccount(TeacherAccountApplicationCommand command);

    /** Authenticates an active account for the requesting client connection. */
    LoginResult login(LoginCommand command, ClientContext context);
}
