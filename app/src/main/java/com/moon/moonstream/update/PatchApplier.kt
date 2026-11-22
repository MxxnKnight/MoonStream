package com.moon.moonstream.update

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Applies a MoonStream delta-update patch (MSPATCH1 container, built by
 * tools/make_patch.py) to the installed APK and rebuilds the new APK
 * byte-for-byte — so the new APK's release signature stays valid and
 * Android installs it exactly like a full download.
 *
 * Pure Kotlin + java.util.zip/java.security only (no Android classes),
 * so the same file compiles standalone for the sandbox harness.
 *
 * Container layout (keep in sync with make_patch.py):
 *   8 bytes  magic "MSPATCH1"
 *   u16 BE   from-version length N, then N bytes UTF-8
 *   u16 BE   to-version length M, then M bytes UTF-8
 *   i64 LE   new APK size in bytes
 *   32 bytes SHA-256 of the new APK
 *   i64 LE   gzipped control stream length
 *   i64 LE   gzipped diff stream length
 *   i64 LE   gzipped extra stream length
 *   bytes    gzipped control, diff, extra streams (bsdiff control triples;
 *            the diff stream applies byte-wise addition against the old APK)
 *
 * The streams are read sequentially and the old APK is read by range, so
 * applying a 41 MB APK needs only a few MB of RAM — the decompressed diff
 * stream alone is ~38 MB and must never be held whole.
 */
object PatchApplier {
    private val MAGIC = "MSPATCH1".toByteArray(Charsets.US_ASCII)
    private const val CHUNK = 64 * 1024

    class PatchException(message: String) : Exception(message)

    data class PatchHeader(
        val fromVersion: String,
        val toVersion: String,
        val newSize: Long,
        val newSha256: ByteArray,
    )

    private class ParsedHeader(
        val header: PatchHeader,
        val ctrlOffset: Long,
        val diffOffset: Long,
        val extraOffset: Long,
    )

    /** Reads just the header (cheap — the caller checks fromVersion first). */
    fun readHeader(patch: File): PatchHeader {
        return parseHeader(patch).header
    }

