import importlib.util
from pathlib import Path
import pytest
P=Path(__file__).resolve().parents[1]/'tools/repair-v13_6/remove_mitv_vpn.py'
S=importlib.util.spec_from_file_location('remove_mitv_vpn',P)
M=importlib.util.module_from_spec(S);S.loader.exec_module(M)

class TV:
    def __init__(self,out,owner='unrelated.vpn',system=False):
        self.out=out;self.calls=[];self.owner=owner;self.lockdown='1';self.system=system
        self.present=set(M.TARGETS)|{'com.android.vpndialogs','com.mitv.tvhome','unrelated.vpn'}
    def run(self,label,args,**kw):return 'device',0,b''
    def shell(self,label,*args,**kw):
        self.calls.append((label,args))
        if args[:3]==('pm','list','packages'):
            data=self.present.copy()
            if '-3' in args:data=({'unrelated.vpn'} if self.system else set(M.TARGETS)|{'unrelated.vpn'})&data
            return '\n'.join('package:'+x for x in sorted(data)),0,b''
        if args[:2]==('pm','path'):return 'package:/data/app/owned/base.apk',0,b''
        if args[:2]==('pm','uninstall'):self.present.discard(args[-1]);return 'Success',0,b''
        if args[0]=='settings':
            if 'get' in args:return self.owner if args[-1]=='always_on_vpn_app' else self.lockdown,0,b''
            if 'delete' in args:self.owner='null'
            if 'put' in args:self.lockdown=args[-1]
        return '',0,b''

def writes(tv):
    return [args for _,args in tv.calls if 'uninstall' in args or 'force-stop' in args or 'put' in args or 'delete' in args]

def test_plan_has_no_device_mutations(tmp_path):
    tv=TV(tmp_path);assert M.cleanup(tv)['status']=='READ_ONLY_PLAN';assert not writes(tv)

def test_only_two_exact_apps_removed_and_other_vpn_settings_preserved(tmp_path):
    tv=TV(tmp_path);result=M.cleanup(tv,True)
    assert result['status']=='REMOVED_VERIFIED'
    assert tv.present=={'com.android.vpndialogs','com.mitv.tvhome','unrelated.vpn'}
    assert tv.owner=='unrelated.vpn' and tv.lockdown=='1'
    assert len([x for x in writes(tv) if 'uninstall' in x])==2

def test_target_lockdown_is_cleared_before_uninstall(tmp_path):
    tv=TV(tmp_path,owner=M.TARGETS[0]);M.cleanup(tv,True)
    assert tv.owner=='null' and tv.lockdown=='0'
    labels=[x[0] for x in tv.calls]
    assert labels.index('clear-target-always-on')<labels.index('uninstall-'+M.TARGETS[0])

def test_system_identity_never_uninstalled(tmp_path):
    tv=TV(tmp_path,system=True)
    with pytest.raises(RuntimeError,match='EXPECTED_THIRD_PARTY'):M.cleanup(tv,True)
    assert not writes(tv)
