package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Shared Shop-only previous/next control for catalog result pages. */
final class ShopPaginationPanel extends JPanel {
    private final JButton previous;
    private final JButton next;
    private final JLabel status;
    private IntConsumer pageChanged = ignored -> { };
    private int page;

    ShopPaginationPanel(String prefix, ShopUiKit uiKit) {
        super(new FlowLayout(FlowLayout.CENTER, 8, 0));
        String stablePrefix = Objects.requireNonNull(prefix, "prefix") + ".pagination";
        Objects.requireNonNull(uiKit, "uiKit");
        setName(stablePrefix);
        previous = uiKit.secondaryButton(stablePrefix + ".previous", "上一页");
        next = uiKit.secondaryButton(stablePrefix + ".next", "下一页");
        status = named(new JLabel("第 1 / 1 页"), stablePrefix + ".status");
        previous.getAccessibleContext().setAccessibleName("上一页");
        next.getAccessibleContext().setAccessibleName("下一页");
        status.getAccessibleContext().setAccessibleName("分页状态");
        previous.addActionListener(event -> pageChanged.accept(page - 1));
        next.addActionListener(event -> pageChanged.accept(page + 1));
        add(previous);
        add(status);
        add(next);
        previous.setEnabled(false);
        next.setEnabled(false);
        status.setFont(UiTypography.BODY);
        status.setForeground(UiColors.TEXT_SECONDARY);
    }

    void showPage(PageResult<?> result, IntConsumer pageChanged) {
        Objects.requireNonNull(result, "result");
        if (result.page() < 0 || result.pageSize() <= 0 || result.total() < 0) {
            throw new IllegalArgumentException("Invalid page result");
        }
        this.page = result.page();
        this.pageChanged = Objects.requireNonNull(pageChanged, "pageChanged");
        long totalPages = Math.max(1L,
                (result.total() + (long) result.pageSize() - 1L) / result.pageSize());
        status.setText("第 " + ((long) result.page() + 1L) + " / " + totalPages + " 页");
        previous.setEnabled(result.page() > 0);
        long nextOffset = ((long) result.page() + 1L) * (long) result.pageSize();
        next.setEnabled(nextOffset < result.total());
    }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
