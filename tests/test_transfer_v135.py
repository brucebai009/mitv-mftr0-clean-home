import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import pytest

spec=importlib.util.spec_from_file_location('transfer_v135',Path(__file__).resolve().parents[1]/'tools/repair-v13_5/migrate_launcher.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class FakeTransfer:
    last=None
    corrupt_push=False
    corrupt_target=False
    def __init__(self,adb,serial,out):
        self.out=out;self.remote={};self.target={};self.commands=[];FakeTransfer.last=self
    def verify(self):pass
    def run(self,args,*,input_file=None,output_file=None,**kwargs):
        assert input_file is None, 'Never stream binary stdin through the remote shell'
        self.commands.append(args)
        if args[0]=='push':self.remote[args[2]]=Path(args[1]).read_bytes();return b''
        if args[:2]==['shell','sha256sum']:
            return ((('0'*64) if self.corrupt_push else hashlib.sha256(self.remote[args[2]]).hexdigest())+'  file').encode()
        if args[:3]==['shell','wc','-c']:return str(len(self.remote[args[3]])).encode()
        if args[:2]==['shell','-T']:
            uri,remote=re.findall("'([^']+)'",args[2]);self.target[uri]=self.remote[remote];return b''
        if args[0]=='exec-out':
            data=self.target[args[-1]]
            output_file.write_bytes(data[:-1] if self.corrupt_target else data);return b''
        if '--method' in args:return b'Result: Bundle[{accepted=true}]'
        return b''

def setup(tmp_path,monkeypatch):
    source=tmp_path/'source';source.mkdir();p=source/'media/videos/clip.mp4';p.parent.mkdir(parents=True)
    data=bytes(range(256))*600;p.write_bytes(data)
    state={'schema':1,'source':m.SOURCE,'target':m.TARGET,'preferences':{k:{} for k in ('launcher_settings','wallpaper_rotation','last_opened')},'privateFiles':{},'media':[{'path':'videos/clip.mp4','size':len(data),'sha256':hashlib.sha256(data).hexdigest()}]}
    meta=source/'state.json';meta.write_text(json.dumps(state))
    (source/'COMPLETE.json').write_text(json.dumps({'sha256':m.digest(meta),'files':1}))
    adb=tmp_path/'adb';adb.touch()
    monkeypatch.setattr(m,'Transfer',FakeTransfer)
    monkeypatch.setattr(FakeTransfer,'corrupt_push',False);monkeypatch.setattr(FakeTransfer,'corrupt_target',False)
    monkeypatch.setattr(sys,'argv',['transfer','import','--adb',str(adb),'--serial','tv','--out',str(tmp_path/'out'),'--source',str(source),'--execute','--resume-metadata-sha256','a'*64])
    return source

def test_binary_transfer_checks_push_and_provider_readback_before_apply(tmp_path,monkeypatch):
    src=setup(tmp_path,monkeypatch);m.main();tv=FakeTransfer.last
    assert tv.target['content://com.ts.launcher.theme/videos/clip.mp4']==(src/'media/videos/clip.mp4').read_bytes()
    assert any('resume_sha256:s:'+'a'*64 in c for c in tv.commands)
    assert any('apply' in c for c in tv.commands)
    assert (tmp_path/'out/COMPLETE.json').is_file()

@pytest.mark.parametrize('where',['corrupt_push','corrupt_target'])
def test_corruption_never_reaches_preference_apply(tmp_path,monkeypatch,where):
    setup(tmp_path,monkeypatch);monkeypatch.setattr(FakeTransfer,where,True)
    with pytest.raises(RuntimeError):m.main()
    assert not any('apply' in c for c in FakeTransfer.last.commands)
    assert not (tmp_path/'out/COMPLETE.json').exists()

def test_unsafe_target_uri_rejected_before_any_write(tmp_path):
    tv=FakeTransfer(None,None,tmp_path)
    with pytest.raises(ValueError):m.write_verified(tv,"content://com.ts.launcher.theme/a'; id",tmp_path/'none','/tmp/test',0)
    assert tv.commands==[]
