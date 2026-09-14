package edu.seu.vcampus.client.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.library.ui.BookDetailPanel;
import edu.seu.vcampus.client.library.ui.BookSearchPanel;

class LibraryPresentationTest {
    @Test
    void catalogAndDetailOmitRedundantDescriptions() {
        LibraryClientService service = mock(LibraryClientService.class);

        assertThat(labels(new BookSearchPanel(service)))
                .doesNotContain("按书名、作者或 ISBN 检索可借馆藏。");
        assertThat(labels(new BookDetailPanel(service)))
                .doesNotContain("查看书目信息和馆藏副本。");
    }

    private static List<String> labels(Container root) {
        List<String> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) result.add(label.getText());
            if (child instanceof Container container) result.addAll(labels(container));
        }
        return result;
    }
}
