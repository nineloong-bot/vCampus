package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;

/** Authenticated storefront with Demo navigation, layered dialogs and asynchronous state. */
public final class CommercePanel extends JPanel {
    final CommerceTransport api;
    final boolean admin;
    private final CommerceNavigation navigation;
    private final JPanel content=new JPanel(new BorderLayout(0,14));
    private final JLabel feedback=CommerceTheme.muted(" ");
    private final JButton cart=CommerceTheme.button("购物车",()->new CartPage(this).open());
    private final Map<JButton,Pending> pending=new WeakHashMap<>();
    private final Map<String,Runnable> pendingReads=new LinkedHashMap<>();
    private final CommerceStage stage;
    private int generation;
    private Runnable back,modalReturn;
    private View view;
    private record Pending(Serializable body,String key) { }
    private record View(String title,JComponent main,JComponent footer,Runnable back,boolean modal,int width) { }
    /** Creates a role-specific workspace sharing the application's authenticated transport. */
    public CommercePanel(CommerceTransport api,boolean admin){
        super(new BorderLayout());this.api=api;this.admin=admin;setName("shop.commerce");setBackground(CommerceTheme.BACKGROUND);
        navigation=new CommerceNavigation(admin);
        if(admin){
            navigation.addLeft(CommerceTheme.button("店铺管理",()->new GovernancePages(this).shops()));
            navigation.addLeft(CommerceTheme.button("商品管理",()->new GovernancePages(this).products()));
        }else{
            navigation.addLeft(CommerceTheme.button("←",()->{if(stageModal())closeModal();else if(back!=null)back.run();else home();}));
            JButton search=CommerceTheme.button("",()->new CatalogPage(this).search(snapshot()));search.setIcon(new SearchIcon());
            search.setToolTipText("搜索商品或店铺");search.getAccessibleContext().setAccessibleName("搜索商品或店铺");navigation.addLeft(search);
            navigation.addRight(cart);navigation.addRight(CommerceTheme.button("我的",()->new AccountPages(this).open()));
            navigation.addRight(CommerceTheme.button("首页",this::home));
        }
        JPanel base=new JPanel(new BorderLayout());base.setBackground(CommerceTheme.BACKGROUND);content.setOpaque(false);
        base.add(navigation,BorderLayout.NORTH);base.add(new CommerceViewport(content),BorderLayout.CENTER);
        feedback.setBorder(BorderFactory.createEmptyBorder(3,38,5,38));base.add(feedback,BorderLayout.SOUTH);
        stage=new CommerceStage(base,this::closeModal);add(stage);CommerceStyle.apply(navigation);
    }
    private boolean stageModal(){return stage!=null&&stage.isModal();}
    /** Opens the role's default page. */
    public void home(){if(admin)new GovernancePages(this).shops();else new CatalogPage(this).open();}
    /** Visible navigation text for accessibility-oriented regression checks. */
    public String navigationText(){return labels(navigation);}
    private String labels(Container p){StringBuilder s=new StringBuilder();for(Component c:p.getComponents()){
        if(c instanceof JButton b)s.append(b.getText());else if(c instanceof Container nested)s.append(labels(nested));}return s.toString();}
    void display(String title,JComponent main,JComponent footer,Runnable previous){
        generation++;pendingReads.clear();back=previous;modalReturn=null;stage.hideDialog();
        view=new View(title,main,footer,previous,false,0);content.removeAll();
        if(!title.isBlank()){
            JLabel heading=CommerceTheme.heading(title,27);heading.setBorder(BorderFactory.createEmptyBorder(24,0,6,0));content.add(heading,BorderLayout.NORTH);
        }
        JComponent center=main;
        if(main.getLayout() instanceof FormLayout){JPanel card=CommerceTheme.card(Color.WHITE,24);card.add(main,BorderLayout.NORTH);center=CommerceTheme.scroll(card);}
        content.add(center,BorderLayout.CENTER);
        if(footer!=null){footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,CommerceTheme.LINE),BorderFactory.createEmptyBorder(10,0,10,0)));content.add(footer,BorderLayout.SOUTH);}
        notice(" ");restyle();
    }
    void modal(String title,JComponent main,JComponent footer,Runnable previous,int width){
        if(!stage.isModal())modalReturn=snapshot();
        generation++;pendingReads.clear();back=previous;view=new View(title,main,footer,previous,true,width);
        stage.showDialog(title,main,footer,width);notice(" ");restyle();
    }
    void closeModal(){
        if(!stage.isModal())return;Runnable previous=modalReturn;stage.hideDialog();modalReturn=null;
        if(previous!=null)previous.run();else home();
    }
    void notice(String text){String value=text==null||text.isBlank()?" ":text;feedback.setText(value);stage.notice(value);}
    Runnable snapshot(){
        View saved=view;Runnable background=modalReturn;java.util.List<Runnable> unfinished=java.util.List.copyOf(pendingReads.values());
        if(saved==null)return this::home;
        return ()->{
            if(saved.modal()){modal(saved.title(),saved.main(),saved.footer(),saved.back(),saved.width());modalReturn=background;}
            else display(saved.title(),saved.main(),saved.footer(),saved.back());
            unfinished.forEach(Runnable::run);
        };
    }
    void fetch(String command,Serializable body,Consumer<Serializable> done){fetch(command,body,done,()->{});}
    void fetch(String command,Serializable body,Consumer<Serializable> done,Runnable failed){
        int current=generation;String key=UUID.randomUUID().toString();notice("正在加载…");pendingReads.put(key,()->fetch(command,body,done,failed));
        api.execute(command,body,key).whenComplete((data,error)->SwingUtilities.invokeLater(()->{
            if(current!=generation)return;pendingReads.remove(key);
            if(error!=null){failed.run();failure(error);restyle();return;}notice(" ");done.accept(data);restyle();
        }));
    }
    void write(String command,Serializable body,JButton button,Consumer<Serializable> done){
        Pending action=pending.get(button);
        if(action!=null&&!Objects.equals(action.body(),body)){notice("上次操作结果未确认，请使用原内容重试");return;}
        if(action==null){action=new Pending(body,UUID.randomUUID().toString());pending.put(button,action);}
        button.setEnabled(false);int current=generation;
        api.execute(command,body,action.key()).whenComplete((data,error)->SwingUtilities.invokeLater(()->{
            button.setEnabled(true);Throwable cause=error;while(cause!=null&&cause.getCause()!=null)cause=cause.getCause();
            if(error==null||cause instanceof CommerceFailure f&&!f.code.contains("RETRY"))pending.remove(button);
            if(current!=generation)return;if(error!=null){failure(error);return;}done.accept(data);restyle();
        }));
    }
    void failure(Throwable error){
        while(error.getCause()!=null)error=error.getCause();
        if(error instanceof CommerceFailure f){String detail=CommerceMessages.explain(f.code);
            if("SHOP_CATALOG_INVALID_REQUEST".equals(f.code)&&f.getMessage()!=null)detail=f.getMessage();notice("操作未完成："+detail);
        }else notice("操作未完成：连接异常，请使用原操作重试");
    }
    private void restyle(){CommerceStyle.apply(stage);stage.revalidate();stage.repaint();}
    void updateCart(boolean nonempty){cart.setText(nonempty?"购物车 ●":"购物车");}
    boolean confirm(String text){return JOptionPane.showConfirmDialog(this,text,"请确认",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION;}
    String input(String text){return JOptionPane.showInputDialog(this,text);}
    void refreshCartDot(){api.execute("SHOP2_CART_GET",EmptyRequest.INSTANCE,UUID.randomUUID().toString()).thenAccept(data->
        SwingUtilities.invokeLater(()->{if(data instanceof edu.seu.vcampus.common.shop.catalog.CatalogDtos.CartResult r)updateCart(!r.lines().isEmpty());}));}
}
