from PIL import Image, ImageDraw, ImageFilter
import numpy as np
import os
SRC=os.path.join(os.path.dirname(__file__), 'logo-a-kaynak.png')
OUT=os.path.join(os.path.dirname(__file__), '..', 'public', 'brand')
im=Image.open(SRC).convert('RGB')
x0,y0,x1,y1=15,5,1231,1191
s=1024/(x1-x0); H=round((y1-y0)*s); oy=(1024-H)//2
tile=im.crop((x0,y0,x1,y1)).resize((1024,H),Image.LANCZOS)
canvas=Image.new('RGB',(1024,1024),(1,4,16)); canvas.paste(tile,(0,oy))
# tile mask from source: inside = blue channel > 34, holes filled by scanline
a=np.asarray(im).astype(int)
inside=a[...,2]>34
m=np.zeros_like(inside)
for y in range(a.shape[0]):
    xs=np.where(inside[y])[0]
    if len(xs)>20: m[y,xs.min():xs.max()+1]=True
mask=Image.fromarray((m*255).astype(np.uint8)).crop((x0,y0,x1,y1)).resize((1024,H),Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.8))
M=Image.new('L',(1024,1024),0); M.paste(mask,(0,oy))
t=canvas.convert('RGBA'); t.putalpha(M)
t.save(os.path.join(os.path.dirname(__file__), 'logo-a-1024.png'),optimize=True)
for n in (512,256,180,64,32):
    r=t.resize((n,n),Image.LANCZOS)
    if n<=64: r=r.filter(ImageFilter.UnsharpMask(radius=0.6,percent=50,threshold=1))
    r.save(f'{OUT}/logo-a-{n}.png',optimize=True)
# symbol keying
A=np.asarray(canvas).astype(float)
bg=np.array([1,18,68.])
d=np.sqrt(((A-bg)**2).sum(-1))
al=np.clip((d-30)/(120-30),0,1); al=al*al*(3-2*al)
yy,xx=np.mgrid[0:1024,0:1024]
edge=np.minimum(np.minimum(xx,yy-oy),np.minimum(1023-xx,oy+H-1-yy))
rm=Image.new('L',(1024,1024),0);ImageDraw.Draw(rm).rounded_rectangle((88,88+oy-12,936,936-oy+12),radius=205,fill=255)
al*=np.asarray(rm.filter(ImageFilter.GaussianBlur(14))).astype(float)/255
col=np.clip((A-bg*(1-al[...,None]))/np.maximum(al[...,None],1e-3),0,255)
sym=np.dstack([col,al*255]).astype(np.uint8)
symI=Image.fromarray(sym,'RGBA'); symI.save(os.path.join(os.path.dirname(__file__), 'logo-a-symbol-1024.png'),optimize=True)
symI.resize((512,512),Image.LANCZOS).save(f'{OUT}/logo-a-symbol-512.png',optimize=True)
# pen split
P=[(1068,312),(1100,322),(1106,350),(1094,450),(1037,620),(978,728),(972,792),(932,822),(882,902),(826,998),(803,992),(806,880),(816,828),(840,758),(858,718),(903,618),(968,448),(1038,338)]
P=[((x-x0)*s,(y-y0)*s+oy) for x,y in P]
pm=Image.new('L',(1024,1024),0);ImageDraw.Draw(pm).polygon(P,fill=255);pm=pm.filter(ImageFilter.MaxFilter(7))
mk=np.asarray(pm)>127
S_=np.asarray(symI).astype(float)/255; pre=S_.copy(); pre[...,:3]*=pre[...,3:4]
ys,xs=np.where(mk); by0,by1,bx0,bx1=ys.min()-4,ys.max()+5,xs.min()-4,xs.max()+5
sub=pre[by0:by1,bx0:bx1].copy(); sm=mk[by0:by1,bx0:bx1]
# coarse init: mean of boundary ring
sub[sm]=0
for it in range(3000):
    nb=(np.roll(sub,1,0)+np.roll(sub,-1,0)+np.roll(sub,1,1)+np.roll(sub,-1,1))/4
    sub[sm]=nb[sm]
pre[by0:by1,bx0:bx1]=sub
al2=np.clip(pre[...,3:4],1e-4,1); paper=np.dstack([np.clip(pre[...,:3]/al2,0,1),pre[...,3]])
Image.fromarray((paper*255).astype(np.uint8),'RGBA').resize((512,512),Image.LANCZOS).save(f'{OUT}/logo-a-paper.png',optimize=True)
pen=np.asarray(symI).copy(); soft=np.asarray(pm.filter(ImageFilter.GaussianBlur(1.5))).astype(float)/255
pen[...,3]=(pen[...,3]*soft).astype(np.uint8)
Image.fromarray(pen,'RGBA').resize((512,512),Image.LANCZOS).save(f'{OUT}/logo-a-pen.png',optimize=True)
