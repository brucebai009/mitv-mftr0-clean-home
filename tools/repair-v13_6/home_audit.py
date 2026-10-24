#!/usr/bin/env python3
"""Bounded HOME/entry audit for the recorded MFTR0. inspect/firmware never write device settings."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import time

SPEC = importlib.util.spec_from_file_location('tv_continue', Path(__file__).resolve().parents[1] / 'repair-v13_4/continue_tv.py')
BASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BASE)
IDS = BASE.IDS
TARGET = 'com.ts.launcher/com.gathin.tvhome.MainActivity'
STOCK = 'com.mitv.tvhome/.MainActivityUserMode'
ENTRY = 'com.gathin.tvhome.entry'
MAIN = 'android.intent.action.MAIN'
HOME = 'android.intent.category.HOME'
DEFAULT = 'android.intent.category.DEFAULT'

def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

def components(text):
    return re.findall(r'^\s*([a-zA-Z][\w.]+/[\w.$]+)\s*$', text, re.M)

def normalized(name):
    if not name or '/' not in name: return name
    pkg, cls = name.split('/', 1)
    return pkg+'/'+(pkg+cls if cls.startswith('.') else cls)

def resolves_target(text):
    found = components(text)
    return len(found) == 1 and normalized(found[0]) == TARGET

def classify(text):
    found = components(text)
    if len(found) != 1: return 'UNKNOWN'
    c = normalized(found[0])
    if c == TARGET: return 'COMPAT'
    if 'ResolverActivity' in c or 'ChooserActivity' in c: return 'CHOOSER'
    if c.startswith('com.mitv.tvhome/'): return 'STOCK'
    return 'OTHER'

def matrix(tv, prefix):
    results = {}
    variants = (
        ('home', ['-c', HOME]),
        ('home-default-category', ['-c', HOME, '-c', DEFAULT]),
        ('home-match-default-only', ['--query-flags', '65536', '-c', HOME]),
    )
    for name, flags in variants:
        args = ['--brief', '--user', '0', '-a', MAIN, *flags]
        text, code, _ = tv.shell(prefix+'-'+name+'-resolve', 'cmd', 'package', 'resolve-activity', *args)
        candidates, candidates_code, _ = tv.shell(prefix+'-'+name+'-candidates', 'cmd', 'package', 'query-activities', *args)
        results[name] = dict(resolver=text, returncode=code, classification=classify(text),
                             candidates=candidates, candidatesReturncode=candidates_code)
    tv.shell(prefix+'-preferred', 'dumpsys', 'package', 'preferred-xml', '--full', timeout=20)
    tv.shell(prefix+'-home-role', 'cmd', 'role', 'get-role-holders', '--user', '0', 'android.app.role.HOME')
    tv.shell(prefix+'-activities', 'dumpsys', 'activity', 'activities')
    save(tv.out/(prefix+'-matrix.json'), results)
    return results

def inspect(tv):
    tv.shell('package-command-help', 'cmd', 'package', 'help')
    tv.shell('role-command-help', 'cmd', 'role', 'help')
    results = matrix(tv, 'inspect')
    for pkg in (*IDS, ENTRY, 'com.mitv.tvhome', 'com.xiaomi.mitv.appstore'):
        tv.shell('package-'+pkg, 'dumpsys', 'package', pkg)
    for cat in ('LAUNCHER', 'LEANBACK_LAUNCHER'):
        for pkg in (*IDS, ENTRY):
            tv.shell('entry-'+pkg+'-'+cat, 'cmd', 'package', 'query-activities', '--brief', '--user', '0',
                     '-a', MAIN, '-c', 'android.intent.category.'+cat, '-p', pkg)
    tv.shell('disabled', 'pm', 'list', 'packages', '-d', '--user', '0')
    tv.shell('enabled-accessibility', 'settings', '--user', '0', 'get', 'secure', 'enabled_accessibility_services')
    tv.shell('accessibility-state', 'dumpsys', 'accessibility')
    tv.snapshot('inspect-screen', hierarchy=False)
    return dict(status='READ_ONLY_COLLECTED', resolverMatrix=results,
                conclusion='A resolver mismatch alone does not prove a firmware whitelist rejection.')

def probe(tv):
    BASE.require_helpers_off(tv, 'com.ts.launcher')
    before = matrix(tv, 'before')
    candidates = before['home']['candidates']
    names = {normalized(x) for x in components(candidates)}
    if normalized(STOCK) not in names:
        return dict(status='NO_VERIFIED_STOCK_RECOVERY_COMPONENT', devicePreferenceChanged=False)
    if TARGET not in names:
        return dict(status='COMPAT_NOT_IN_HOME_CANDIDATES', devicePreferenceChanged=False)
    previous = components(before['home']['resolver'])
    allowed = {TARGET, 'com.gathin.tvhome/com.gathin.tvhome.MainActivity', normalized(STOCK)}
    restore = previous[0] if len(previous) == 1 and normalized(previous[0]) in allowed else STOCK
    selected = False
    changed = False
    try:
        # Mark before the call: a timeout may happen after the device changed the preference.
        changed = True
        tv.shell('select-compat', 'cmd', 'package', 'set-home-activity', '--user', '0', TARGET, required=True)
        after = matrix(tv, 'after-set')
        # Capture the real implicit HOME result even when the CLI query reports a chooser.
        tv.shell('home-event', 'input', 'keyevent', '3', required=True)
        time.sleep(2)
        frame = tv.snapshot('actual-home', hierarchy=True)
        tv.shell('home-events', 'logcat', '-b', 'events', '-d', '-t', '250', timeout=15)
        late = matrix(tv, 'after-home')
        selected = (all(x['returncode'] == 0 and resolves_target(x['resolver']) for x in late.values())
                    and frame['stableComponent'] and normalized(frame['afterComponent']) == TARGET
                    and frame['windowContainsResumedComponent'] and frame['validPng'])
        return dict(status='SYNTHETIC_HOME_CANDIDATE' if selected else 'HOME_NOT_SELECTED_OR_QUERY_DISAGREEMENT',
                    afterSet=after, afterHome=late, screen=frame,
                    restoredOnFailure=not selected, physicalHome='NOT_TESTED', naturalBoot='NOT_TESTED',
                    whitelistRejected='NOT_PROVEN_BY_RESOLVER_RESULT')
    finally:
        if changed and not selected:
            tv.shell('restore-home', 'cmd', 'package', 'set-home-activity', '--user', '0', restore, required=True)
            tv.shell('restore-home-event', 'input', 'keyevent', '3', required=True)
            matrix(tv, 'after-restore')

def open_desktop(tv):
    # A recovery/demo entry, explicitly not a HOME takeover test.
    text, code, _ = tv.shell('open-compat', 'am', 'start', '-W', '-n', TARGET, required=True)
    time.sleep(2)
    snap = tv.snapshot('opened-desktop', hierarchy=False)
    ok = snap['stableComponent'] and normalized(snap['afterComponent']) == TARGET
    return dict(status='DIRECT_OPEN_CAPTURED' if ok else 'DIRECT_OPEN_FOREGROUND_MISMATCH',
                defaultHomeChanged=False, nativeHomeVerified=False, screenshot=snap)


def framework_name(name):
    return bool(re.fullmatch(r'[A-Za-z0-9_.-]+\.jar', name)
                and (name in ('services.jar', 'framework.jar') or 'mitv' in name.lower()))

def firmware(tv):
    # Fixed small reads. Permission errors are evidence, not a reason to root/chmod/remount.
    for name in ('ro.build.fingerprint', 'ro.build.version.sdk', 'ro.build.version.release',
                 'ro.product.device', 'persist.sys.system_str'):
        tv.shell('property-'+name, 'getprop', name)
    tv.shell('appcontrol-properties', 'getprop | grep -E "^\\[(persist\\.appcontrol[^]]*|persist\\.sys\\.system_str)\\]"')
    for table, key in (('system', 'str_killbackgroud_pkg_white_list'),
                       ('global', 'disable_back_home_whitelist')):
        tv.shell('setting-'+key, 'settings', '--user', '0', 'get', table, key)
    for name in ('/data/system/mitv_features.xml', '/system/etc/mitv_features.xml',
                 '/vendor/etc/mitv_features.xml', '/product/etc/mitv_features.xml'):
        tv.shell('whitelist-'+name.replace('/', '_'), 'cat', name)
    jars = []
    for folder in ('/system/framework', '/system_ext/framework', '/product/framework', '/vendor/framework'):
        listing, code, _ = tv.shell('list-'+folder.replace('/', '_'), 'ls', '-1', folder)
        if code == 0:
            jars.extend(folder+'/'+name for name in listing.splitlines() if framework_name(name))
    records = []; total = 0
    for remote in jars[:8]:
        size_text, code, _ = tv.shell('size-'+str(len(records)), 'stat', '-c', '%s', remote)
        if code != 0 or not size_text.strip().isdigit():
            records.append(dict(remote=remote, status='SIZE_UNAVAILABLE')); continue
        size = int(size_text.strip())
        if size > 32*1024*1024 or total+size > 96*1024*1024:
            records.append(dict(remote=remote, size=size, status='SIZE_LIMIT')); continue
        local = tv.out / ('framework-'+remote.strip('/').replace('/', '_'))
        _, code, _ = tv.run('pull-'+str(len(records)), ['pull', remote, str(local)], timeout=45)
        ok = code == 0 and local.is_file() and local.stat().st_size == size
        records.append(dict(remote=remote, local=local.name, size=size, status='PULLED' if ok else 'PULL_FAILED',
                            sha256=hashlib.sha256(local.read_bytes()).hexdigest() if ok else None))
        if ok: total += size
    save(tv.out/'framework-files.json', records)
    return dict(status='READ_ONLY_FIRMWARE_COLLECTED', files=records,
                note='Analyze actual DEX and whitelist call sites. An empty/odex-only JAR is not evidence of absent filtering.')

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', type=Path, required=True)
    p.add_argument('--serial', required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--phase', choices=('inspect', 'probe', 'firmware', 'open'), required=True)
    p.add_argument('--execute', action='store_true')
    a = p.parse_args()
    if a.phase in ('probe', 'open') and not a.execute: p.error('probe/open require --execute')
    if a.out.exists(): p.error('Use a new output directory')
    a.out.mkdir(parents=True)
    tv = BASE.TV(a.adb, a.serial, a.out)
    try:
        tv.verify()
        result = {'inspect': inspect, 'probe': probe, 'firmware': firmware, 'open': open_desktop}[a.phase](tv)
        save(a.out/'result.json', result)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except Exception as e:
        save(a.out/'result.json', dict(status='ERROR', error=str(e)))
        raise

if __name__ == '__main__': main()
