package edu.seu.vcampus.client.shop.commerce;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.*;

/** Shared navigation and content surfaces for the seller and administrator workspaces. */
final class ManagementLayout {
    private ManagementLayout() { }

    static JPanel seller(CommercePanel ui, JComponent main, String section) {
        JPanel side = CommerceTheme.card(Color.WHITE, 18);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(226, 400));
        side.add(CommerceTheme.heading("我的店铺", 20));
        side.add(CommerceTheme.gap(28));
        String[] labels = {"店铺概览", "商品管理", "订单管理", "经营资质", "店铺设置"};
        Runnable[] actions = {() -> new AccountPages(ui).loadWorkspace(), () -> new SellerCatalogPage(ui).open(),
                () -> new OrderPages(ui).list(true), () -> new GovernancePages(ui).qualifications(false),
                () -> new AccountPages(ui).loadSettings()};
        for (int i = 0; i < labels.length; i++) {
            JButton button = CommerceTheme.button(labels[i] + "  ›", actions[i]);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            if (labels[i].equals(section)) button.setBackground(new Color(0xe8f0e1));
            side.add(button);
            side.add(CommerceTheme.gap(9));
        }
        side.add(Box.createVerticalGlue());
        side.add(CommerceTheme.muted("店主工作台"));
        side.add(CommerceTheme.muted("校园集 · CAMPUS MARKET"));
        String description = switch (section) {
            case "商品管理" -> "管理草稿与在售商品，让好物被更多人发现。";
            case "订单管理" -> "查看订单进度，安排每一笔订单的履约。";
            case "经营资质" -> "查看主体资质与可经营范围。";
            case "店铺设置" -> "维护店铺资料，查看平台通知。";
            default -> "从这里开始，打理你的校园小店。";
        };
        JPanel content = new JPanel(new BorderLayout(0, 24));
        content.setOpaque(false);
        JPanel intro = CommerceTheme.form();
        intro.add(CommerceTheme.muted("SELLER WORKSPACE"));
        intro.add(CommerceTheme.gap(10));
        intro.add(CommerceTheme.muted(description));
        content.add(intro, BorderLayout.NORTH);
        content.add(main);
        JPanel shell = new JPanel(new BorderLayout(30, 0));
        shell.setOpaque(false);
        shell.add(side, BorderLayout.WEST);
        shell.add(content);
        return shell;
    }

    static JPanel admin(GovernancePages pages, JComponent main, String section) {
        JPanel side = CommerceTheme.card(new Color(0xedf1eb), 12);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(160, 300));
        boolean products=section.equals("商品管理");
        JButton shops=CommerceTheme.button("店铺管理", pages::shops);
        JButton catalog=CommerceTheme.button("商品管理", pages::products);
        CommerceTheme.primary(products?catalog:shops);
        side.add(shops);
        side.add(CommerceTheme.gap(12));
        side.add(catalog);
        JPanel tabs = CommerceTheme.row();
        String[] labels = products?new String[]{"商品列表", "举报与恢复申请", "处理记录"}:
                new String[]{"店铺列表", "开店审核", "资质审核", "举报与恢复申请", "处理记录"};
        Runnable[] actions = products?new Runnable[]{pages::products,()->pages.cases(false),()->pages.audit(null)}:
                new Runnable[]{pages::shops, pages::applications, () -> pages.qualifications(true),
                () -> pages.cases(false), () -> pages.audit(null)};
        for (int i = 0; i < labels.length; i++) {
            JButton button = CommerceTheme.button(labels[i], actions[i]);
            if (labels[i].equals(section)||products&&i==0) CommerceTheme.primary(button);
            tabs.add(button);
        }
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);
        content.add(tabs, BorderLayout.NORTH);
        content.add(main);
        JPanel shell = new JPanel(new BorderLayout(22, 0));
        shell.setOpaque(false);
        shell.add(side, BorderLayout.WEST);
        shell.add(content);
        return shell;
    }

    static JPanel table(JTable table) {
        JPanel card = CommerceTheme.card(Color.WHITE, 18);
        card.setLayout(new BorderLayout());
        card.add(CommerceTheme.scroll(table));
        return card;
    }

    static void badges(JTable table, int column) {
        table.getColumnModel().getColumn(column).setCellRenderer((grid, value, selected, focused, row, col) -> {
            JPanel cell=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,10,10));
            cell.setBackground(selected?grid.getSelectionBackground():Color.WHITE);
            JPanel pill=CommerceTheme.card(new Color(0xedf3e9),5);
            pill.setLayout(new BorderLayout());
            pill.add(CommerceTheme.muted(String.valueOf(value)));
            cell.add(pill);
            return cell;
        });
    }
}
