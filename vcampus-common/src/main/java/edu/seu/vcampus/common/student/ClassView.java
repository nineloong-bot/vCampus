package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 行政班级信息视图对象。
 *  *
 *  * @param classId 班级标识
 *  * @param className 班级名称
 *  * @param majorId 所属专业标识
 */
public record ClassView(String classId, String majorId, String code, String name,
        int enrollmentYear, int classNumber, boolean active, long rowVersion) implements Serializable {
    @Override public String toString() { return name; }
}
