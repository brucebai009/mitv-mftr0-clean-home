#!/usr/bin/env python3
"""Bounded MiTV-MFTR0 evidence phases. No install, scan, data deletion, or automatic reboot chain."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import zlib

IDS = ('com.gathin.tvhome', 'com.ts.launcher')
READ_PACKAGES = (*IDS, 'com.mitv.tvhome')
FINGERPRINT = 'xiaomi/freeguy/freeguy:11/RP1A.200720.011/OS2.0.5.0.RFTMATV:user/release-keys'
HELPERS = ('HomeOnlyAccessibilityService', 'HomeKeyService', 'HomeBootReceiver')
COMPONENT = '/com.gathin.tvhome.MainActivity'

def now(): return datetime.now(timezone.utc).isoformat()
def save(path, value): path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
def component(text):
    match = re.search(r'mResumedActivity:.*? u\d+ ([\w.]+/[\w.$]+)', text)
    if not match: match = re.search(r'ResumedActivity:.*? u\d+ ([\w.]+/[\w.$]+)', text)
    return match.group(1) if match else None

def valid_png(data):
    if not data.startswith(b'\x89PNG\r\n\x1a\n'): return False
    offset = 8; first = True
    while offset + 12 <= len(data):
        length = int.from_bytes(data[offset:offset+4], 'big')
        kind = data[offset+4:offset+8]; end = offset + 12 + length
        if end > len(data) or (first and (kind != b'IHDR' or length != 13)): return False
        payload = data[offset+4:end-4]
        if zlib.crc32(payload) & 0xffffffff != int.from_bytes(data[end-4:end], 'big'): return False
        if kind == b'IEND': return length == 0 and end == len(data)
        offset = end; first = False
    return False

def helper_off(text):
    return ('accepted=true' in text and 'helper_enabled=false' in text and 'boot_recovery=false' in text
            and all(re.search(r'\b' + name + r'=2\b', text) for name in HELPERS))

def home_is(text, package):
    return bool(re.search(r'^' + re.escape(package) + r'/(?:\.MainActivity|com\.gathin\.tvhome\.MainActivity)\s*$', text, re.M))

def cpu_ticks(data):
    for line in data.splitlines():
        if line.startswith('cpu '):
            fields = [int(x) for x in line.split()[1:9]]
            if len(fields) != 8: return None
            return {'total': sum(fields), 'idle': fields[3] + fields[4]}
    return None

def lifecycle_timeout(text, package):
    for line in text.splitlines():
        if package in line and re.search(r'ANR in|Input event dispatching timed out|Activity pause timeout|is not responding',line,re.I):
            return line
    return None

class TV:
    def __init__(self, adb, serial, out):
        self.prefix = [str(adb), '-s', serial]; self.out = out; self.sequence = 0
    def run(self, label, args, timeout=12, required=False):
        self.sequence += 1
        stem = f'{self.sequence:04d}-{label}'
        begin = time.monotonic(); utc = now()
        try:
            p = subprocess.run(self.prefix + list(args), capture_output=True, timeout=timeout)
            code, data, error = p.returncode, p.stdout, p.stderr
        except subprocess.TimeoutExpired as e:
            code, data, error = None, e.stdout or b'', (e.stderr or b'') + b'\nHOST_TIMEOUT'
        (self.out/(stem+'.stdout')).write_bytes(data)
        (self.out/(stem+'.stderr')).write_bytes(error)
        record = dict(label=label, utc=utc, durationSeconds=round(time.monotonic()-begin, 3),
                      argv=self.prefix+list(args), returncode=code, stdout=stem+'.stdout', stderr=stem+'.stderr')
        with (self.out/'commands.jsonl').open('a', encoding='utf-8') as f:
            f.write(json.dumps(record, ensure_ascii=False)+'\n')
        if required and (code != 0 or re.search(rb'(?i)(exception|error:|failure|permission denial)', data+error)):
            raise RuntimeError(f'{label} failed; inspect {stem}.stdout/.stderr')
        return data.decode('utf-8', 'replace'), code, data
    def shell(self, label, *args, **kw): return self.run(label, ['shell', *args], **kw)
    def verify(self):
        text, code, _ = self.run('transport', ['get-state'], timeout=5)
        if code != 0 or text.strip() != 'device': raise RuntimeError('DEVICE_OFFLINE: reconnect only the known authorized endpoint')
        model, _, _ = self.shell('model', 'getprop', 'ro.product.model', required=True)
        user, _, _ = self.shell('user', 'am', 'get-current-user', required=True)
        fingerprint, _, _ = self.shell('fingerprint', 'getprop', 'ro.build.fingerprint', required=True)
        if model.strip() != 'MiTV-MFTR0' or user.strip() != '0' or fingerprint.strip() != FINGERPRINT:
            raise RuntimeError('DEVICE_IDENTITY_DRIFT: no automatic device writes')
    def status(self, package, required=True):
        text, _, _ = self.shell('status-'+package, 'content', 'call', '--uri', 'content://'+package+'.recovery', '--method', 'status', required=required)
        if required and ('accepted=true' not in text or 'package='+package not in text):
            raise RuntimeError('STATUS_UNAVAILABLE: install and verify v13.4 before this phase')
        return text
    def resolver(self):
        return self.shell('home-resolver', 'cmd', 'package', 'resolve-activity', '--brief', '--user', '0',
                          '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME')[0]
    def snapshot(self, label, hierarchy=False):
        before = self.shell(label+'-activity-before', 'dumpsys', 'activity', 'activities')[0]
        windows = self.shell(label+'-windows', 'dumpsys', 'window', 'windows')[0]
        _, code, data = self.run(label+'-screen', ['exec-out','screencap','-p'])
        valid = code == 0 and valid_png(data)
        if valid: (self.out/(label+'.png')).write_bytes(data)
        if hierarchy:
            # Do not reuse /sdcard/window_dump.xml left by a previous test or another foreground app.
            remote = '/data/local/tmp/tvhome-v13_4-'+str(self.sequence)+'.xml'
            self.shell(label+'-ui-clean', 'rm', '-f', remote)
            _, result, _ = self.shell(label+'-ui-dump', 'uiautomator', 'dump', '--compressed', remote, timeout=15)
            if result == 0: self.shell(label+'-ui-read', 'cat', remote)
            self.shell(label+'-ui-remove', 'rm', '-f', remote)
        after = self.shell(label+'-activity-after', 'dumpsys', 'activity', 'activities')[0]
        one, two = component(before), component(after)
        full = one
        if one and '/.' in one:
            pkg, cls = one.split('/',1); full = pkg+'/'+pkg+cls
        result = dict(label=label, utc=now(), beforeComponent=one, afterComponent=two,
                      stableComponent=one is not None and one == two, validPng=valid,
                      windowContainsResumedComponent=bool(one and (one in windows or full in windows)),
                      sha256=hashlib.sha256(data).hexdigest() if valid else None,
                      visualPass='NOT_AUTOMATICALLY_INFERRED')
        save(self.out/(label+'-capture.json'), result)
        return result
    def failures(self):
        self.shell('last-anr', 'dumpsys', 'activity', 'lastanr', timeout=20)
        for package in IDS:
            self.shell('exit-info-'+package, 'dumpsys', 'activity', 'exit-info', package, timeout=20)
        self.shell('logcat-tail', 'logcat', '-d', '-v', 'threadtime', '-t', '2500', timeout=20)
    @contextmanager
    def logs(self):
        args = self.prefix + ['logcat','-v','threadtime','-T','1','TvHome.Video:I','TvHome.Lifecycle:I',
            'TvHome.Settings:I','AndroidRuntime:E','ActivityTaskManager:W','ActivityManager:E',
            'WindowManager:W','InputDispatcher:W','*:S']
        with (self.out/'live-logcat.txt').open('wb') as output, (self.out/'live-logcat.stderr').open('wb') as error:
            p = subprocess.Popen(args, stdout=output, stderr=error)
            try: yield
            finally:
                if p.poll() is None:
                    p.terminate()
                    try: p.wait(timeout=5)
                    except subprocess.TimeoutExpired: p.kill(); p.wait(timeout=5)
                save(self.out/'live-logcat-command.json', dict(argv=args, returncode=p.returncode))


def inspect(tv, package):
    tv.resolver()
    tv.shell('users', 'pm', 'list', 'users')
    tv.shell('disabled-packages', 'pm', 'list', 'packages', '-d', '--user', '0')
    tv.shell('accessibility', 'settings', '--user', '0', 'get', 'secure', 'enabled_accessibility_services')
    for pkg in (*IDS, 'com.mitv.tvhome', 'com.xiaomi.mitv.appstore'):
        tv.shell('package-'+pkg, 'dumpsys', 'package', pkg)
    tv.status(package, required=False)
    tv.snapshot('current', hierarchy=True)
    tv.failures()


def settings_cycle(tv, package, cycles):
    tv.status(package)
    records=[]
    with tv.logs():
        for i in range(cycles):
            tv.shell(f'cycle-{i}-open', 'am','start','-W','-n',package+COMPONENT,
                     '-a','com.gathin.tvhome.action.LAUNCHER_SETTINGS', timeout=20, required=True)
            time.sleep(2)
            entry=tv.snapshot(f'cycle-{i}-settings', hierarchy=True)
            expected=package+'/com.gathin.tvhome.LauncherSettingsActivity'
            compact=package+'/.LauncherSettingsActivity'
            timeout=lifecycle_timeout((tv.out/'live-logcat.txt').read_text(encoding='utf-8',errors='replace'),package)
            if timeout or not entry['windowContainsResumedComponent']:
                records.append(dict(cycle=i,status='LIFECYCLE_TIMEOUT' if timeout else 'NO_SETTINGS_WINDOW',entry=entry,timeout=timeout));break
            if not entry['stableComponent'] or entry['afterComponent'] not in (expected,compact):
                records.append(dict(cycle=i, status='UNEXPECTED_FOREGROUND', entry=entry)); break
            tv.shell(f'cycle-{i}-back', 'input','keyevent','4', required=True)
            time.sleep(2)
            back=tv.snapshot(f'cycle-{i}-returned', hierarchy=True)
            records.append(dict(cycle=i, status='CAPTURED_REQUIRES_VISUAL_AND_ANR_REVIEW', entry=entry, returned=back))
            save(tv.out/'cycles.json', records)
            timeout=lifecycle_timeout((tv.out/'live-logcat.txt').read_text(encoding='utf-8',errors='replace'),package)
            if timeout:
                records[-1].update(status='LIFECYCLE_TIMEOUT',timeout=timeout);break
            if not back['stableComponent'] or back['afterComponent'] not in (package+COMPONENT,package+'/.MainActivity'): break
        tv.failures()
    save(tv.out/'cycles.json', records)
    # AM resumed state alone is insufficient: a blank/no-window Activity may still be reported resumed.
    return dict(status='COLLECTED_NOT_A_VISUAL_PASS', cycles=len(records))


def video_observe(tv, package, seconds):
    records=[]; start=time.monotonic()
    with tv.logs():
        for offset in range(0,seconds+1,10):
            delay=start+offset-time.monotonic()
            if delay>0:time.sleep(delay)
            snap=tv.snapshot('video-'+str(offset),hierarchy=False)
            records.append(dict(offsetSeconds=time.monotonic()-start,snapshot=snap))
            save(tv.out/'video-observations.json',records)
            if not snap['stableComponent'] or snap['afterComponent'] not in (package+COMPONENT,package+'/.MainActivity'):
                return dict(status='OBSERVATION_FOREGROUND_CHANGED',samples=len(records))
    return dict(status='COLLECTED_REVIEW_FRAME_HEARTBEATS_AND_FIXED_VIDEO_CROP',samples=len(records))


def helpers(tv, package, restore=False):
    tv.status(package)
    method='restore_helpers' if restore else 'disable_helpers'
    text, _, _=tv.shell(method,'content','call','--uri','content://'+package+'.recovery','--method',method,required=True)
    if 'accepted=true' not in text: raise RuntimeError('HELPER_CHANGE_INCOMPLETE: do not claim native HOME validation')
    status=tv.status(package)
    if not restore and not helper_off(status): raise RuntimeError('HELPER_STATE_MISMATCH')
    tv.shell('helpers-package-state','dumpsys','package',package)
    return dict(status='HELPERS_RESTORED_DISARMED' if restore else 'HELPERS_DISABLED_CONFIRMED')


def require_helpers_off(tv, package):
    if not helper_off(tv.status(package)): raise RuntimeError('HELPERS_NOT_OFF: active package')
    for other in IDS:
        if other == package: continue
        present, code, _ = tv.shell('other-owned-install','pm','list','packages','--user','0',other,required=True)
        if code == 0 and ('package:'+other) in present.splitlines() and not helper_off(tv.status(other)):
            raise RuntimeError('HELPERS_NOT_OFF: other owned launcher '+other)


def home_probe(tv, package):
    require_helpers_off(tv, package)
    tv.resolver()
    tv.shell('set-home','cmd','package','set-home-activity','--user','0',package+COMPONENT,required=True)
    resolver=tv.resolver()
    if not home_is(resolver,package): return dict(status='HOME_RESOLVER_NOT_SELECTED', rebootIssued=False)
    tv.shell('synthetic-home','input','keyevent','3',required=True)
    time.sleep(2)
    screen=tv.snapshot('after-home',hierarchy=True)
    expected=(package+COMPONENT,package+'/.MainActivity')
    return dict(status='SYNTHETIC_HOME_CANDIDATE' if screen['stableComponent'] and screen['afterComponent'] in expected else 'HOME_FOREGROUND_MISMATCH',
                screenshot=screen, rebootIssued=False, physicalHome='NOT_TESTED',naturalBoot='NOT_TESTED')


def natural_boot(tv, package):
    require_helpers_off(tv, package)
    if not home_is(tv.resolver(),package): raise RuntimeError('HOME_RESOLVER_NOT_SELECTED: no reboot issued')
    start=time.monotonic()
    _, code, _=tv.run('reboot-issued',['reboot'],timeout=8)
    if code != 0: raise RuntimeError('REBOOT_COMMAND_FAILED')
    checkpoints=[]
    for scheduled in (15,30,60,90,120,180):
        remaining=start+scheduled-time.monotonic()
        if remaining > 0: time.sleep(remaining)
        observed=round(time.monotonic()-start,2)
        state, rc, _=tv.run(f'boot-{scheduled}-transport',['get-state'],timeout=3)
        if (rc != 0 or state.strip() != 'device') and re.search(r':\d+$', tv.prefix[-1]):
            # Only the already authorized TCP endpoint; reconnecting transport does not launch UI.
            tv.run(f'boot-{scheduled}-reconnect-known',['connect',tv.prefix[-1]],timeout=3)
            state, rc, _=tv.run(f'boot-{scheduled}-transport-recheck',['get-state'],timeout=3)
            observed=round(time.monotonic()-start,2)
        if rc != 0 or state.strip() != 'device':
            checkpoints.append(dict(scheduledSeconds=scheduled, actualSeconds=observed,status='ADB_UNAVAILABLE'))
        else:
            # All commands from reboot to the last checkpoint are observational: no am start,
            # input, provider calls, helper, or reconnect-triggered app launch.
            boot=tv.shell(f'boot-{scheduled}-completed','getprop','sys.boot_completed',timeout=3)[0].strip()
            resolver=tv.resolver()
            snap=tv.snapshot(f'boot-{scheduled}')
            checkpoints.append(dict(scheduledSeconds=scheduled,actualSeconds=observed,status='OBSERVED',
                                    bootCompleted=boot,resolver=resolver,snapshot=snap))
        save(tv.out/'boot-checkpoints.json',checkpoints)
    return dict(status='BOOT_EVIDENCE_REQUIRES_REVIEW' if any(x['status']=='OBSERVED' for x in checkpoints) else 'BOOT_UNOBSERVED_ADB_OFFLINE',checkpoints=checkpoints)


def sample(tv, package, seconds):
    records=[]; start=time.monotonic(); previous=None
    tv.shell('services','dumpsys','-l')
    tv.shell('frames-before','dumpsys','gfxinfo',package,'framestats')
    with tv.logs():
        for index in range(seconds//10+1):
            remaining=start+index*10-time.monotonic()
            if remaining>0:time.sleep(remaining)
            activity=tv.shell(f'sample-{index}-activity','dumpsys','activity','activities')[0]
            raw=tv.shell(f'sample-{index}-proc-stat','cat','/proc/stat')[0]
            current=cpu_ticks(raw); percent=None
            if current and previous and current['total']>previous['total']:
                percent=100*(1-(current['idle']-previous['idle'])/(current['total']-previous['total']))
            previous=current
            tv.shell(f'sample-{index}-top','top','-b','-n','1','-m','25',timeout=8)
            tv.shell(f'sample-{index}-memory','dumpsys','meminfo',package,timeout=8)
            records.append(dict(index=index,elapsedSeconds=round(time.monotonic()-start,2),foreground=component(activity),
                                allCoreCpuBusyPercent=percent,procStat=current))
            save(tv.out/'samples.json',records)
        tv.shell('frames-after','dumpsys','gfxinfo',package,'framestats')
        for name in ('media.codec','media.player','media.resource_manager','thermalservice','hardware_properties'):
            tv.shell(name.replace('.','-'),'dumpsys',name,timeout=10)
        tv.snapshot('sample-end')
    return dict(status='COLLECTED_NOT_A_PERFORMANCE_PASS',samples=len(records),
                note='Use /proc/stat deltas, actual foreground, and fresh frame counts. top/dumpsys may be cached or unsupported.')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb',type=Path,required=True);parser.add_argument('--serial',required=True)
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--package',choices=READ_PACKAGES,required=True)
    parser.add_argument('--execute',action='store_true')
    parser.add_argument('phase',choices=('inspect','capture','settings-cycle','helpers-off','helpers-restore','home-probe','natural-boot','sample','video-observe'))
    parser.add_argument('--cycles',type=int,default=5);parser.add_argument('--seconds',type=int,default=60)
    args=parser.parse_args()
    if not args.adb.is_file() or not re.fullmatch(r'[A-Za-z0-9_.:\-\[\]]+',args.serial):parser.error('Invalid adb path or serial')
    if not 1<=args.cycles<=10 or args.seconds not in (30,60,90):parser.error('cycles 1..10; seconds 30,60,90')
    mutating=args.phase in ('settings-cycle','helpers-off','helpers-restore','home-probe','natural-boot')
    if mutating and not args.execute:parser.error('This phase requires --execute')
    if mutating and args.package not in IDS:parser.error('Mutations are restricted to the two owned launcher IDs')
    args.out.mkdir(parents=True,exist_ok=False)
    save(args.out/'session.json',dict(schema=2,utc=now(),phase=args.phase,package=args.package,synthetic=True))
    tv=TV(args.adb.resolve(),args.serial,args.out)
    result=dict(status='STARTED')
    try:
        tv.verify()
        if args.phase=='inspect':inspect(tv,args.package);result=dict(status='COLLECTED')
        elif args.phase=='capture':
            if args.package in IDS: tv.status(args.package)
            result=tv.snapshot('capture',hierarchy=True)
        elif args.phase=='settings-cycle':result=settings_cycle(tv,args.package,args.cycles)
        elif args.phase=='video-observe':result=video_observe(tv,args.package,args.seconds)
        elif args.phase.startswith('helpers-'):result=helpers(tv,args.package,args.phase=='helpers-restore')
        elif args.phase=='home-probe':result=home_probe(tv,args.package)
        elif args.phase=='natural-boot':result=natural_boot(tv,args.package)
        else:result=sample(tv,args.package,args.seconds)
    except Exception as error:
        result=dict(status='BLOCKED',error=str(error));raise
    finally:save(args.out/'result.json',dict(**result,finishedUtc=now()))
    print(json.dumps(result,ensure_ascii=False))

if __name__=='__main__':main()
