package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UserView;

/** Application boundary for user-management use cases implemented so far. */
public interface UserService {
    /** Applies for a pending teacher account using a public registration command. */
    UserView applyForTeacherAccount(TeacherAccountApplicationCommand command);
}
