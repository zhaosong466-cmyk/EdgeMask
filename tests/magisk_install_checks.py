#!/usr/bin/env python3
"""Magisk entry-point regressions and production APK CLI installer in a fake system.

Executes Magisk's pinned install_module() with filesystem paths rebased into a temporary
directory. Platform setup is stubbed. No Android device, real Root or display/property writes.
"""
import hashlib
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = ROOT / 'tests/fixtures/magisk'
ASSETS = ROOT / 'app/src/main/assets'
ARCHIVE = ASSETS / 'EdgeMask-QTI-Compat-1.0.1.zip'
WORKSPACE = ROOT.parent


def run(args, **kwargs):
    return subprocess.run(args, capture_output=True, text=True, timeout=25, **kwargs)


def executable(path, content):
    path.write_text('#!/bin/sh\n' + content)
    path.chmod(0o755)


def old_manager_extract(archive, target):
    # FlashZip v24.3 flattens this subtree, then reads updater-script and looks for #MAGISK.
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            if name.startswith('META-INF/com/google/android/') and not name.endswith('/'):
                (target / PurePosixPath(name).name).write_bytes(z.read(name))
    return '#MAGISK' in (target / 'updater-script').read_text()


def fixture(base, name, **overrides):
    d = base / name
    d.mkdir()
    bins = d / 'bin'
    bins.mkdir()
    module = d / 'adb/modules_update/edgemask_qti_framebuffer'
    original = (FIXTURES / 'install_module.sh').read_text()
    # Replace every absolute Android storage root in this TEST COPY only.
    assert original.count('/data/adb/') == 5
    body = original.replace('/data/adb/', str(d / 'adb') + '/')
    assert '/data/adb/' not in body
    helpers = '''MAGISK_VER_CODE=30000
BOOTMODE=true
TMPDIR="$EDGE_FIXTURE/tmp"
REPLACE=""
REMOVE=""
setup_flashable(){ :; }
mount_partitions(){ :; }
api_level_arch_detect(){ :; }
boot_actions(){ :; }
recovery_actions(){ exit 9; }
recovery_cleanup(){ :; }
chcon(){ :; }
is_legacy_script(){ return 1; }
ui_print(){ printf '%s\\n' "$*"; }
print_title(){ printf '%s\\n' "$*"; }
grep_prop(){ sed -n "s/^$1=//p" "$2"; }
set_default_perm(){ :; }
set_perm(){ chmod "$4" "$1"; }
mktouch(){ mkdir -p "${1%/*}"; touch "$1"; }
abort(){ printf '%s\\n' "$*" >&2; [ -z "${MODPATH:-}" ] || rm -rf "$MODPATH"; exit 1; }
'''
    (d / 'util_functions.sh').write_text(helpers + body)
    launcher = d / 'install launcher.sh'
    source = (ASSETS / 'magisk-install.sh').read_text()
    assert source.count('/data/adb/modules_update/edgemask_qti_framebuffer') == 1
    launcher.write_text(source.replace('/data/adb/modules_update/edgemask_qti_framebuffer', str(module)))
    archive = d / "module ' $(touch INJECTION) `touch INJECTION2`.zip"
    shutil.copyfile(ARCHIVE, archive)
    executable(bins / 'su', '''printf 'su\\n' >> "$EDGE_FIXTURE/su-calls"
[ "${EDGE_DENIED:-0}" = 0 ] || { echo 'AUTHORIZATION_DENIED'; exit 1; }
[ "$#" = 2 ] && [ "$1" = -c ] || exit 2
exec /bin/sh -c "$2"
''')
    executable(bins / 'id', 'printf "%s\\n" "${EDGE_UID:-0}"\n')
    executable(bins / 'mount', 'exit 0\n')
    for forbidden in ('resetprop', 'setprop', 'wm', 'reboot'):
        executable(bins / forbidden, 'printf "%s\\n" "$0 $*" >> "$EDGE_FIXTURE/forbidden"; exit 99\n')
    verified = (ROOT / 'compat/qti-framebuffer/supported-libraries.txt').read_text().strip()
    executable(bins / 'sha256sum', 'printf "%s  %s\\n" "${EDGE_LIBRARY_HASH:-' + verified + '}" "$1"\n')
    executable(bins / 'magisk', '''case "$1" in
-V) [ "${EDGE_DAEMON_FAILURE:-0}" = 0 ] || exit 1; printf '%s\\n' "${EDGE_VERSION:-30000}";;
--help) [ "${EDGE_NO_CLI:-0}" = 0 ] && echo '--install-module ZIP'; exit 1;;
--install-module)
  [ "$#" = 2 ] || exit 2
  printf '%s\\n' "$2" >> "$EDGE_FIXTURE/cli-archives"
  [ "${EDGE_INSTALL_FAILURE:-0}" = 0 ] || { echo 'INSTALLER_FAILURE_DETAILS'; exit 1; }
  [ "${EDGE_EMPTY_SUCCESS:-0}" = 0 ] || exit 0
  [ "${EDGE_DELAY:-0}" = 0 ] || sleep 1
  ZIPFILE=$2
  . "$EDGE_FIXTURE/util_functions.sh"
  install_module
  if [ "${EDGE_WRONG_VERSION:-0}" = 1 ]; then sed -i 's/versionCode=2/versionCode=1/' "$MODPATH/module.prop"; fi
  [ "${EDGE_INCOMPLETE:-0}" = 0 ] || rm "$MODPATH/post-fs-data.sh"
  ;;
*) exit 2;;
esac
''')
    env = dict(os.environ, EDGE_FIXTURE=str(d), PATH=str(bins) + ':' + os.environ['PATH'], **overrides)
    return d, module, launcher, archive, env


