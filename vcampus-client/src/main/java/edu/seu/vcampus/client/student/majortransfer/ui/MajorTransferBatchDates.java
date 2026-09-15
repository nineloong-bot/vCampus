package edu.seu.vcampus.client.student.majortransfer.ui;

import javax.swing.JTextField;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Parses and formats transfer-batch date fields consistently in Beijing time. */
final class MajorTransferBatchDates {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private MajorTransferBatchDates() { }

    static String format(Instant instant) {
        return instant == null ? "" : FORMAT.format(instant.atZone(SHANGHAI));
    }

    static Instant parse(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text, FORMAT).atZone(SHANGHAI).toInstant();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(label + " 格式错误，应为 yyyy-MM-dd HH:mm");
        }
    }
}
