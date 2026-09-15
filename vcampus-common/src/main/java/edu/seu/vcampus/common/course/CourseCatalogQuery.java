package edu.seu.vcampus.common.course; import java.io.*;
/** Catalog filters and paging. */ /**
 * Carries immutable course catalog query data.
 * @param keyword the keyword
 * @param activeOnly the active only
 * @param page the page
 * @param pageSize the page size
 */
public record CourseCatalogQuery(String keyword,Boolean activeOnly,int page,int pageSize)implements Serializable{@Serial private static final long serialVersionUID=1L;/**
 * Validates and creates a course catalog query.
 * @param keyword the keyword
 * @param activeOnly the active only
 * @param page the page
 * @param pageSize the page size
 */
public CourseCatalogQuery{if(page<0||pageSize<1||pageSize>100||(long)page*pageSize+pageSize>Integer.MAX_VALUE)throw new IllegalArgumentException("invalid page");}}
