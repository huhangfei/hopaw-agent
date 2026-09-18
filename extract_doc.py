import olefile, struct, re, sys

path = r'D:\other\hopaw-agent\attachments\2026-09-03\497e42d74d78413b9055e38f7ba48586.doc'
ole = olefile.OleFileIO(path)
wd = ole.openstream('WordDocument').read()
flags = struct.unpack('<H', wd[0x0A:0x0C])[0]
fcClx = struct.unpack('<I', wd[0x01A2:0x01A6])[0]
lcbClx = struct.unpack('<I', wd[0x01A6:0x01AA])[0]
tblName = '1Table' if (flags & 0x0200) else '0Table'
table = ole.openstream(tblName).read()
clx = table[fcClx:fcClx+lcbClx]
pos = 0
pieces = []
while pos < len(clx):
    t = clx[pos]
    if t == 2:
        lcb = struct.unpack('<I', clx[pos+1:pos+5])[0]
        plcpcd = clx[pos+5:pos+5+lcb]
        n = (lcb - 4) // 12
        cps = [struct.unpack('<I', plcpcd[i*4:(i+1)*4])[0] for i in range(n+1)]
        for i in range(n):
            off = (n+1)*4 + i*8
            pcd = plcpcd[off:off+8]
            fc = struct.unpack('<I', pcd[2:6])[0]
            fCompressed = (fc & 0x40000000) != 0
            fcVal = fc & 0x3FFFFFFF
            cch = cps[i+1] - cps[i]
            if fCompressed:
                raw = wd[fcVal//2: fcVal//2 + cch]
                txt = raw.decode('gbk', errors='replace')
            else:
                raw = wd[fcVal: fcVal + cch*2]
                txt = raw.decode('utf-16-le', errors='replace')
            pieces.append(txt)
        break
    elif t == 1:
        cb = struct.unpack('<H', clx[pos+1:pos+3])[0]
        pos += 3 + cb
    else:
        pos += 1
text = ''.join(pieces)
text = text.replace('\r','\n').replace('\x07',' | ').replace('\x0b','\n').replace('\x0c','\n')
text = re.sub(r'[\x00-\x06\x08\x0e-\x1f]', '', text)
out = open(r'D:\other\hopaw-agent\doc_text.txt','w',encoding='utf-8')
out.write(text)
print('done', len(text))
