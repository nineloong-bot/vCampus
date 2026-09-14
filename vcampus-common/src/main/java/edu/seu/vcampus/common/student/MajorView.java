package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 专业信息视图对象。
 *  *
 *  * @param majorId 专业标识
 *  * @param majorName 专业名称
 *  * @param departmentId 所属院系标识
 */
public record MajorView(String majorId, String departmentId, String code, String name,
        String grades, boolean active, long rowVersion) implements Serializable {
    @Override public String toString() { return name; }
}
