package edu.seu.vcampus.client.shop.commerce;
import javax.swing.*;
import java.awt.*;
/** Offline artwork keyed by the server-validated preset identifier. */
final class PresetImages {
 static final String[] IDS={"","book","pen","cup","bag","shirt","box"};
 private PresetImages(){}
 static Icon icon(String id,int size){
  var resource=PresetImages.class.getResource("/shop/presets/"+id+".png");
  if(resource==null)return UIManager.getIcon("FileView.fileIcon");
  return new ImageIcon(new ImageIcon(resource).getImage().getScaledInstance(size,size,Image.SCALE_SMOOTH));
 }
}
