import importlib.util
from pathlib import Path
import struct
import zlib
import pytest

spec=importlib.util.spec_from_file_location('continue_tv',Path(__file__).resolve().parents[1]/'tools/repair-v13_4/continue_tv.py')
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
OFF='accepted=true, helper_enabled=false, boot_recovery=false, HomeOnlyAccessibilityService=2, HomeKeyService=2, HomeBootReceiver=2'

class FakeTV:
    def __init__(self,out,resolver='com.mitv.tvhome/.MainActivityUserMode',status=OFF):
        self.out=out;self.commands=[];self.answer=resolver;self.helper=status;self.prefix=['adb','-s','test-usb']
    def status(self,p):return self.helper
    def resolver(self):self.commands.append(['resolve']);return self.answer
    def run(self,label,args,**kwargs):self.commands.append(args);return ('offline',1,b'') if args==['get-state'] else ('',0,b'')
    def shell(self,label,*args,**kwargs):return self.run(label,list(args),**kwargs)


def test_does_not_reboot_when_original_home_request_was_rejected(tmp_path):
    tv=FakeTV(tmp_path)
    result=mod.home_probe(tv,'com.gathin.tvhome')
    assert result['status']=='HOME_RESOLVER_NOT_SELECTED'
    assert not any('reboot' in x or 'input' in x for x in tv.commands)
    with pytest.raises(RuntimeError,match='HOME_RESOLVER_NOT_SELECTED'):mod.natural_boot(tv,'com.gathin.tvhome')
    assert not any('reboot' in x for x in tv.commands)


def test_one_enabled_helper_blocks_native_home_probe_before_writes(tmp_path):
    tv=FakeTV(tmp_path,status=OFF.replace('HomeKeyService=2','HomeKeyService=0'))
    with pytest.raises(RuntimeError,match='HELPERS_NOT_OFF'):mod.home_probe(tv,'com.ts.launcher')
    assert tv.commands==[]


def test_offline_boot_remains_unknown_and_never_launches_desktop(tmp_path,monkeypatch):
    tv=FakeTV(tmp_path,resolver='com.ts.launcher/com.gathin.tvhome.MainActivity')
    clock=[0.0]
    monkeypatch.setattr(mod.time,'monotonic',lambda:clock[0])
    monkeypatch.setattr(mod.time,'sleep',lambda delta:clock.__setitem__(0,clock[0]+delta))
    result=mod.natural_boot(tv,'com.ts.launcher')
    assert result['status']=='BOOT_UNOBSERVED_ADB_OFFLINE'
    assert len(result['checkpoints'])==6
    assert tv.commands.count(['reboot'])==1
    assert not any('am' in x or 'input' in x for x in tv.commands)


def test_only_full_correct_home_component_is_accepted():
    assert mod.home_is('com.ts.launcher/com.gathin.tvhome.MainActivity\n','com.ts.launcher')
    assert not mod.home_is('Success','com.ts.launcher')
    assert not mod.home_is('com.ts.launcher/.SomethingElse','com.ts.launcher')
    assert not mod.home_is('com.mitv.tvhome/.MainActivityUserMode','com.ts.launcher')


def test_png_rejects_error_output_truncation_and_corrupt_crc():
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b'\x00\x00\x00\x00'))+chunk(b'IEND',b'')
    assert mod.valid_png(png)
    assert not mod.valid_png(b'adb: error: device offline')
    assert not mod.valid_png(png[:-4])
    assert not mod.valid_png(png[:22]+b'\xff'+png[23:])


def test_cpu_counter_ignores_guest_double_counting():
    assert mod.cpu_ticks('cpu 10 20 30 40 5 6 7 8 999 999\n')=={'total':126,'idle':45}
    assert mod.cpu_ticks('Permission denied') is None


def test_only_owned_process_timeout_stops_the_settings_cycle():
    line='W ActivityTaskManager: Activity pause timeout for ActivityRecord{abc u0 com.ts.launcher/com.gathin.tvhome.MainActivity}'
    assert mod.lifecycle_timeout(line,'com.ts.launcher')==line
    assert mod.lifecycle_timeout(line,'com.example.unrelated') is None
    assert mod.lifecycle_timeout('I TvHome.Lifecycle: pause end elapsedMs=3','com.ts.launcher') is None
