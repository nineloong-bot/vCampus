package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record ActiveOnlyQuery(boolean activeOnly) implements Serializable { }
