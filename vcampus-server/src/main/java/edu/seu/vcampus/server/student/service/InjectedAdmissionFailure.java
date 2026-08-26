package edu.seu.vcampus.server.student.service;

/** Deliberate test-only failure at an admission checkpoint. */
public final class InjectedAdmissionFailure extends RuntimeException {
    public InjectedAdmissionFailure(AdmissionFailurePoint point) { super("Injected at " + point); }
}
