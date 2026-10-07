#!/usr/bin/env python3
"""Build a classic module ZIP with the official Magisk installer entry points."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parent
source = root / 'qti-framebuffer'
properties = dict(line.split('=', 1) for line in (source / 'module.prop').read_text().splitlines() if '=' in line)
dest = root.parent / ('app/src/main/assets/EdgeMask-QTI-Compat-' + properties['version'] + '.zip')
files = ('module.prop', 'supported-libraries.txt', 'customize.sh', 'post-fs-data.sh', 'skip_mount',
         'META-INF/com/google/android/update-binary', 'META-INF/com/google/android/updater-script',
         'LICENSE-Magisk', 'NOTICE')
dest.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED, compresslevel=9, allowZip64=False) as archive:
    for name in files:
        entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        entry.create_system = 3
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = (0o100755 if name.endswith('.sh') or name.endswith('/update-binary') else 0o100644) << 16
        archive.writestr(entry, (source / name).read_bytes())
print(dest.name, dest.stat().st_size, hashlib.sha256(dest.read_bytes()).hexdigest())
