package edu.seu.vcampus.server.student.pdf;

import edu.seu.vcampus.common.student.PdfDocument;
import edu.seu.vcampus.common.student.StudentAcademicProfile;
import edu.seu.vcampus.common.student.StudentPersonalProfile;
import edu.seu.vcampus.common.student.StudentProfileData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A4 Chinese student information form rendered from approved values. */
public final class StudentProfilePdfService implements StudentProfilePdfGenerator {
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 36;
    private static final float FOOTER_TOP = 42;
    private static final float LABEL_WIDTH = 62;
    private static final float VALUE_WIDTH = (PAGE_WIDTH - 2 * MARGIN - 3 * LABEL_WIDTH) / 3;
    private static final float BODY_SIZE = 8.5f;
    private static final PDColor TEAL = new PDColor(new float[] {0.18f, 0.58f, 0.52f}, PDDeviceRGB.INSTANCE);
    private static final PDColor PALE = new PDColor(new float[] {0.93f, 0.97f, 0.96f}, PDDeviceRGB.INSTANCE);
    private static final PDColor TEXT = new PDColor(new float[] {0.20f, 0.22f, 0.23f}, PDDeviceRGB.INSTANCE);

    @Override
    public PdfDocument generate(StudentProfileData profile, Instant generatedAt) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(generatedAt, "generatedAt");
        try (PDDocument document = new PDDocument();
             InputStream fontStream = requireFont()) {
            PDFont font = PDType0Font.load(document, fontStream, true);
            Renderer renderer = new Renderer(document, font);
            renderer.title(profile, generatedAt);
            renderer.section("个人基本信息");
            renderer.fields(personalFields(profile));
            renderer.section("学籍信息");
            renderer.fields(academicFields(profile.academic()));
            renderer.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            String filename = "学籍基本信息_" + safe(profile.core().studentNumber()) + "_"
                    + safe(profile.core().studentName()) + ".pdf";
            return new PdfDocument(filename, output.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException("无法生成学籍信息 PDF", error);
        }
    }

    private static InputStream requireFont() {
        InputStream value = StudentProfilePdfService.class.getResourceAsStream(
                "/fonts/NotoSansCJKsc-Regular.ttf");
        if (value == null) throw new IllegalStateException("缺少 PDF 中文字体");
        return value;
    }

    private static List<Field> personalFields(StudentProfileData p) {
        StudentPersonalProfile v = p.personal();
        List<Field> values = new ArrayList<>();
        values.add(new Field("一卡通号", p.core().campusCardNumber()));
        values.add(new Field("学号", p.core().studentNumber()));
        values.add(new Field("姓名", p.core().studentName()));
        values.add(new Field("姓名拼音", v.namePinyin())); values.add(new Field("曾用名", v.formerName()));
        values.add(new Field("性别", p.core().gender())); values.add(new Field("政治面貌", v.politicalStatus()));
        values.add(new Field("民族", v.ethnicity())); values.add(new Field("婚姻状态", v.maritalStatus()));
        values.add(new Field("证件类型", v.idDocumentType())); values.add(new Field("身份证件号", v.idDocumentNumber()));
        values.add(new Field("证件签发日期", show(v.idIssuedDate()))); values.add(new Field("出生日期", show(v.birthDate())));
        values.add(new Field("籍贯", v.nativePlace())); values.add(new Field("国家地区", v.countryRegion()));
        values.add(new Field("出生地", v.birthplace())); values.add(new Field("生源地", v.studentOriginPlace()));
        values.add(new Field("户口性质", v.householdRegistrationType()));
        values.add(new Field("入学前户口", v.householdBeforeEnrollment()));
        values.add(new Field("入学后户口", v.householdAfterEnrollment()));
        values.add(new Field("港澳台侨外", v.overseasChineseStatus())); values.add(new Field("信仰宗教", v.religion()));
        values.add(new Field("是否团员", yesNo(v.leagueMember()))); values.add(new Field("入团时间", show(v.leagueJoinDate())));
        values.add(new Field("是否党员", yesNo(v.partyMember()))); values.add(new Field("入党时间", show(v.partyJoinDate())));
        values.add(new Field("健康状况", v.healthStatus())); values.add(new Field("血型", v.bloodType()));
        values.add(new Field("体重(KG)", show(v.weightKg()))); values.add(new Field("身高(CM)", show(v.heightCm())));
        values.add(new Field("特长", v.specialties())); values.add(new Field("爱好", v.hobbies()));
        values.add(new Field("是否独生子女", yesNo(v.onlyChild()))); values.add(new Field("邮箱", v.email()));
        values.add(new Field("联系电话", v.phone()));
        return values;
    }

    private static List<Field> academicFields(StudentAcademicProfile v) {
        return List.of(new Field("学生类别", v.studentCategory()), new Field("是否在籍", yesNo(v.enrolled())),
                new Field("是否在校", yesNo(v.onCampus())), new Field("学籍状态", v.academicStatus()),
                new Field("校区", v.campus()), new Field("现在年级", v.currentGrade()),
                new Field("院系", v.departmentName()), new Field("专业", v.majorName()),
                new Field("班级", v.className()), new Field("培养层次", v.educationLevel()),
                new Field("培养方式", v.trainingMode()), new Field("学制", show(v.programLengthYears())),
                new Field("就读方式", v.attendanceMode() == null ? null : v.attendanceMode().displayName()),
                new Field("就读学位", v.degreeName()), new Field("就读学历", v.educationName()),
                new Field("预计毕业日期", show(v.expectedGraduationDate())), new Field("毕业日期", show(v.graduationDate())),
                new Field("学生来源", v.studentSource()), new Field("学习形式(研)", v.graduateStudyMode()),
                new Field("辅导员姓名", v.counselorName()), new Field("辅导员联系方式", v.counselorContact()));
    }

