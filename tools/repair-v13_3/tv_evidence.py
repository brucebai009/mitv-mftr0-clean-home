#!/usr/bin/env python3
"""Private ADB evidence. Requires an explicit serial; never installs or selects HOME."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
from datetime import datetime, timezone

PNG_MAGIC = b'\x89PNG\r\n\x1a\n'
PACKAGES = ('com.gathin.tvhome', 'com.ts.launcher', 'com.mitv.tvhome')

def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

class TV:
    def __init__(self, adb, serial, out):
        self.prefix = [str(adb), '-s', serial]
        self.out = out
        self.records = []
    def run(self, name, args, timeout=30, binary=False):
        command = self.prefix + list(args)
        started = time.monotonic()
        try:
            result = subprocess.run(command, capture_output=True, timeout=timeout, check=False)
            record = dict(name=name, argv=command, returncode=result.returncode,
                          seconds=round(time.monotonic()-started, 3),
                          stderr=result.stderr.decode('utf-8', errors='replace'))
            data = result.stdout
        except subprocess.TimeoutExpired as error:
            data = error.stdout or b''
            record = dict(name=name, argv=command, returncode=None, error='TIMEOUT', timeout=timeout)
        suffix = '.bin' if binary else '.txt'
        (self.out / (name + suffix)).write_bytes(data)
        self.records.append(record)
        write_json(self.out / 'commands.json', self.records)
        return data, record
    def shell(self, name, *args, timeout=30):
        return self.run(name, ['shell', *args], timeout)
    def verify(self):
        state, rec = self.run('device-state', ['get-state'], 8)
        if rec.get('returncode') != 0 or state.strip() != b'device':
            raise RuntimeError('No authorized online device at the supplied serial; do not scan for another TV')
        model, rec = self.shell('device-model', 'getprop', 'ro.product.model')
        if rec.get('returncode') != 0 or model.strip() != b'MiTV-MFTR0':
            raise RuntimeError('Model mismatch: expected MiTV-MFTR0; no mutation allowed')
        user, rec = self.shell('current-user', 'am', 'get-current-user')
        text = user.decode().strip()
        if rec.get('returncode') != 0 or not text.isdigit():
            raise RuntimeError('Cannot determine current Android user')
        return text
    def screenshot(self, name):
        data, rec = self.run(name, ['exec-out', 'screencap', '-p'], binary=True)
        if rec.get('returncode') != 0 or not data.startswith(PNG_MAGIC):
            raise RuntimeError('Invalid screenshot: inspect recorded stderr; do not claim visual evidence')
        (self.out / (name + '.bin')).rename(self.out / (name + '.png'))
        return hashlib.sha256(data).hexdigest()

def snapshot(tv, user):
    commands = {
        'firmware': ['getprop'],
        'packages-disabled': ['pm', 'list', 'packages', '-d', '--user', user],
        'packages-all': ['pm', 'list', 'packages', '-f', '--user', user],
        'home-candidates': ['cmd', 'package', 'query-activities', '--brief', '--user', user,
                            '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'],
        'home-resolver': ['cmd', 'package', 'resolve-activity', '--brief', '--user', user,
                         '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'],
        'accessibility': ['settings', '--user', user, 'get', 'secure', 'enabled_accessibility_services'],
        'activity': ['dumpsys', 'activity', 'activities'],
        'window': ['dumpsys', 'window', 'windows'],
        'cpu': ['dumpsys', 'cpuinfo'],
        'memory': ['dumpsys', 'meminfo'],
        'codec': ['dumpsys', 'media.codec'],
        'player': ['dumpsys', 'media.player'],
        'power': ['dumpsys', 'power'],
        'logcat': ['logcat', '-d', '-v', 'threadtime', '-t', '2000'],
    }
    for name, args in commands.items():
        _, rec = tv.shell(name, *args)
        print(name, 'CAPTURED' if rec.get('returncode') == 0 else 'UNAVAILABLE', flush=True)
    for package in PACKAGES:
        tv.shell('package-' + package, 'dumpsys', 'package', package)
        tv.shell('frames-' + package, 'dumpsys', 'gfxinfo', package, 'framestats')
    tv.screenshot('screen')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True, type=Path)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--out', required=True, type=Path)
    sub = parser.add_subparsers(dest='action', required=True)
    sub.add_parser('snapshot')
    screen = sub.add_parser('screens')
    screen.add_argument('--count', type=int, default=2)
    screen.add_argument('--interval', type=float, default=4)
    sample = sub.add_parser('sample')
    sample.add_argument('--package', choices=PACKAGES, required=True)
    sample.add_argument('--seconds', type=int, default=60)
    key = sub.add_parser('key')
    key.add_argument('--code', type=int, choices=(3, 4, 19, 20, 21, 22, 23), required=True)
    key.add_argument('--execute', action='store_true')
    args = parser.parse_args()
    if not args.adb.is_file():
        parser.error('--adb must be an existing adb executable')
    if not re.fullmatch(r'[A-Za-z0-9_.:\-\[\]]+', args.serial):
        parser.error('Invalid serial')
    args.out.mkdir(parents=True, exist_ok=False)
    tv = TV(args.adb.resolve(), args.serial, args.out)
    write_json(args.out / 'session.json', dict(time_utc=datetime.now(timezone.utc).isoformat(),
        action=args.action, warning='Private raw device evidence; no pass is inferred from collection'))
    user = tv.verify()
    if args.action == 'snapshot':
        snapshot(tv, user)
    elif args.action == 'screens':
        if not (1 <= args.count <= 10 and 0 <= args.interval <= 10):
            parser.error('count must be 1..10; interval must be 0..10')
        hashes = []
        for i in range(args.count):
            if i:
                time.sleep(args.interval)
            hashes.append(tv.screenshot(f'screen-{i:02d}'))
        write_json(args.out / 'screen-hashes.json', dict(sha256=hashes,
            note='Whole-screen differences do not prove wallpaper motion; inspect a clock-free wallpaper crop'))
    elif args.action == 'sample':
        if not 10 <= args.seconds <= 180:
            parser.error('seconds must be 10..180')
        tv.shell('frames-before', 'dumpsys', 'gfxinfo', args.package, 'framestats')
        deadline = time.monotonic() + args.seconds
        i = 0
        while True:
            tv.shell(f'cpu-{i:02d}', 'dumpsys', 'cpuinfo')
            tv.shell(f'memory-{i:02d}', 'dumpsys', 'meminfo', args.package)
            print(f'Sample {i} captured', flush=True)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                break
            time.sleep(min(10, remaining))
            i += 1
        tv.shell('frames-after', 'dumpsys', 'gfxinfo', args.package, 'framestats')
        tv.shell('activity-after', 'dumpsys', 'activity', 'activities')
        tv.shell('codec-after', 'dumpsys', 'media.codec')
    else:
        if not args.execute:
            raise RuntimeError('Key sending requires --execute; no key sent')
        _, rec = tv.shell('synthetic-key', 'input', 'keyevent', str(args.code))
        if rec.get('returncode') != 0:
            raise RuntimeError('Key injection failed')
        time.sleep(1)
        tv.shell('activity-after-key', 'dumpsys', 'activity', 'activities')
        tv.screenshot('after-key')
        print('Synthetic key only; this does not verify a physical remote key')

if __name__ == '__main__':
    main()
