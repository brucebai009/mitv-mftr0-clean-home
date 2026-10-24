import importlib.util
from pathlib import Path
import pytest

PATH = Path(__file__).resolve().parents[1]/'tools/repair-v13_6/home_audit.py'
SPEC = importlib.util.spec_from_file_location('home_audit', PATH)
M = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(M)

def test_resolver_is_not_evidence_of_vendor_whitelist_rejection():
    assert M.classify('android/com.android.internal.app.ResolverActivity\n') == 'CHOOSER'
    assert not M.resolves_target('android/com.android.internal.app.ResolverActivity\n')
    assert M.resolves_target(M.TARGET+'\n')
    assert not M.resolves_target(M.TARGET+'\n'+M.STOCK+'\n')
    assert M.classify('Error: unknown command') == 'UNKNOWN'

def test_framework_paths_cannot_come_from_shell_injection_or_unrelated_jars():
    for name in ('services.jar', 'framework.jar', 'mitv-framework.jar'):
        assert M.framework_name(name)
    for name in ('../../secret.jar', 'mitv;id.jar', 'mitv$(id).jar', 'unrelated.jar', '/etc/passwd'):
        assert not M.framework_name(name)

class FakeTV:
    def __init__(self, out, failed_set=False, candidates=None):
        self.out = out; self.calls = []; self.failed_set = failed_set
        self.candidates = candidates if candidates is not None else M.TARGET+'\n'+M.STOCK+'\n'
    def shell(self, label, *args, **kw):
        self.calls.append((label,args))
        if label == 'select-compat' and self.failed_set: raise RuntimeError('HOST_TIMEOUT')
        if 'candidates' in label: return self.candidates, 0, b''
        if 'resolve' in label: return 'android/com.android.internal.app.ResolverActivity\n', 0, b''
        return '', 0, b''
    def snapshot(self, *args, **kw):
        return dict(stableComponent=True, afterComponent='android/com.android.internal.app.ResolverActivity',
                    windowContainsResumedComponent=True, validPng=True)

@pytest.fixture
def no_wait(monkeypatch):
    monkeypatch.setattr(M.BASE, 'require_helpers_off', lambda *args: None)
    monkeypatch.setattr(M.time, 'sleep', lambda *args: None)

def test_chooser_is_captured_then_known_stock_home_restored(tmp_path, no_wait):
    tv = FakeTV(tmp_path)
    result = M.probe(tv)
    assert result['whitelistRejected'] == 'NOT_PROVEN_BY_RESOLVER_RESULT'
    assert result['restoredOnFailure']
    labels = [x[0] for x in tv.calls]
    assert labels.index('select-compat') < labels.index('home-event') < labels.index('restore-home')
    restore = next(args for label,args in tv.calls if label == 'restore-home')
    assert restore[-1] == M.STOCK
    assert all('reboot' not in args for _,args in tv.calls)

def test_timeout_after_mutation_also_restores_home(tmp_path, no_wait):
    tv = FakeTV(tmp_path, failed_set=True)
    with pytest.raises(RuntimeError, match='HOST_TIMEOUT'): M.probe(tv)
    assert any(label == 'restore-home' for label,_ in tv.calls)

def test_absent_compat_or_recovery_component_never_changes_preferences(tmp_path, no_wait):
    for i,candidates in enumerate((M.STOCK+'\n', M.TARGET+'\n')):
        out = tmp_path/str(i);out.mkdir()
        tv = FakeTV(out,candidates=candidates)
        result = M.probe(tv)
        assert not result['devicePreferenceChanged']
        assert not any('set-home-activity' in args for _,args in tv.calls)
