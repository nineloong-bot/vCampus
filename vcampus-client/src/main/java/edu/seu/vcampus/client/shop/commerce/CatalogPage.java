package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/** Prototype catalog layout with native multi-selection and fixed pagination. */
final class CatalogPage {
    private final CommercePanel ui;
    private int page=1, pageSize=12, total, loadSequence;
    private boolean search, shops;
    private String keyword="", shopId, sort="DEFAULT";
    private Runnable source;
    private JPanel body, pages;
    private JLabel counter, totalLabel, selection, resultTitle;
    private JList<Product> products;
    private JButton prev, next, clear;
    CatalogPage(CommercePanel ui) { this.ui=ui;source=ui::home; }
    void open() { render(); }
    void search() { search(ui::home); }
    void search(Runnable previous) { source=previous;search=true;render(); }
    void shop(String id) { shopId=id;render(); }
    private void render() {
        body=new JPanel(new BorderLayout());body.setOpaque(false);
        JPanel top=ProductArt.stack(0);
        JPanel intro=ProductArt.stack(0);intro.setBorder(BorderFactory.createEmptyBorder(27,0,21,0));
        JLabel eyebrow=CommerceTheme.muted("VCAMPUS / MARKETPLACE");eyebrow.setFont(eyebrow.getFont().deriveFont(10f));intro.add(eyebrow);
        intro.add(Box.createVerticalStrut(8));
        JLabel title=CommerceTheme.heading(search?"想找的好物，就在这里。":shopId==null?"把校园生活，装进购物车。":"店铺商品",29);
        intro.add(title);intro.add(Box.createVerticalStrut(6));
        intro.add(CommerceTheme.muted(search?"搜索商品或店铺，发现更多校园好物。":shopId==null?"发现身边的好物，逛逛同学们的小店。":"欢迎进店，看看今天有什么新发现。"));top.add(intro);
        if(search) top.add(searchTools());
        JPanel toolbar=new JPanel(new BorderLayout());toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(0xdce3dc)),BorderFactory.createEmptyBorder(16,0,9,0)));
        resultTitle=CommerceTheme.heading(shops?"店铺搜索":search?"商品搜索结果":shopId==null?"全部商品":"店内商品",17);
        totalLabel=CommerceTheme.muted("");toolbar.add(compact(resultTitle,totalLabel),BorderLayout.WEST);
        String[] labels={"默认排序","价格从低到高","价格从高到低","销量从高到低","销量从低到高"};
        String[] codes={"DEFAULT","PRICE_ASC","PRICE_DESC","SALES_DESC","SALES_ASC"};
        JComboBox<String> sorter=new JComboBox<>(labels);
        for(int i=0;i<codes.length;i++)if(codes[i].equals(sort))sorter.setSelectedIndex(i);
        sorter.addActionListener(e->{sort=codes[sorter.getSelectedIndex()];page=1;load();});
        if(!shops)toolbar.add(compact(CommerceTheme.muted("排序"),sorter),BorderLayout.EAST);top.add(toolbar);
        selection=CommerceTheme.muted("点击选择 · Shift 连选 · Ctrl 多选 · 双击查看详情");
        clear=CommerceTheme.button("清空选择",()->{if(products!=null)products.clearSelection();});clear.setEnabled(false);
        clear.putClientProperty("commerce.nav",true);clear.setBorder(BorderFactory.createEmptyBorder(3,7,3,7));
        clear.setFont(CommerceTheme.font(Font.PLAIN,12));
        JPanel select=new JPanel(new BorderLayout());select.setOpaque(false);select.setPreferredSize(new Dimension(0,32));select.add(selection,BorderLayout.WEST);select.add(clear,BorderLayout.EAST);
        if(!shops)top.add(select);body.add(top,BorderLayout.NORTH);
        ui.display("",body,paginator(),search?source:ui::home);
        if(shopId!=null)ui.fetch("SHOP2_CATALOG_SHOP",shopId,data->{Shop shop=(Shop)data;
            title.setText(shop.name()+("SUSPENDED".equals(shop.status())?" · 暂停营业":""));
            intro.add(compact(CommerceTheme.button("举报店铺",()->new GovernancePages(ui).report("REPORT_SHOP",shop.id()))));load();});
        else load();ui.refreshCartDot();
    }
    private JPanel compact(Component... items) {
        JPanel row=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));row.setOpaque(false);
        for(Component item:items)row.add(item);return row;
    }
    private JPanel searchTools() {
        JPanel panel=CommerceTheme.form();JTextField input=new JTextField(keyword,32);
        input.putClientProperty("JTextField.placeholderText","搜索你想找的商品或店铺");
        JButton submit=CommerceTheme.primary(CommerceTheme.button("搜索",()->{keyword=input.getText();page=1;load();}));
        input.addActionListener(e->submit.doClick());panel.add(CommerceTheme.row(input,submit));
        JButton goods=CommerceTheme.button("商品",()->{keyword=input.getText();shops=false;page=1;render();});
        JButton stores=CommerceTheme.button("店铺",()->{keyword=input.getText();shops=true;page=1;render();});
        for(JButton tab:new JButton[]{goods,stores}){
            tab.putClientProperty("commerce.nav",true);
            Color underline=tab==(shops?stores:goods)?CommerceTheme.ACCENT:CommerceTheme.BACKGROUND;
            tab.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,2,0,underline),
                    BorderFactory.createEmptyBorder(7,20,7,20)));
        }
        panel.add(CommerceTheme.row(goods,stores));return panel;
    }
    private JPanel paginator() {
        JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);
        footer.setPreferredSize(new Dimension(0,68));footer.setBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(0xdbe2d9)));
        counter=CommerceTheme.muted("");footer.add(counter,BorderLayout.WEST);
        JComboBox<Integer> size=new JComboBox<>(new Integer[]{12,24,48});size.setSelectedItem(pageSize);
        size.addActionListener(e->{pageSize=(Integer)size.getSelectedItem();page=1;load();});
        prev=CommerceTheme.button("← 上一页",()->{if(page>1){page--;load();}});
        next=CommerceTheme.button("下一页 →",()->{if(page*pageSize<total){page++;load();}});
        pages=CommerceTheme.row();JPanel controls=CommerceTheme.row(CommerceTheme.muted("每页"),size,CommerceTheme.muted("件"),prev,pages,next);
        footer.add(controls,BorderLayout.EAST);return footer;
    }
    private void load() {
        int version=++loadSequence;Query query=new Query(keyword,shopId,sort,page,pageSize);
        ui.fetch(shops?"SHOP2_CATALOG_SHOPS":"SHOP2_CATALOG_LIST",query,data->{
            if(version!=loadSequence)return;
            if(shops){Shops result=(Shops)data;total=result.total();showShops(result.items());}
            else{Page result=(Page)data;total=result.total();showProducts(result.items());}
            int count=Math.max(1,(total+pageSize-1)/pageSize);
            counter.setText("共 "+total+(shops?" 家店铺":" 件商品")+" · 第 "+page+" / "+count+" 页");
            totalLabel.setText(total+(shops?" 家店铺":" 件好物"));prev.setEnabled(page>1);next.setEnabled(page<count);pages.removeAll();
            for(int i=Math.max(1,page-2);i<=Math.min(count,page+2);i++){
                int target=i;JButton button=CommerceTheme.button(""+i,()->{page=target;load();});
                if(i==page)CommerceTheme.primary(button);pages.add(button);
            }
            pages.revalidate();pages.repaint();
        });
    }
    private void replace(JComponent center) {
        Component old=((BorderLayout)body.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if(old!=null)body.remove(old);body.add(center,BorderLayout.CENTER);body.revalidate();body.repaint();
    }
    private void showShops(List<Shop> values) {
        JPanel list=CommerceTheme.form();
        for(Shop shop:values){
            JPanel card=CommerceTheme.card(Color.WHITE,27);card.setLayout(new BorderLayout(10,10));
            JPanel info=CommerceTheme.form();info.add(CommerceTheme.muted("校园店铺"));info.add(CommerceTheme.heading(shop.name(),19));
            info.add(CommerceTheme.muted(shop.description()));card.add(info,BorderLayout.CENTER);
            card.add(CommerceTheme.button("进店逛逛 →",()->new CatalogPage(ui).shop(shop.id())),BorderLayout.EAST);
            list.add(card);list.add(Box.createVerticalStrut(18));
        }
        if(values.isEmpty())list.add(CommerceTheme.muted("没有找到相关结果，试试其他关键词。"));replace(CommerceTheme.scroll(list));
    }
    private void showProducts(List<Product> values) {
        products=new JList<>(values.toArray(Product[]::new));JList<Product> list=products;list.setName("shop.product-list");
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);list.setVisibleRowCount(0);list.setFixedCellHeight(282);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);list.setBackground(CommerceTheme.BACKGROUND);list.setCellRenderer(new ProductTile());
        JScrollPane scroll=CommerceTheme.scroll(list);
        Runnable resize=()->{int width=scroll.getViewport().getWidth()>0?scroll.getViewport().getWidth():Math.max(1,ui.getWidth()-76);int columns=ui.getWidth()>=1350?5:ui.getWidth()<=550?2:ui.getWidth()<=850?3:4;list.setFixedCellWidth(Math.max(1,width/columns));};
        scroll.addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){resize.run();}});
        list.addListSelectionListener(e->{int count=list.getSelectedIndices().length;selection.setText(count==0?"点击选择 · Shift 连选 · Ctrl 多选 · 双击查看详情":"已选 "+count+" 件商品 · 右键加入购物车");clear.setEnabled(count>0);});
        JPopupMenu menu=new JPopupMenu();JMenuItem add=new JMenuItem("加入购物车（默认规格，每件1件）");menu.add(add);
        JButton operation=new JButton();add.addActionListener(e->{List<String> ids=list.getSelectedValuesList().stream().map(Product::id).toList();
            if(ids.isEmpty())return;ui.write("SHOP2_CART_BULK",new BulkAdd("",ids),operation,data->{CartResult result=(CartResult)data;
                ui.updateCart(!result.lines().isEmpty());ui.notice(result.notices().isEmpty()?"已加入购物车":String.join("；",result.notices()));});});
        list.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                int i=list.locationToIndex(e.getPoint());if(i<0||!list.getCellBounds(i,i).contains(e.getPoint()))return;
                int y=e.getY()-list.getCellBounds(i,i).y;
                if(SwingUtilities.isLeftMouseButton(e)&&y>=185&&y<=207)new CatalogPage(ui).shop(values.get(i).shopId());
                else if(e.getClickCount()==2&&SwingUtilities.isLeftMouseButton(e))new ProductPage(ui).open(values.get(i).id());
            }
            private void popup(MouseEvent e){if(e.isPopupTrigger()){int i=list.locationToIndex(e.getPoint());if(i>=0&&!list.isSelectedIndex(i))list.setSelectedIndex(i);menu.show(list,e.getX(),e.getY());}}
            @Override public void mousePressed(MouseEvent e){popup(e);}
            @Override public void mouseReleased(MouseEvent e){popup(e);}
        });replace(scroll);resize.run();
        selection.setText("点击选择 · Shift 连选 · Ctrl 多选 · 双击查看详情");clear.setEnabled(false);
        if(values.isEmpty())ui.notice("没有找到相关结果，试试其他关键词。");
    }
}
