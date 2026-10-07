#!/usr/bin/env python3
"""Production boot/installer scripts in a temporary fake system, never real properties.

Tests the firmware hash guard, early timing, bounded property call and reversible package.
Does not execute any uploaded native library or verify a real display.
"""
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'compat/qti-framebuffer'


def executable(path, content):
    path.write_text('#!/bin/sh\n' + content)
    path.chmod(0o755)


def fixture(base, name):
    d = base / name
    d.mkdir()
    bins = d / 'bin'
    bins.mkdir()
    lib = d / 'library.so'
    lib.write_bytes(b'synthetic library, not user firmware')
    library_hash = hashlib.sha256(lib.read_bytes()).hexdigest()
    (d / 'supported-libraries.txt').write_text(library_hash + '\n')
    for script in ('post-fs-data.sh', 'customize.sh'):
        original = (SOURCE / script).read_text()
        assert original.count('/system_ext/lib64/libsurfaceflinger.so') == 1
        (d / script).write_text(original.replace('/system_ext/lib64/libsurfaceflinger.so', str(lib)))
    executable(bins / 'pidof', 'exit "${EDGE_ALREADY_STARTED:-1}"\n')
    executable(bins / 'getprop', 'cat "$EDGE_FIXTURE/property"\n')
    executable(bins / 'resetprop', '''printf '%s\\n' "$*" >> "$EDGE_FIXTURE/writes"
[ "${EDGE_PROP_FAILURE:-0}" = 0 ] || exit 1
[ "${EDGE_PROP_HANG:-0}" = 0 ] || sleep 10
[ "$#" = 3 ] && [ "$1" = -n ] && [ "$2" = vendor.display.enable_fb_scaling ] && [ "$3" = false ] || exit 2
[ "${EDGE_PROP_VERIFY_FAILURE:-0}" = 0 ] || exit 0
printf 'false\\n' > "$EDGE_FIXTURE/property"
''')
    (d / 'property').write_text('true\n')
    env = dict(os.environ, EDGE_FIXTURE=str(d), PATH=str(bins) + ':' + os.environ['PATH'])
    return d, bins, env


count = 0
with tempfile.TemporaryDirectory(prefix='edge-fb-module-') as tmp:
    base = Path(tmp)
    cases = {'applied': {}, 'library-changed': {}, 'library-unreadable': {},
             'display-already-started': {'EDGE_ALREADY_STARTED': '0'}, 'resetprop-missing': {},
             'property-failed': {'EDGE_PROP_FAILURE': '1'},
             'property-timeout': {'EDGE_PROP_HANG': '1'},
             'property-unverified': {'EDGE_PROP_VERIFY_FAILURE': '1'}}
    for expected, overrides in cases.items():
        d, bins, env = fixture(base, expected)
        env.update(overrides)
        if expected == 'library-changed':
            (d / 'library.so').write_bytes(b'OTA changed the code')
        if expected == 'library-unreadable':
            (d / 'library.so').unlink()
        if expected == 'resetprop-missing':
            (bins / 'resetprop').unlink()
        result = subprocess.run(['sh', str(d / 'post-fs-data.sh')], env=env,
                                capture_output=True, text=True, timeout=10)
        assert result.returncode == 0, result.stderr
        expected_result = 'property-failed' if expected == 'property-timeout' else expected
        assert (d / 'boot-result').read_text().strip() == expected_result
        assert (d / 'applied-boot').read_text() == Path('/proc/sys/kernel/random/boot_id').read_text()
        changed = (d / 'property').read_text().strip() == 'false'
        assert changed == (expected == 'applied')
        called = (d / 'writes').exists()
        assert called == (expected in ('applied', 'property-failed', 'property-timeout', 'property-unverified'))
        if called:
            assert (d / 'writes').read_text() == '-n vendor.display.enable_fb_scaling false\n'
        count += 1
        print('PASS: boot module', expected)
    for name, bootmode, mismatch, succeeds in (
        ('installer', 'true', False, True), ('recovery', 'false', False, False),
        ('installer-ota', 'true', True, False)):
        d, bins, env = fixture(base, name)
        if mismatch:
            (d / 'library.so').write_bytes(b'unknown code')
        env.update(BOOTMODE=bootmode, MODPATH=str(d))
        wrapper = '''abort(){ echo "$*" >&2; exit 1; }
ui_print(){ echo "$*"; }
set_perm(){ printf '%s\\n' "$*" >> "$EDGE_FIXTURE/permissions"; }
. "$1"
'''
        result = subprocess.run(['sh', '-c', wrapper, 'installer-test', str(d / 'customize.sh')],
                                env=env, capture_output=True, text=True, timeout=10)
        assert (result.returncode == 0) == succeeds, result.stderr
        assert not (d / 'writes').exists(), 'installation must not change properties'
        assert (d / 'property').read_text() == 'true\n'
        assert (d / 'permissions').exists() == succeeds
        count += 1
        print('PASS: module', name)

archive = ROOT / 'app/src/main/assets/EdgeMask-QTI-Compat-1.0.1.zip'
with zipfile.ZipFile(archive) as z:
    expected = {'module.prop', 'supported-libraries.txt', 'customize.sh', 'post-fs-data.sh', 'skip_mount',
                'META-INF/com/google/android/update-binary', 'META-INF/com/google/android/updater-script',
                'LICENSE-Magisk', 'NOTICE'}
    assert set(z.namelist()) == expected and z.testzip() is None
    for name in expected:
        assert z.read(name) == (SOURCE / name).read_bytes()
    assert 'AI-written' in z.read('module.prop').decode()
    assert z.getinfo('post-fs-data.sh').external_attr >> 16 == 0o100755
    assert z.getinfo('META-INF/com/google/android/update-binary').external_attr >> 16 == 0o100755
    assert z.read('META-INF/com/google/android/updater-script') == b'#MAGISK\n'
    assert hashlib.sha256(z.read('META-INF/com/google/android/update-binary')).hexdigest() == 'bcf4b1d9913f3af17755569c853e0b5a75b8005f6a18eb3f86dadcc0e968c29d'
    assert all(i.extract_version < 45 and not i.flag_bits & 1 for i in z.infolist())
    assert not any(name in z.namelist() for name in ('system.prop', 'service.sh', 'sepolicy.rule', 'uninstall.sh'))
count += 1
print('PASS: minimal reversible package; disabling/removing stops boot override')
print(f'PASS: {count} module scenarios; no real Android execution')
