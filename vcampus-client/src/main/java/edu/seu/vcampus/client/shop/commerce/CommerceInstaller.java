package edu.seu.vcampus.client.shop.commerce;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.common.user.UserRole;
import javax.swing.*;
import java.awt.*;
/** Connects the approved commerce interface to the real authenticated application shell. */
public final class CommerceInstaller {
 private CommerceInstaller() { }
 /** Replaces the shop placeholder while preserving shell navigation and session ownership. */
 public static void install(MainFrame frame,UserView user,ClientConnection connection) {
  if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT required");
  Container host=(Container)find(frame,"page.shop");AbstractButton entry=(AbstractButton)find(frame,"navigation.shop");
  if(host==null||entry==null)throw new IllegalStateException("Shop shell entry missing");
  boolean admin=user.role()!=UserRole.STUDENT&&user.role()!=UserRole.TEACHER;
  CommercePanel page=new CommercePanel(new SocketCommerceTransport(connection),admin);
  if(host instanceof JComponent panel){panel.setBorder(BorderFactory.createEmptyBorder());panel.setBackground(CommerceTheme.BACKGROUND);}
  host.removeAll();host.add(page,BorderLayout.CENTER);entry.addActionListener(e->page.home());host.revalidate();host.repaint();
 }
 private static Component find(Component component,String name){
  if(name.equals(component.getName()))return component;
  if(component instanceof Container c)for(Component child:c.getComponents()){Component found=find(child,name);if(found!=null)return found;}
  return null;
 }
}
