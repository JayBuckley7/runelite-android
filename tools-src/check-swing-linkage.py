"""Report missing AWT/Swing symbols in plugin JARs; this does not validate runtime behavior.

Usage: python tools-src/check-swing-linkage.py PLUGIN.jar --classpath AWT.jar --classpath ANDROID.jar
"""
import argparse,struct,zipfile,pathlib
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('plugin')
parser.add_argument('--classpath', action='append', required=True)
parser.add_argument('--output')
args=parser.parse_args()
class C:
 def __init__(self,b):
  self.b=b;self.i=8
  n=self.u2();p=[None]*n;k=1
  while k<n:
   t=self.u1()
   if t==1: l=self.u2();p[k]=b[self.i:self.i+l].decode('utf8','replace');self.i+=l
   elif t in (7,8,16,19,20):p[k]=(t,self.u2())
   elif t in (9,10,11,12,18,17):p[k]=(t,self.u2(),self.u2())
   elif t in (3,4):self.i+=4
   elif t in (5,6):self.i+=8;k+=1
   elif t==15:self.i+=3
   else:raise ValueError(t)
   k+=1
  self.p=p;self.u2();self.name=p[p[self.u2()][1]];s=self.u2();self.parents=[p[p[s][1]]] if s else []
  self.parents += [p[p[self.u2()][1]] for _ in range(self.u2())]
  self.fields=self.members();self.methods=self.members()
 def u1(self):v=self.b[self.i];self.i+=1;return v
 def u2(self):v=struct.unpack_from('>H',self.b,self.i)[0];self.i+=2;return v
 def members(self):
  result=set()
  for _ in range(self.u2()):
   self.u2();name=self.p[self.u2()];desc=self.p[self.u2()];result.add((name,desc))
   for _ in range(self.u2()):self.u2();n=struct.unpack_from('>I',self.b,self.i)[0];self.i+=4+n
  return result

import json,re
z=zipfile.ZipFile(args.plugin)
classes={}
for path in args.classpath + [args.plugin]:
 with zipfile.ZipFile(path) as jar:
  for n in jar.namelist():
   if n.endswith('.class') and not n.startswith('META-INF/'):
    c=C(jar.read(n));classes.setdefault(c.name,c)
def has(owner,name,desc,field,seen=None):
 seen=set() if seen is None else seen
 if owner in seen:return False
 seen.add(owner);c=classes.get(owner)
 if c is None:return False
 if (name,desc) in (c.fields if field else c.methods):return True
 return name!='<init>' and any(has(p,name,desc,field,seen) for p in c.parents)

missing=set();missing_classes=set();count=0;refs=set()
for n in z.namelist():
 if not n.endswith('.class') or n.startswith('META-INF/'):continue
 count+=1;c=C(z.read(n));p=c.p
 for x in p:
  if isinstance(x,str):
   for name in re.findall(r'L((?:java/awt|javax/swing)/[^;<>]+);',x):
    if name not in classes:missing_classes.add((n,name))
  if not isinstance(x,tuple):continue
  if x[0]==7:
   name=p[x[1]]
   if name.startswith(('java/awt/','javax/swing/')) and name not in classes:missing_classes.add((n,name))
  if x[0] not in (9,10,11):continue
  owner=p[p[x[1]][1]];nt=p[x[2]];name,desc=p[nt[1]],p[nt[2]]
  if owner.startswith(('java/awt/','javax/swing/')):
   refs.add((owner,name,desc))
   if not has(owner,name,desc,x[0]==9):missing.add((n,owner,name,desc))
result={'pluginClassesScanned':count,'uniqueJavaMemberReferences':len(refs),'missingClasses':sorted(missing_classes),'missingMembers':sorted(missing)}
if args.output: pathlib.Path(args.output).write_text(json.dumps(result,indent=2), encoding='utf8')
print(json.dumps(result,indent=2))
