package edu.seu.vcampus.client.course.service;
/** Stable protocol failure suitable for page state and notification mapping. */
public final class CourseClientException extends RuntimeException { private final String code,traceId; private final boolean retryable; public CourseClientException(String code,String message,String traceId,boolean retryable){super(message);this.code=code;this.traceId=traceId;this.retryable=retryable;} public String code(){return code;} public String traceId(){return traceId;} public boolean retryable(){return retryable;} }
