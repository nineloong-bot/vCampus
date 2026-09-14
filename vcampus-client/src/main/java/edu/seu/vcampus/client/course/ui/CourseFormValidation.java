package edu.seu.vcampus.client.course.ui;

import java.time.Instant;
import java.time.LocalDate;/**
 * 课程管理相关表单与对话框的统一输入校验工具类。
 */


final class CourseFormValidation {
    private CourseFormValidation() { }

    static void requireOrdered(LocalDate start, LocalDate end, String message) {
        if (!end.isAfter(start)) throw new IllegalArgumentException(message);
    }

    static void requireOrdered(Instant start, Instant end, String message) {
        if (!end.isAfter(start)) throw new IllegalArgumentException(message);
    }
}
