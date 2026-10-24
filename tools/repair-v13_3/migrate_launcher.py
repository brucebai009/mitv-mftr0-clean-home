#!/usr/bin/env python3
"""Transfer original launcher state to a fresh compatibility install; all ADB writes are explicit."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

SOURCE = 'com.gathin.tvhome'
TARGET = 'com.ts.launcher'
MAX_METADATA = 512 * 1024

def allowed(path):
    if path in ('config.json', 'wallpaper.jpg', 'wallpaper_catalog.json', 'theme_revision'):
        return True
    return bool(re.fullmatch(r'wallpapers/[A-Za-z0-9_-][A-Za-z0-9_.-]*\.(?:jpg|jpeg|png|webp)|videos/[A-Za-z0-9_-][A-Za-z0-9_.-]*\.mp4', path, re.I))

def validate(data):
    if data.get('schema') != 1 or data.get('source') != SOURCE or data.get('target') != TARGET:
        raise ValueError('Wrong identity or schema')
    media = data.get('media')
    if not isinstance(media, list) or len(media) > 256: raise ValueError('Invalid media manifest')
    seen = set(); total = 0
    for entry in media:
        path = entry['path']; size = entry['size']
        if not isinstance(path, str) or not allowed(path) or path in seen: raise ValueError('Unsafe/duplicate path')
        if type(size) is not int or not 0 <= size <= 512 * 1024 * 1024: raise ValueError('Invalid size')
        if not re.fullmatch(r'[0-9a-f]{64}', entry['sha256']): raise ValueError('Invalid SHA256')
        seen.add(path); total += size
    if total > 2 * 1024 * 1024 * 1024: raise ValueError('Media total exceeds 2 GiB')
    if set(data.get('preferences', {})) != {'launcher_settings', 'wallpaper_rotation', 'last_opened'}:
        raise ValueError('Unexpected preference stores')
    if not set(data.get('privateFiles', {})) <= {'order.txt', 'cards.json'}: raise ValueError('Unexpected private files')
    return media

class Transfer:
    def __init__(self, adb, serial, out):
        self.prefix = [str(adb), '-s', serial]; self.out = out; self.events = []
    def run(self, args, *, input_file=None, output_file=None, timeout=180):
        with input_file.open('rb') if input_file else open_devnull() as stdin:
            with output_file.open('wb') if output_file else capture_file(self.out) as stdout:
                p = subprocess.run(self.prefix + args, stdin=stdin, stdout=stdout, stderr=subprocess.PIPE, timeout=timeout)
        result = b'' if output_file else (self.out/'last-output.tmp').read_bytes()
        self.events.append({'argv': args, 'returncode': p.returncode, 'stderr': p.stderr.decode('utf-8', 'replace')})
        (self.out/'commands.json').write_text(json.dumps(self.events, ensure_ascii=False, indent=2), encoding='utf-8')
        if p.returncode != 0: raise RuntimeError('ADB failed: ' + p.stderr.decode('utf-8', 'replace'))
        # Android content/pm can print a Java exception while adb itself exits zero.
        if not output_file and re.search(rb'(?i)(exception|error:|failure|denied)', result + p.stderr):
            raise RuntimeError(result.decode('utf-8', 'replace'))
        return result
    def verify(self):
        if self.run(['get-state']).strip() != b'device': raise RuntimeError('TV unavailable')
        if self.run(['shell','getprop','ro.product.model']).strip() != b'MiTV-MFTR0': raise RuntimeError('Wrong TV model')
        if self.run(['shell','am','get-current-user']).strip() != b'0': raise RuntimeError('This transfer requires user 0')

def open_devnull():
    import os
    return open(os.devnull, 'rb')
def capture_file(out): return (out/'last-output.tmp').open('wb')

def verify_file(file, entry):
    digest = hashlib.sha256()
    with file.open('rb') as stream:
        while block := stream.read(1024*1024): digest.update(block)
    if file.stat().st_size != entry['size'] or digest.hexdigest() != entry['sha256']:
        raise ValueError('Media changed or was corrupted: ' + entry['path'])

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True); parser.add_argument('--serial', required=True)
    parser.add_argument('--out', type=Path, required=True); parser.add_argument('--execute', action='store_true')
    parser.add_argument('action', choices=('export','import')); parser.add_argument('--source', type=Path)
    args=parser.parse_args()
    if not args.adb.is_file() or not re.fullmatch(r'[A-Za-z0-9_.:\-\[\]]+', args.serial): parser.error('Invalid adb path/serial')
    if args.action == 'import' and (not args.execute or args.source is None): parser.error('Import needs --source and --execute')
    args.out.mkdir(parents=True, exist_ok=False)
    tv=Transfer(args.adb.resolve(), args.serial,args.out); tv.verify()
    if args.action == 'export':
        metadata=args.out/'state.json'
        tv.run(['exec-out','content','read','--uri',f'content://{SOURCE}.transfer/export'], output_file=metadata)
        if metadata.stat().st_size > MAX_METADATA: raise ValueError('Oversized metadata')
        entries=validate(json.loads(metadata.read_text(encoding='utf-8')))
        for entry in entries:
            file=args.out/'media'/entry['path'];file.parent.mkdir(parents=True,exist_ok=True)
            tv.run(['exec-out','content','read','--uri',f'content://{SOURCE}.theme/'+entry['path']],output_file=file)
            verify_file(file,entry)
        (args.out/'COMPLETE.json').write_text(json.dumps({'sha256':hashlib.sha256(metadata.read_bytes()).hexdigest(),'files':len(entries)}),encoding='utf-8')
        print('EXPORT VERIFIED:', len(entries), 'media files; original data unchanged')
    else:
        src=args.source.resolve();metadata=src/'state.json'
        marker=json.loads((src/'COMPLETE.json').read_text(encoding='utf-8'))
        if metadata.stat().st_size > MAX_METADATA or hashlib.sha256(metadata.read_bytes()).hexdigest()!=marker['sha256']:
            raise ValueError('Export metadata hash mismatch')
        entries=validate(json.loads(metadata.read_text(encoding='utf-8')))
        for entry in entries: verify_file(src/'media'/entry['path'],entry)
        response=tv.run(['shell','content','call','--uri',f'content://{TARGET}.transfer','--method','prepare'])
        if b'accepted=true' not in response: raise RuntimeError('Target is not a fresh installation; nothing copied')
        # Stage media first; preferences and selected wallpaper path are committed only after app-side SHA checks.
        for entry in entries:
            tv.run(['shell','content','write','--uri',f'content://{TARGET}.theme/'+entry['path']],input_file=src/'media'/entry['path'])
        tv.run(['shell','content','write','--uri',f'content://{TARGET}.transfer/import'],input_file=metadata)
        response=tv.run(['shell','content','call','--uri',f'content://{TARGET}.transfer','--method','apply'])
        (args.out/'apply.txt').write_bytes(response)
        if b'accepted=true' not in response: raise RuntimeError('App did not accept transfer')
        print('TRANSFER ACCEPTED; still verify selected wallpaper, dock and order on the TV')
if __name__=='__main__': main()
