package edu.seu.vcampus.server.user.service;
@FunctionalInterface
public interface UserQueryPort {
    String findLoginIdByUserId(String userId);
}
