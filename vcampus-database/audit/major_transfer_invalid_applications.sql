SELECT a.applicationId, '学生不是本科正常在籍在校状态' AS reason
FROM tblMajorTransferApplication AS a INNER JOIN tblStudent AS s ON a.studentId=s.studentId
WHERE s.studentType<>'UNDERGRADUATE' OR s.studentStatus<>'ACTIVE'
   OR s.enrolled IS NULL OR s.enrolled<>TRUE OR s.onCampus IS NULL OR s.onCampus<>TRUE
UNION ALL
SELECT a.applicationId, '申请时不是大一或年龄不在17至20岁' AS reason
FROM ((tblMajorTransferApplication AS a INNER JOIN tblStudent AS s ON a.studentId=s.studentId)
      INNER JOIN tblClass AS c ON s.classId=c.classId)
      INNER JOIN tblMajorTransferBatch AS b ON a.batchId=b.batchId
WHERE (Year(b.applicationStart)-IIf(Month(b.applicationStart)<9,1,0)-c.enrollmentYear+1)<>1
   OR s.birthDate IS NULL
   OR DateDiff('yyyy', s.birthDate, b.applicationStart)
      - IIf(Format(s.birthDate,'mmdd')>Format(b.applicationStart,'mmdd'),1,0) NOT BETWEEN 17 AND 20
UNION ALL
SELECT a.applicationId, '当前学院与目标学院相同' AS reason
FROM (((tblMajorTransferApplication AS a INNER JOIN tblStudent AS s ON a.studentId=s.studentId)
      INNER JOIN tblClass AS c ON s.classId=c.classId)
      INNER JOIN tblMajor AS fromMajor ON c.majorId=fromMajor.majorId)
      INNER JOIN tblMajorTransferOption AS o ON a.optionId=o.optionId
WHERE fromMajor.departmentId=o.targetDepartmentId;
