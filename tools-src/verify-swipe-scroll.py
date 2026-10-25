"""Replay generic game-widget swipe routing without Android."""
from pathlib import Path
import os
import subprocess

root = Path('build/verify-swipe-scroll')
root.mkdir(parents=True, exist_ok=True)
source = root / 'VerifySwipeScroll.java'
source.write_text(r'''package net.runelite.client.plugins.mobile;
import java.awt.Rectangle;
public class VerifySwipeScroll {
 static void check(boolean ok) { if(!ok)throw new AssertionError(); }
 public static void main(String[] args) {
  Object id=new Object(); int[] moved={0};
  Rectangle bounds=new Rectangle(0,0,200,300);
  MobileSwipeScroll.Target t=new MobileSwipeScroll.Target(id,bounds,new Rectangle(190,0,10,300),
    new Rectangle[]{new Rectangle(0,0,100,100)},2,v->moved[0]=v);
  MobileSwipeScroll.publish(new MobileSwipeScroll.Target[]{t});
  check(MobileSwipeScroll.begin(50,50,0,20,180)==null);
  check(MobileSwipeScroll.begin(50,50,20,10,0)==null);
  check(MobileSwipeScroll.begin(195,50,0,20,0)==null);
  check(MobileSwipeScroll.begin(220,50,0,20,0)==null);
  MobileSwipeScroll.Gesture g=MobileSwipeScroll.begin(50,50,0,20,50);
  check(g!=null); g.move(90); check(moved[0]==-20); g.move(30); check(moved[0]==10);
  g.end(); g.move(100); check(moved[0]==10);
  g=MobileSwipeScroll.begin(50,150,0,20,300); check(g!=null);
  MobileSwipeScroll.publish(new MobileSwipeScroll.Target[0]);
  g.move(200); check(moved[0]==10);
  MobileSwipeScroll.publish(new MobileSwipeScroll.Target[]{t});
  g.move(250); check(moved[0]==10); // cancelled session cannot revive
  int[] child={0};
  MobileSwipeScroll.Target inner=new MobileSwipeScroll.Target(new Object(),new Rectangle(0,0,80,80),null,
    new Rectangle[0],1,v->child[0]=v);
  MobileSwipeScroll.publish(new MobileSwipeScroll.Target[]{inner,t});
  g=MobileSwipeScroll.begin(40,40,0,20,0); g.move(60); check(child[0]==-20 && moved[0]==10);
  MobileSwipeScroll.publish(new MobileSwipeScroll.Target[]{new MobileSwipeScroll.Target(id,new Rectangle(5,0,200,300),null,new Rectangle[0],2,v->moved[0]=v)});
  g.move(80); check(child[0]==-20);
  System.out.println("PASS: natural direction, scale, nested lists, item holds, horizontal drags, scrollbar exclusion and stale gesture cancellation");
 }
}
''', encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
suffix = '.exe' if os.name == 'nt' else ''
production = Path('android/src/androidMain/java/net/runelite/client/plugins/mobile/MobileSwipeScroll.java')
subprocess.run([str(java / ('javac' + suffix)), '-d', str(root), str(production), str(source)], check=True)
subprocess.run([str(java / ('java' + suffix)), '-cp', str(root), 'net.runelite.client.plugins.mobile.VerifySwipeScroll'], check=True)
