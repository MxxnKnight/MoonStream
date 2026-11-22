#!/usr/bin/env python3
"""Build a MoonStream delta-update patch between two release APKs.

Usage:
    make_patch.py <old.apk> <new.apk> <from_version> <to_version> <out.patch>

The patch is a bsdiff between the two APKs, re-packaged so the Android
side needs no bzip2 library (Android has no built-in bzip2 decoder):

  * bsdiff4 generates the standard BSDIFF40 patch (three bzip2 streams:
    control, diff, extra).
  * This script decompresses the three streams and re-compresses each
    with gzip, which java.util.zip reads natively.

The rebuilt APK is byte-identical to the new APK, so its release
signature stays valid — Android installs it exactly like a full download.

Container format (MSPATCH1) — PatchApplier.kt reads this, keep in sync:
    8 bytes  magic "MSPATCH1"
    u16 BE   from-version length N, then N bytes UTF-8 (e.g. "0.15.167")
    u16 BE   to-version length M, then M bytes UTF-8
    i64 LE   new APK size in bytes
    32 bytes SHA-256 of the new APK (verified after applying)
    i64 LE   gzipped control stream length
    i64 LE   gzipped diff stream length
    i64 LE   gzipped extra stream length
    bytes    gzipped control stream, gzipped diff stream, gzipped extra stream

The control stream holds bsdiff control triples (x, y, z as signed
little-endian int64, sign-magnitude: the top bit of byte 7 is the sign).
Requires the `bsdiff4` package (pip install bsdiff4).
"""

import bz2
import gzip
import hashlib
import struct
import sys

import bsdiff4

MAGIC = b"MSPATCH1"


def split_bsdiff(patch: bytes):
    """Split a BSDIFF40 patch into its three decompressed streams."""
    if patch[:8] != b"BSDIFF40":
        raise ValueError("not a BSDIFF40 patch")
    ctrl_len, diff_len, _new_size = struct.unpack("<qqq", patch[8:32])
    ctrl = bz2.decompress(patch[32:32 + ctrl_len])
    diff = bz2.decompress(patch[32 + ctrl_len:32 + ctrl_len + diff_len])
    extra = bz2.decompress(patch[32 + ctrl_len + diff_len:])
    return ctrl, diff, extra


def main(argv):
    if len(argv) != 6:
        print(__doc__.splitlines()[2])
        return 2
    old_apk, new_apk, from_ver, to_ver, out_path = argv[1:6]
    with open(old_apk, "rb") as f:
        old_bytes = f.read()
    with open(new_apk, "rb") as f:
        new_bytes = f.read()
    new_size = len(new_bytes)
    new_sha256 = hashlib.sha256(new_bytes).digest()
    raw = bsdiff4.diff(old_bytes, new_bytes)
    ctrl, diff, extra = split_bsdiff(raw)
    ctrl_gz = gzip.compress(ctrl, mtime=0)
    diff_gz = gzip.compress(diff, mtime=0)
    extra_gz = gzip.compress(extra, mtime=0)
    from_b = from_ver.encode("utf-8")
    to_b = to_ver.encode("utf-8")
    with open(out_path, "wb") as f:
        f.write(MAGIC)
        f.write(struct.pack(">H", len(from_b)))
        f.write(from_b)
        f.write(struct.pack(">H", len(to_b)))
        f.write(to_b)
        f.write(struct.pack("<q", new_size))
        f.write(new_sha256)
        f.write(struct.pack("<qqq", len(ctrl_gz), len(diff_gz), len(extra_gz)))
        f.write(ctrl_gz)
        f.write(diff_gz)
        f.write(extra_gz)
    print(f"patch {out_path}: {len(ctrl_gz) + len(diff_gz) + len(extra_gz)} bytes "
          f"of streams, new APK {new_size} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