count = 0
with tempfile.TemporaryDirectory(prefix='edge-magisk-') as tmp:
    base = Path(tmp)
    old_dir = base / 'old-manager'
    old_dir.mkdir()
    assert 'zipFile.unzip(installDir, "META-INF/com/google/android", true)' in (FIXTURES / 'FlashZip-v24.3.kt').read_text()
    old = base / 'old-minimal.zip'
    with zipfile.ZipFile(old, 'w', zipfile.ZIP_DEFLATED) as z:
        for name in ('module.prop', 'supported-libraries.txt', 'customize.sh', 'post-fs-data.sh', 'skip_mount'):
            z.write(ROOT / 'compat/qti-framebuffer' / name, name)
    try:
        old_manager_extract(old, old_dir)
        raise AssertionError('old ZIP unexpectedly supplied the entry point')
    except FileNotFoundError:
        print('PASS: reproduced old-manager Unzip error on missing updater-script')
    new_dir = base / 'new-manager'
    new_dir.mkdir()
    assert old_manager_extract(ARCHIVE, new_dir)
    assert (new_dir / 'update-binary').is_file()
    count += 2

    # Run the official module bootstrap and its actual install_module() in the fake system.
    d, module, launcher, archive, env = fixture(base, 'official-bootstrap')
    bootstrap = d / 'update-binary'
    upstream = (new_dir / 'update-binary').read_text()
    assert upstream.count('/data/adb/magisk/util_functions.sh') == 2
    bootstrap.write_text(upstream.replace('/data/adb/magisk/util_functions.sh', str(d / 'util_functions.sh')))
    result = run(['sh', str(bootstrap), 'dummy', '1', str(archive)], env=env)
    assert result.returncode == 0, result.stdout + result.stderr
    assert (module / 'post-fs-data.sh').read_bytes() == (ROOT / 'compat/qti-framebuffer/post-fs-data.sh').read_bytes()
    assert (module / 'skip_mount').is_file() and not (module / 'META-INF').exists()
    assert not (d / 'forbidden').exists()
    assert (module / 'post-fs-data.sh').stat().st_mode & 0o777 == 0o755
    count += 1
    print('PASS: official bootstrap installs the actual ZIP without property/display writes')

    classes = base / 'classes'
    classes.mkdir()
    sources = [ROOT / 'app/src/main/java/com/edges/mask' / name for name in ('RootShell.java', 'MagiskModuleInstaller.java')]
    sources += [ROOT / 'tests/ModuleInstallerChecks.java']
    result = run(['java', '-jar', str(WORKSPACE / 'tools/ecj.jar'), '-source', '8', '-target', '8',
                  '-d', str(classes)] + [str(p) for p in sources])
    assert result.returncode == 0, result.stdout + result.stderr
    cases = [
        ('cli-success', {}, 'success', None),
        ('root-denied', {'EDGE_DENIED': '1'}, 'failure', 'AUTHORIZATION_DENIED'),
        ('non-root', {'EDGE_UID': '2000'}, 'failure', 'Root authorization'),
        ('missing-magisk', {}, 'failure', 'Magisk command is unavailable'),
        ('daemon-unavailable', {'EDGE_DAEMON_FAILURE': '1'}, 'failure', 'Cannot reach'),
        ('invalid-version', {'EDGE_VERSION': 'unverified'}, 'failure', 'Cannot verify'),
        ('no-cli-support', {'EDGE_NO_CLI': '1'}, 'failure', 'no module CLI installer'),
        ('installer-error', {'EDGE_INSTALL_FAILURE': '1'}, 'failure', 'INSTALLER_FAILURE_DETAILS'),
        ('unknown-library', {'EDGE_LIBRARY_HASH': '0' * 64}, 'failure', 'has not been analyzed'),
        ('empty-success', {'EDGE_EMPTY_SUCCESS': '1'}, 'failure', 'installation was not verified'),
        ('wrong-staged-version', {'EDGE_WRONG_VERSION': '1'}, 'failure', 'new module version'),
        ('incomplete-stage', {'EDGE_INCOMPLETE': '1'}, 'failure', 'files are incomplete'),
        ('missing-archive', {}, 'failure', '内置安装文件不完整'),
        ('missing-launcher', {}, 'failure', '内置安装文件不完整'),
        ('concurrent', {'EDGE_DELAY': '1'}, 'concurrent', None),
    ]
    for name, overrides, expected, error in cases:
        d, module, launcher, archive, env = fixture(base, name, **overrides)
        if name == 'missing-magisk':
            (d / 'bin/magisk').unlink()
        if name == 'missing-archive':
            archive.unlink()
        if name == 'missing-launcher':
            launcher.unlink()
        args = ['java', '-cp', str(classes), 'com.edges.mask.ModuleInstallerChecks',
                str(launcher), str(archive), expected] + ([error] if error else [])
        result = run(args, env=env, cwd=d)
        assert result.returncode == 0, name + '\n' + result.stdout + result.stderr
        assert not (d / 'forbidden').exists(), name + ': installer changed display/property or rebooted'
        assert not (d / 'INJECTION').exists() and not (d / 'INJECTION2').exists(), 'archive path shell injection'
        if expected in ('success', 'concurrent'):
            assert (d / 'cli-archives').read_text().splitlines() == [str(archive)]
            assert (module / 'post-fs-data.sh').read_bytes() == (ROOT / 'compat/qti-framebuffer/post-fs-data.sh').read_bytes()
        count += 1
        print('PASS:', name)
print(f'PASS: {count} Magisk entry-point/CLI scenarios; no actual phone verification')
