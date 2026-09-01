package edu.seu.vcampus.server.student.service;

/** Test seam for proving all admission writes roll back together. */
@FunctionalInterface
public interface AdmissionFailureInjector {
    AdmissionFailureInjector NONE = point -> { };
    void reached(AdmissionFailurePoint point);
}
