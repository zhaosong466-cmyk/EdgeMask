#!/usr/bin/env python3
"""Package only reviewed text files. No user ROM binaries or diagnostic data."""
from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parent
source = root / 'qti-framebuffer'
dest = root.parent / 'app/src/main/assets/EdgeMask-QTI-Compat-1.0.zip'
files = ('module.prop', 'supported-libraries.txt', 'customize.sh', 'post-fs-data.sh', 'skip_mount')
dest.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name in files:
        entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
        entry.create_system = 3
        entry.compress_type = zipfile.ZIP_DEFLATED
        entry.external_attr = (0o100755 if name.endswith('.sh') else 0o100644) << 16
        archive.writestr(entry, (source / name).read_bytes())
print(dest.name, dest.stat().st_size, hashlib.sha256(dest.read_bytes()).hexdigest())
