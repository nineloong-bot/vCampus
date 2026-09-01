package edu.seu.vcampus.server.student.service;

/** Transaction checkpoints used by deterministic rollback tests. */
public enum AdmissionFailurePoint { AFTER_NUMBERS, AFTER_ACCOUNT, AFTER_PROFILE, AFTER_AUDIT, AFTER_DEDUP }
