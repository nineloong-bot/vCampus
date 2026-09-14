package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 院系信息视图对象。
 *  *
 *  * @param departmentId 院系标识
 *  * @param departmentName 院系名称
 */
public record DepartmentView(String departmentId, String code, String name,
        boolean active, long rowVersion) implements Serializable {
    @Override public String toString() { return name; }
}
