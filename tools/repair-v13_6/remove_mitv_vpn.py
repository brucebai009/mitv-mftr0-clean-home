#!/usr/bin/env python3
"""Remove only the two user-authorized, previously observed MiTV VPN apps from user 0."""
import argparse
import importlib.util
import json
from pathlib import Path
import re

SPEC = importlib.util.spec_from_file_location('home_audit', Path(__file__).with_name('home_audit.py'))
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)
TARGETS = ('com.mitvvpn.tv.mihomo.debug', 'com.mitvvpn.tv.xray.debug')

def packages(text):
    return set(re.findall(r'^package:([A-Za-z][\w.]*)\s*$', text, re.M))

def cleanup(tv, execute=False):
    before = packages(tv.shell('all-before', 'pm', 'list', 'packages', '--user', '0', required=True)[0])
    third = packages(tv.shell('third-party-before', 'pm', 'list', 'packages', '-3', '--user', '0', required=True)[0])
    installed = [pkg for pkg in TARGETS if pkg in before]
    if any(pkg not in third for pkg in installed):
        raise RuntimeError('EXPECTED_THIRD_PARTY_APP: no uninstall attempted')
    for pkg in installed:
        tv.shell('identity-'+pkg, 'dumpsys', 'package', pkg, required=True)
        paths = tv.shell('paths-'+pkg, 'pm', 'path', '--user', '0', pkg, required=True)[0].splitlines()
        if not paths or any(not line.startswith('package:/data/app/') or not line.endswith('.apk') for line in paths):
            raise RuntimeError('UNEXPECTED_APK_PATH: no uninstall attempted')
    owner = tv.shell('vpn-owner', 'settings', '--user', '0', 'get', 'secure', 'always_on_vpn_app', required=True)[0].strip()
    tv.shell('vpn-lockdown', 'settings', '--user', '0', 'get', 'secure', 'always_on_vpn_lockdown')
    tv.shell('network-before', 'dumpsys', 'connectivity', timeout=20)
    if not execute:
        return dict(status='READ_ONLY_PLAN', targets=installed, absent=[x for x in TARGETS if x not in before],
                    clearAlwaysOnForTarget=owner in installed, dataRemoval='User authorized app uninstall; no -k flag')
    # Clear only an always-on assignment owned by an app actually being removed.
    if owner in installed:
        tv.shell('disable-target-lockdown', 'settings', '--user', '0', 'put', 'secure', 'always_on_vpn_lockdown', '0', required=True)
        if tv.shell('verify-lockdown', 'settings', '--user', '0', 'get', 'secure', 'always_on_vpn_lockdown', required=True)[0].strip() != '0':
            raise RuntimeError('LOCKDOWN_NOT_CLEARED: no uninstall attempted')
        tv.shell('clear-target-always-on', 'settings', '--user', '0', 'delete', 'secure', 'always_on_vpn_app', required=True)
        if tv.shell('verify-owner', 'settings', '--user', '0', 'get', 'secure', 'always_on_vpn_app', required=True)[0].strip() not in ('null', ''):
            raise RuntimeError('ALWAYS_ON_NOT_CLEARED: no uninstall attempted')
    removed = []
    for pkg in installed:
        tv.shell('stop-'+pkg, 'am', 'force-stop', '--user', '0', pkg, required=True)
        output = tv.shell('uninstall-'+pkg, 'pm', 'uninstall', '--user', '0', pkg, timeout=30, required=True)[0]
        if output.strip() != 'Success': raise RuntimeError('UNINSTALL_NOT_CONFIRMED: '+pkg)
        state, code, _ = tv.run('adb-after-'+pkg, ['get-state'], timeout=5)
        if code != 0 or state.strip() != 'device': raise RuntimeError('ADB_DISCONNECTED_AFTER_UNINSTALL')
        removed.append(pkg)
        AUDIT.save(tv.out/'removed-so-far.json', removed)
    after = packages(tv.shell('all-after', 'pm', 'list', 'packages', '--user', '0', required=True)[0])
    unexpected = sorted((before-after)-set(installed))
    remaining = sorted(set(installed)&after)
    tv.shell('network-after', 'dumpsys', 'connectivity', timeout=20)
    tv.shell('vpn-owner-after', 'settings', '--user', '0', 'get', 'secure', 'always_on_vpn_app')
    tv.shell('vpn-lockdown-after', 'settings', '--user', '0', 'get', 'secure', 'always_on_vpn_lockdown')
    result = dict(status='REMOVED_VERIFIED' if not unexpected and not remaining else 'PACKAGE_DELTA_MISMATCH',
                  removed=removed, remainingTargets=remaining, unexpectedMissingPackages=unexpected,
                  absentBefore=[x for x in TARGETS if x not in before], internet='REQUIRES_NETWORK_AND_APP_CHECK',
                  otherMiTVVpnPackages=sorted(x for x in after if x.startswith('com.mitvvpn.') and x not in TARGETS))
    AUDIT.save(tv.out/'cleanup-summary.json', result)
    if unexpected or remaining: raise RuntimeError('PACKAGE_DELTA_MISMATCH: inspect cleanup-summary.json')
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb',type=Path,required=True);p.add_argument('--serial',required=True)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--execute',action='store_true')
    a=p.parse_args()
    if a.out.exists():p.error('Use a new output directory')
    a.out.mkdir(parents=True)
    tv=AUDIT.BASE.TV(a.adb,a.serial,a.out)
    try:
        tv.verify();result=cleanup(tv,a.execute)
        AUDIT.save(a.out/'result.json',result);print(json.dumps(result,ensure_ascii=False,indent=2))
    except Exception as e:
        AUDIT.save(a.out/'result.json',dict(status='ERROR',error=str(e)));raise

if __name__=='__main__':main()
