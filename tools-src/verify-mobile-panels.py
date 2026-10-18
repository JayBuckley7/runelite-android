# Compile the production compositor against small API doubles, then exercise visibility transitions.
from pathlib import Path
import os, subprocess
root = Path('build/verify-mobile-panels')
sources = {
'javax/inject/Inject.java': 'package javax.inject; public @interface Inject {}',
'net/runelite/api/Client.java': 'package net.runelite.api; public interface Client { boolean isGpu(); boolean isResized(); BufferProvider getBufferProvider(); net.runelite.api.widgets.Widget getWidget(int id); }',
'net/runelite/api/BufferProvider.java': 'package net.runelite.api; public interface BufferProvider { int getWidth(); int getHeight(); int[] getPixels(); }',
'net/runelite/api/widgets/Widget.java': 'package net.runelite.api.widgets; public interface Widget { boolean isHidden(); java.awt.Rectangle getBounds(); }',
'net/runelite/api/gameval/InterfaceID.java': 'package net.runelite.api.gameval; public class InterfaceID { public static class ToplevelOsrsStretch { public static final int SIDE_MENU=1, CHAT_CONTAINER=2, MAP_CONTAINER=3; } public static class ToplevelPreEoc { public static final int SIDE_CONTAINER=4, CHAT_CONTAINER=5, MAP_CONTAINER=6, SIDE_STATIC_LAYER=7, SIDE_MOVABLE_LAYER=8; } }',
'net/runelite/client/callback/ClientThread.java': 'package net.runelite.client.callback; public class ClientThread { public java.util.List<Runnable> pending=new java.util.ArrayList<>(); public void invokeLater(Runnable r) { pending.add(r); } public void flush() { for(Runnable r:pending) r.run(); pending.clear(); } }',
'net/runelite/client/config/ConfigManager.java': 'package net.runelite.client.config; public class ConfigManager { public java.util.Map<String,Integer> values=new java.util.HashMap<>(); public void setConfiguration(String g,String k,int v) { values.put(k,v); } }',
'net/runelite/client/ui/overlay/Overlay.java': 'package net.runelite.client.ui.overlay; public abstract class Overlay { public void setPosition(OverlayPosition p){} public void setLayer(OverlayLayer l){} public void setPriority(float p){} public abstract java.awt.Dimension render(java.awt.Graphics2D g); }',
'net/runelite/client/ui/overlay/OverlayPosition.java': 'package net.runelite.client.ui.overlay; public enum OverlayPosition { DYNAMIC }',
'net/runelite/client/ui/overlay/OverlayLayer.java': 'package net.runelite.client.ui.overlay; public enum OverlayLayer { ABOVE_WIDGETS }',
'net/runelite/client/plugins/mobile/MobileHitTest.java': 'package net.runelite.client.plugins.mobile; public class MobileHitTest { public static boolean isMenuAt(int x,int y){return false;} public static boolean isMenuOptionAt(int x,int y){return false;} }',
'net/runelite/client/plugins/mobile/MobileConfig.java': 'package net.runelite.client.plugins.mobile; public interface MobileConfig { String GROUP="mobile"; int inventoryScale(); int chatScale(); int minimapScale(); int staticTabsScale(); int movableTabsScale(); }',
'VerifyMobilePanels.java': '''
import java.awt.Rectangle;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.mobile.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
public class VerifyMobilePanels {
 static void check(boolean b,String s) { if(!b) throw new AssertionError(s); }
 static class W implements Widget { boolean hidden; Rectangle bounds; W(int x,int y,int w,int h){bounds=new Rectangle(x,y,w,h);} public boolean isHidden(){return hidden;} public Rectangle getBounds(){return bounds;} }
 static class C implements Client,BufferProvider { Map<Integer,W> widgets=new HashMap<>(); int[] pixels=new int[800*800]; public boolean isGpu(){return true;} public boolean isResized(){return true;} public BufferProvider getBufferProvider(){return this;} public Widget getWidget(int id){return widgets.get(id);} public int getWidth(){return 800;} public int getHeight(){return 800;} public int[] getPixels(){return pixels;} }
 public static void main(String[] args) {
  C c=new C(); W body=new W(600,300,100,150), first=new W(400,700,100,20), second=new W(600,700,100,20);
  c.widgets.put(4,body);c.widgets.put(7,first);c.widgets.put(8,second);
  ConfigManager cm=new ConfigManager(); cm.values.put("inventoryScale",175);cm.values.put("staticTabsScale",150);cm.values.put("movableTabsScale",200);
  MobileConfig config=new MobileConfig(){ public int inventoryScale(){return cm.values.get("inventoryScale");} public int chatScale(){return 100;} public int minimapScale(){return 100;} public int staticTabsScale(){return cm.values.get("staticTabsScale");} public int movableTabsScale(){return cm.values.get("movableTabsScale");} };
  ClientThread ct=new ClientThread(); MobileInventoryOverlay o=new MobileInventoryOverlay(c,config,ct,cm);
  o.render(null); int[] before=MobileInventoryOverlay.mapInput(365,695,800,800);
  check(before[0]>=400 && before[0]<500,"scaled first row not selectable");
  body.hidden=true;o.validateVisibility();o.render(null);
  check(Arrays.equals(before,MobileInventoryOverlay.mapInput(365,695,800,800)),"tab size changed on inventory close");
  MobileInventoryOverlay.Resize resize=MobileInventoryOverlay.beginResize(365,695,800,800);
  check(resize!=null,"closed inventory prevented tab pinch");resize.update(1.2f);resize.end();ct.flush();
  check(cm.values.get("staticTabsScale")==180,"tab scale not persisted");
  check(cm.values.get("movableTabsScale")==200 && cm.values.get("inventoryScale")==175,"pinch changed another piece");
  body.hidden=false;o.render(null);
  int[] reopened=MobileInventoryOverlay.mapInput(335,690,800,800);
  check(reopened[0]>=400 && reopened[0]<500,"reopen lost saved tab scale");
  MobileInventoryOverlay.clear();o.render(null);
  check(Arrays.equals(reopened,MobileInventoryOverlay.mapInput(335,690,800,800)),"clearing render state lost scale");
  resize=MobileInventoryOverlay.beginResize(335,690,800,800);resize.update(1.1f);
  first.hidden=true;o.validateVisibility();ct.flush();
  check(cm.values.get("staticTabsScale")==198,"hiding a resizing piece discarded its scale");
  first.hidden=false;o.render(null);
  check(MobileInventoryOverlay.beginResize(310,690,800,800)!=null,"hidden piece failed to restore");
  System.out.println("PASS: production compositor preserves independent tab scales across inventory close/reopen, render reset, and mid-pinch visibility changes");
 }
}
'''
}
for name, content in sources.items():
 p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
java=Path(os.environ['JAVA_HOME'])/'bin'
production=Path('android/src/androidMain/java/net/runelite/client/plugins/mobile')
subprocess.run([str(java/'javac.exe'),'-d',str(root/'classes'),*[str(root/n) for n in sources],str(production/'MobileInventoryOverlay.java'),str(production/'InventoryScaleGeometry.java')],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(root/'classes'),'VerifyMobilePanels'],check=True)
