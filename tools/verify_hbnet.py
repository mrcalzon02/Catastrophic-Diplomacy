import pathlib,json,hashlib
root=pathlib.Path(__file__).resolve().parents[1]
m=json.loads((root/'hbnet-vendor.json').read_text())
assert m['version']=='hbnet-1.0'
for name,expected in m['sha256'].items():
    assert pathlib.Path(name).name==name
    assert hashlib.sha256((root/m['source_root']/name).read_bytes().replace(b'\r\n',b'\n')).hexdigest()==expected, 'hbnet source drift: '+name
print('PASS hbnet checksummed source parity',len(m['sha256']))
