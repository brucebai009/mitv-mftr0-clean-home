import importlib.util
from pathlib import Path
import hashlib
import pytest

spec = importlib.util.spec_from_file_location('migrate_launcher', Path(__file__).resolve().parents[1] / 'tools/repair-v13_3/migrate_launcher.py')
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)

def payload(path='videos/train.mp4'):
    return {'schema': 1, 'source': migration.SOURCE, 'target': migration.TARGET,
            'media': [{'path': path, 'size': 3, 'sha256': hashlib.sha256(b'abc').hexdigest()}],
            'preferences': {'launcher_settings': {}, 'wallpaper_rotation': {}, 'last_opened': {}}, 'privateFiles': {}}

@pytest.mark.parametrize('path', ['../config.json', '/videos/train.mp4', 'videos/../train.mp4', 'videos/a.mp4/extra', 'videos/.hidden.mp4', 'videos/a;reboot.mp4', 'videos/a\\b.mp4', 'wallpapers/a.gif'])
def test_rejects_unsafe_or_unrecognized_files_before_any_device_write(path):
    with pytest.raises(ValueError): migration.validate(payload(path))

def test_accepts_manifest_and_checks_actual_bytes(tmp_path):
    data=payload(); entries=migration.validate(data)
    f=tmp_path/'video'; f.write_bytes(b'abc'); migration.verify_file(f, entries[0])
    f.write_bytes(b'abd')
    with pytest.raises(ValueError): migration.verify_file(f, entries[0])

def test_refuses_duplicate_oversize_and_wrong_identity():
    data=payload(); data['media'] *= 2
    with pytest.raises(ValueError): migration.validate(data)
    data=payload(); data['media'][0]['size'] = 513*1024*1024
    with pytest.raises(ValueError): migration.validate(data)
    data=payload(); data['target']='other.app'
    with pytest.raises(ValueError): migration.validate(data)
