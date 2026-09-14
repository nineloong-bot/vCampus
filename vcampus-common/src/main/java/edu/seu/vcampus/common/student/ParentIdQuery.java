package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 按父级实体标识查询子实体的请求对象。
 *  *
 *  * @param parentId 父级实体标识
 */
public record ParentIdQuery(String parentId) implements Serializable { }
