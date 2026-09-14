package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 通用实体标识请求参数对象。
 *
 * @param entityId 目标实体标识
 */
public record EntityIdRequest(String entityId) implements Serializable { }