    private static String yesNo(boolean value) { return value ? "是" : "否"; }
    private static String show(Object value) { return value == null ? "" : value.toString(); }
    private static String safe(String value) {
        return value == null ? "未填写" : value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private record Field(String label, String value) { }

    private static final class Renderer {
        private final PDDocument document;
        private final PDFont font;
        private PDPage page;
        private PDPageContentStream content;
        private float y;
        private int pageNumber;

        private Renderer(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) { footer(); content.close(); }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
            pageNumber++;
        }

        private void title(StudentProfileData profile, Instant generatedAt) throws IOException {
            drawCentered("vCampus 学籍管理", 11, y); y -= 25;
            drawCentered("学生基本信息表", 20, y); y -= 30;
            String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.of("Asia/Shanghai")).format(generatedAt);
            text("生成时间：" + time, MARGIN, y, BODY_SIZE, TEXT);
            text("学号：" + show(profile.core().studentNumber()), PAGE_WIDTH / 2, y, BODY_SIZE, TEXT);
            y -= 22;
        }

        private void section(String title) throws IOException {
            ensureSpace(28);
            content.setNonStrokingColor(TEAL); content.addRect(MARGIN, y - 20,
                    PAGE_WIDTH - 2 * MARGIN, 20); content.fill();
            text(title, MARGIN + 7, y - 14, 10, new PDColor(new float[] {1, 1, 1}, PDDeviceRGB.INSTANCE));
            y -= 26;
        }

        private void fields(List<Field> fields) throws IOException {
            for (int start = 0; start < fields.size(); start += 3) {
                List<Field> row = fields.subList(start, Math.min(start + 3, fields.size()));
                drawRow(row);
            }
            y -= 10;
        }

        private void drawRow(List<Field> row) throws IOException {
            List<List<String>> lines = new ArrayList<>();
            int maxLines = 1;
            for (Field field : row) {
                List<String> valueLines = wrap(show(field.value()), VALUE_WIDTH - 8, BODY_SIZE);
                lines.add(valueLines); maxLines = Math.max(maxLines, valueLines.size());
            }
            float height = Math.max(24, 9 + maxLines * 11);
            ensureSpace(height + 2);
            for (int column = 0; column < 3; column++) {
                float x = MARGIN + column * (LABEL_WIDTH + VALUE_WIDTH);
                content.setNonStrokingColor(PALE); content.addRect(x, y - height, LABEL_WIDTH, height); content.fill();
                strokeRect(x, y - height, LABEL_WIDTH, height); strokeRect(x + LABEL_WIDTH, y - height, VALUE_WIDTH, height);
                if (column < row.size()) {
                    text(row.get(column).label(), x + 4, y - 15, BODY_SIZE, TEXT);
                    int line = 0;
                    for (String valueLine : lines.get(column))
                        text(valueLine, x + LABEL_WIDTH + 4, y - 15 - line++ * 11, BODY_SIZE, TEXT);
                }
            }
            y -= height;
        }

        private List<String> wrap(String value, float width, float size) throws IOException {
            if (value.isBlank()) return List.of("未填写");
            List<String> result = new ArrayList<>(); StringBuilder line = new StringBuilder();
            for (int offset = 0; offset < value.length();) {
                int point = value.codePointAt(offset); String character = new String(Character.toChars(point));
                if (!line.isEmpty() && textWidth(line + character, size) > width) {
                    result.add(line.toString()); line.setLength(0);
                }
                line.append(character); offset += Character.charCount(point);
            }
            if (!line.isEmpty()) result.add(line.toString());
            return result;
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < FOOTER_TOP) newPage();
        }

        private void footer() throws IOException {
            content.setStrokingColor(TEAL); content.moveTo(MARGIN, 31);
            content.lineTo(PAGE_WIDTH - MARGIN, 31); content.stroke();
            text("数据以系统正式档案为准", MARGIN, 18, 7.5f, TEXT);
            String pageText = "第 " + pageNumber + " 页";
            text(pageText, PAGE_WIDTH - MARGIN - textWidth(pageText, 7.5f), 18, 7.5f, TEXT);
        }

        private void finish() throws IOException { footer(); content.close(); content = null; }
        private void drawCentered(String value, float size, float baseline) throws IOException {
            text(value, (PAGE_WIDTH - textWidth(value, size)) / 2, baseline, size, TEXT);
        }
        private float textWidth(CharSequence value, float size) throws IOException {
            return font.getStringWidth(value.toString()) / 1000f * size;
        }
        private void text(String value, float x, float baseline, float size, PDColor color) throws IOException {
            content.beginText(); content.setFont(font, size); content.setNonStrokingColor(color);
            content.newLineAtOffset(x, baseline); content.showText(value); content.endText();
        }
        private void strokeRect(float x, float bottom, float width, float height) throws IOException {
            content.setStrokingColor(TEAL); content.setLineWidth(0.45f);
            content.addRect(x, bottom, width, height); content.stroke();
        }
    }
}
