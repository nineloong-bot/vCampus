package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;
import java.util.List;

/** View of the ranked applicants for a transfer option. */
public record MajorTransferRankingView(
        String optionId,
        String targetMajorName,
        int receiveQuota,
        List<RankedApplicant> applicants,
        double cutoffScore,
        long optionVersion
) implements Serializable {

    /**
     * 转专业综合排序候选人明细对象。
     *
     * @param applicationId 申请标识
     * @param studentId 学生标识
     * @param studentName 学生姓名
     * @param fromStudentNumber 原学号
     * @param applicationType 申请类型
     * @param writtenScore 笔试成绩
     * @param interviewScore 面试成绩
     * @param finalScore 综合总成绩
     * @param proposed 是否进入拟录取/推荐名单
     */
    public record RankedApplicant(
            String applicationId,
            String studentId,
            String studentName,
            String fromStudentNumber,
            MajorTransferApplicationType applicationType,
            Double writtenScore,
            Double interviewScore,
            Double finalScore,
            boolean proposed
    ) implements Serializable { }
}