    /**
     * Rebuilds the new APK from [oldApk] + [patch] into [outApk].
     * Verifies the SHA-256 of the rebuilt file and throws [PatchException]
     * (deleting the partial output) on any mismatch or format error.
     * Progress is 0..100 over the new file size. Returns the header.
     */
    fun apply(
        oldApk: File,
        patch: File,
        outApk: File,
        onProgress: (Int) -> Unit = {},
    ): PatchHeader {
        val parsed = parseHeader(patch)
        val header = parsed.header
        val oldSize = oldApk.length()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            RandomAccessFile(oldApk, "r").use { old ->
                gzipStream(patch, parsed.ctrlOffset).use { ctrl ->
                    gzipStream(patch, parsed.diffOffset).use { diff ->
                        gzipStream(patch, parsed.extraOffset).use { extra ->
                            FileOutputStream(outApk).use { out ->
                                runLoop(old, oldSize, ctrl, diff, extra, out, digest, header, onProgress)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            outApk.delete()
            if (e is PatchException) throw e
            throw PatchException("Patch failed: ${e.message}")
        }
        if (outApk.length() != header.newSize) {
            outApk.delete()
            throw PatchException(
                "Rebuilt APK is ${outApk.length()} bytes, expected ${header.newSize}",
            )
        }
        if (!digest.digest().contentEquals(header.newSha256)) {
            outApk.delete()
            throw PatchException("Rebuilt APK failed its checksum — patch is corrupt")
        }
        return header
    }

    // ---------- header ----------

    private fun parseHeader(patch: File): ParsedHeader {
        FileInputStream(patch).use { input ->
            val magic = ByteArray(8)
            readFully(input, magic)
            if (!magic.contentEquals(MAGIC)) {
                throw PatchException("Not a MoonStream patch (bad magic)")
            }
            val fromVersion = readString(input)
            val toVersion = readString(input)
            val newSize = readLongLE(input)
            val sha = ByteArray(32)
            readFully(input, sha)
            val ctrlLen = readLongLE(input)
            val diffLen = readLongLE(input)
            readLongLE(input) // extra length — the extra stream runs to EOF
            if (newSize <= 0 || ctrlLen < 0 || diffLen < 0) {
                throw PatchException("Patch header has impossible sizes")
            }
            // Header bytes consumed so far: 8 + 2 + from + 2 + to + 8 + 32 + 24.
            val headerEnd = 76L + fromVersion.toByteArray(Charsets.UTF_8).size +
                toVersion.toByteArray(Charsets.UTF_8).size
            return ParsedHeader(
                PatchHeader(fromVersion, toVersion, newSize, sha),
                ctrlOffset = headerEnd,
                diffOffset = headerEnd + ctrlLen,
                extraOffset = headerEnd + ctrlLen + diffLen,
            )
        }
    }

    private fun readString(input: InputStream): String {
        val len = readShortBE(input)
        val bytes = ByteArray(len)
        readFully(input, bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readShortBE(input: InputStream): Int {
        val b = ByteArray(2)
        readFully(input, b)
        return ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
    }

    private fun readLongLE(input: InputStream): Long {
        val b = ByteArray(8)
        readFully(input, b)
        var v = 0L
        for (i in 7 downTo 0) {
            v = (v shl 8) or (b[i].toLong() and 0xFF)
        }
        return v
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw PatchException("Unexpected end of patch file")
            off += n
        }
    }

    private fun gzipStream(patch: File, offset: Long): InputStream {
        val raw = FileInputStream(patch)
        var skipped = 0L
        while (skipped < offset) {
            val n = raw.skip(offset - skipped)
            if (n <= 0) {
                raw.close()
                throw PatchException("Patch file is shorter than its header claims")
            }
            skipped += n
        }
        return GZIPInputStream(BufferedInputStream(raw))
    }

    // ---------- bspatch loop ----------

    private fun runLoop(
        old: RandomAccessFile,
        oldSize: Long,
        ctrl: InputStream,
        diff: InputStream,
        extra: InputStream,
        out: FileOutputStream,
        digest: MessageDigest,
        header: PatchHeader,
        onProgress: (Int) -> Unit,
    ) {
        val ctrlBuf = ByteArray(24)
        val diffBuf = ByteArray(CHUNK)
        val oldBuf = ByteArray(CHUNK)
        var oldPos = 0L
        var newPos = 0L
        var lastProgress = -1
        while (newPos < header.newSize) {
            readFully(ctrl, ctrlBuf)
            val x = offtin(ctrlBuf, 0)
            val y = offtin(ctrlBuf, 8)
            val z = offtin(ctrlBuf, 16)
            if (x < 0 || y < 0) throw PatchException("Patch control block is corrupt")
            if (oldPos + x > oldSize) throw PatchException("Patch reads past the old APK")
            if (newPos + x + y > header.newSize) {
                throw PatchException("Patch writes past the new APK size")
            }
            // Diff block: new bytes = old bytes + diff bytes (mod 256).
            var remaining = x
            while (remaining > 0) {
                val n = minOf(remaining, CHUNK.toLong()).toInt()
                readFully(diff, diffBuf, n)
                old.seek(oldPos)
                readFully(old, oldBuf, n)
                for (i in 0 until n) {
                    diffBuf[i] = (oldBuf[i].toInt() + diffBuf[i].toInt()).toByte()
                }
                out.write(diffBuf, 0, n)
                digest.update(diffBuf, 0, n)
                oldPos += n
                newPos += n
                remaining -= n
            }
            // Extra block: copied verbatim.
            remaining = y
            while (remaining > 0) {
                val n = minOf(remaining, CHUNK.toLong()).toInt()
                readFully(extra, diffBuf, n)
                out.write(diffBuf, 0, n)
                digest.update(diffBuf, 0, n)
                newPos += n
                remaining -= n
            }
            oldPos += z
            if (oldPos < 0 || oldPos > oldSize) {
                throw PatchException("Patch seeks outside the old APK")
            }
            val p = ((newPos * 100) / header.newSize).toInt().coerceIn(0, 100)
            if (p != lastProgress) {
                lastProgress = p
                onProgress(p)
            }
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray, len: Int) {
        var off = 0
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) throw PatchException("Unexpected end of patch stream")
            off += n
        }
    }

    private fun readFully(file: RandomAccessFile, buf: ByteArray, len: Int) {
        var off = 0
        while (off < len) {
            val n = file.read(buf, off, len - off)
            if (n < 0) throw PatchException("Unexpected end of old APK")
            off += n
        }
    }

    /**
     * bsdiff's signed little-endian int64: the top bit of byte 7 is the
     * sign, the remaining 63 bits are the magnitude.
     */
    private fun offtin(buf: ByteArray, off: Int): Long {
        val b7 = buf[off + 7].toInt() and 0xFF
        var y = (b7 and 0x7F).toLong()
        for (i in 6 downTo 0) {
            y = y * 256 + (buf[off + i].toInt() and 0xFF)
        }
        return if (b7 and 0x80 != 0) -y else y
    }
}
