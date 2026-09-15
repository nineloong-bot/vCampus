package edu.seu.vcampus.client.library.ui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.function.IntConsumer;

/** Navigation for a server-paged library table; only successful loads advance the page. */
final class LibraryPagination extends JPanel {
    private final JButton previous = new JButton("上一页");
    private final JButton next = new JButton("下一页");
    private final JLabel position = new JLabel("第 1 / 1 页");
    private int page = 1;
    private long pages = 1;

    LibraryPagination(IntConsumer loadPage) {
        super(new FlowLayout(FlowLayout.CENTER, 8, 4));
        setOpaque(false);
        previous.addActionListener(event -> loadPage.accept(page - 1));
        next.addActionListener(event -> loadPage.accept(page + 1));
        add(previous);
        add(position);
        add(next);
        setLoading(false);
    }

    void showPage(int page, int pageSize, long total) {
        this.page = page;
        pages = Math.max(1, (total + pageSize - 1) / pageSize);
        position.setText("第 " + page + " / " + pages + " 页");
        setLoading(false);
    }

    void setLoading(boolean loading) {
        previous.setEnabled(!loading && page > 1);
        next.setEnabled(!loading && page < pages);
    }
}
