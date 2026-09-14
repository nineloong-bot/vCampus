package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 仅查询当前在籍有效实体的查询参数对象。
 *  *
 *  * @param activeOnly 是否仅包含活动状态记录
 */
public record ActiveOnlyQuery(boolean activeOnly) implements Serializable { }
