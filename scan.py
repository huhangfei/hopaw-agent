t=open(r'D:\other\hopaw-agent\doc_text.txt',encoding='utf-8').read()
lines=t.split('\n')
out=[]
for i,l in enumerate(lines,1):
    if any(k in l for k in ['临时','方案','配时','启动','调度']):
        out.append(f"{i}: {l.strip()[:70]}")
open(r'D:\other\hopaw-agent\scan.txt','w',encoding='utf-8').write('\n'.join(out))
print('written', len(out))
