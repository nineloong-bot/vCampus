package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record DepartmentView(String departmentId, String code, String name,
        boolean active, long rowVersion) implements Serializable { }
