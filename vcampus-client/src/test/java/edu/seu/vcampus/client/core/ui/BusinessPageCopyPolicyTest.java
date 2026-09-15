package edu.seu.vcampus.client.core.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Prevents removed business-page helper captions from returning. */
class BusinessPageCopyPolicyTest {
    private static final List<String> FORBIDDEN = List.of(
            "按课程查看可选教学班",
            "维护学期名称、教学日期和状态",
            "查看书目信息和馆藏副本",
            "新增和短编辑使用统一课程表单",
            "参考数据已就绪",
            "尚无修改申请",
            "page.description");

    @Test
    void businessSourcesContainNoRemovedHelperCaptions() throws IOException {
        Path source = Path.of(System.getProperty("user.dir"), "src", "main", "java");
        String all;
        try (var files = Files.walk(source)) {
            all = files.filter(path -> path.toString().endsWith(".java"))
                    .map(BusinessPageCopyPolicyTest::read)
                    .reduce("", (left, right) -> left + '\n' + right);
        }
        assertThat(FORBIDDEN).allSatisfy(text -> assertThat(all).doesNotContain(text));
        assertThat(all).contains("校园服务 · 学术生活");
    }

    private static String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }
}
