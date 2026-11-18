package com.moon.moonstream.index.parse

import java.util.concurrent.ConcurrentHashMap

/**
 * ZIP-aware split playback (moon's 2026-11-18, TeleStremio-style).
 *
 * Big titles often arrive as multi-volume zips (`Movie.zip.001`, `.002`,
 * …). The parts join into ONE zip file — and a video player cannot play a
 * zip. But media zips are almost always packed STORED (method 0: no
 * compression), so the video sits inside byte-for-byte, just with a small
 * header in front of it. This probe reads that header, finds where the
 * video data starts and how big it is, and the players then serve only
 * those bytes — the video plays with full seeking, no extraction, no
 * extra download. The same trick TeleStremio web uses.
 *
 * Only STORED entries stream this way: a DEFLATE-compressed entry's bytes
 * are not the video's bytes, so seeking into the middle is impossible
 * without decompressing from the start. Those report [Probe.Compressed].
 *
 * Pure Kotlin, no Android APIs: the caller supplies a `readAt` that reads
 * the JOINED byte stream (across split parts) at a global offset. Both
 * the in-app player (TdByteWindow) and the addon server
 * (backend.readFileRange) use this same probe.
 *
 * Format notes (APPNOTE):
 * - A split archive's first part may start with a 4-byte spanning
 *   signature (`PK\x07\x08`) before the first local header.
 * - Sizes over 4 GB don't fit the 32-bit fields: they read 0xFFFFFFFF
 *   and the real 8-byte values live in the ZIP64 extra field (id 0x0001),
 *   in order uncompressed, compressed (then local offset, disk in the
 *   central directory). Moon's 10 GB splits hit this constantly.
 * - When general-purpose flag bit 3 is set, the local header's sizes are
 *   0 and the real sizes live in the central directory at the archive's
 *   end — the probe follows the End Of Central Directory record (and the
 *   ZIP64 EOCD locator/record when the directory sits past 4 GB).
 * - Central-directory offsets are relative to the start of the disk
 *   holding the entry/directory, so [probe] takes the part sizes to map
 *   disk numbers to global offsets.
 */
object ZipStream {

    /** Outcome of probing a (possibly split) zip for a streamable video. */
    sealed interface Probe {
        /** The inner video streams from [Info.dataOffset] for [Info.videoSize] bytes. */
        data class Playable(val info: Info) : Probe

        /** A zip, but the video entry is compressed — offset streaming can't work. */
        data object Compressed : Probe

        /** Not a zip, or the headers couldn't be read — caller falls back to plain bytes. */
        data object Unreadable : Probe
    }

    /** Where the inner video lives inside the joined archive. */
    data class Info(
        /** Byte offset of the video data inside the JOINED archive. */
        val dataOffset: Long,
        /** The video's size in bytes. */
        val videoSize: Long,
        /** The video's file name inside the zip (for the MIME hint). */
        val innerName: String,
    )

    /** Video extensions worth streaming out of a zip. */
    private val VIDEO_EXTS = setOf(
        "mkv", "mka", "mk3d", "mp4", "m4v", "mov", "3gp",
        "avi", "webm", "ts", "m2ts", "mts", "flv", "wmv",
    )

    /** A joined file is a zip when its base name ends in `.zip`. */
    fun isZipArchive(baseName: String): Boolean =
        baseName.substringAfterLast('.', "").lowercase() == "zip"

    // ZIP signatures (little-endian u32).
    private const val SIG_LOCAL = 0x04034b50
    private const val SIG_CENTRAL = 0x02014b50
    private const val SIG_EOCD = 0x06054b50
    private const val SIG_SPANNING = 0x08074b50
    private const val SIG_ZIP64_EOCD = 0x06064b50
    private const val SIG_ZIP64_LOCATOR = 0x07064b50
    private const val METHOD_STORED = 0
    private const val FLAG_DATA_DESCRIPTOR = 0x08
    private const val ZIP64_EXTRA_ID = 0x0001
    private const val U32_MAX = 0xFFFFFFFFL
    private const val U16_MAX = 0xFFFF

    /** Bytes read from the head: spanning signature + local header + name + extra. */
    private const val HEAD_BYTES = 8192

    /** Bytes read from the tail when hunting the End Of Central Directory record. */
    private const val TAIL_BYTES = 65536 + 22

    /** Cap on central-directory bytes read when hunting a video entry. */
    private const val MAX_CD_BYTES = 262144

    // ---- little-endian readers (null when out of bounds) ----

    private fun u16(b: ByteArray, off: Int): Int? {
        if (off < 0 || off + 2 > b.size) return null
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(b: ByteArray, off: Int): Long? {
        if (off < 0 || off + 4 > b.size) return null
        return (b[off].toLong() and 0xFF) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 3].toLong() and 0xFF) shl 24)
    }

    private fun u64(b: ByteArray, off: Int): Long? {
        if (off < 0 || off + 8 > b.size) return null
        var v = 0L
        for (i in 0 until 8) v = v or ((b[off + i].toLong() and 0xFF) shl (8 * i))
        return v
    }

    private fun ascii(b: ByteArray, off: Int, len: Int): String? {
        if (off < 0 || len < 0 || off + len > b.size) return null
        return try {
            String(b, off, len, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Positional reader over one ZIP64 extra block (id 0x0001). Values sit
     * in spec order — uncompressed size, compressed size, local header
     * offset (central directory only), disk start number — each present
     * only when its 32-bit field reads all-ones, so the caller pulls them
     * in that same order, one per overflowed field.
     */
    private class Zip64Reader(extra: ByteArray) {
        private val block: ByteArray
        private var pos = 0

        init {
            var off = 0
            var found: ByteArray? = null
            while (off + 4 <= extra.size) {
                val id = u16(extra, off) ?: break
                val size = u16(extra, off + 2) ?: break
                if (id == ZIP64_EXTRA_ID) {
                    val end = minOf(extra.size, off + 4 + size)
                    found = extra.copyOfRange(off + 4, end)
                    break
                }
                off += 4 + size
            }
            block = found ?: ByteArray(0)
        }

        fun next64(): Long? {
            if (pos + 8 > block.size) return null
            val v = u64(block, pos) ?: return null
            pos += 8
            return v
        }
    }

    private data class LocalEntry(
        val method: Int,
        val hasDataDescriptor: Boolean,
        /** -1 when unknown (data descriptor: sizes live in the central directory). */
        val compSize: Long,
        val uncompSize: Long,
        val name: String,
        /** Global offset of the file data (header start + 30 + name + extra). */
        val dataOffset: Long,
    )

    /**
     * Parses the local file header at the start of [head] (whose byte 0 is
     * global offset [base]). Handles the optional 4-byte spanning signature
     * a split archive carries before its first header. Null when the bytes
     * aren't a local header.
     */
    private fun parseLocal(head: ByteArray, base: Long): LocalEntry? {
        var pos = 0
        if (u32(head, 0) == SIG_SPANNING.toLong()) pos = 4
        if (u32(head, pos) != SIG_LOCAL.toLong()) return null
        val flags = u16(head, pos + 6) ?: return null
        val method = u16(head, pos + 8) ?: return null
        val comp32 = u32(head, pos + 18) ?: return null
        val uncomp32 = u32(head, pos + 22) ?: return null
        val nameLen = u16(head, pos + 26) ?: return null
        val extraLen = u16(head, pos + 28) ?: return null
        val name = ascii(head, pos + 30, nameLen) ?: return null
        val extraStart = pos + 30 + nameLen
        if (extraStart + extraLen > head.size) return null
        val extra = head.copyOfRange(extraStart, extraStart + extraLen)
        val hasDescriptor = (flags and FLAG_DATA_DESCRIPTOR) != 0
        val (comp, uncomp) = if (hasDescriptor) {
            -1L to -1L
        } else {
            val z64 = Zip64Reader(extra)
            var u = uncomp32
            var c = comp32
            if (u == U32_MAX) u = z64.next64() ?: return null
            if (c == U32_MAX) c = z64.next64() ?: return null
            c to u
        }
        return LocalEntry(
            method = method,
            hasDataDescriptor = hasDescriptor,
            compSize = comp,
            uncompSize = uncomp,
            name = name,
            dataOffset = base + pos + 30 + nameLen + extraLen,
        )
    }

    private data class CentralDir(
        /** Global offset of the first central-directory entry. */
        val start: Long,
        /** Total entries in the directory. */
        val entries: Long,
    )

    /**
     * Locates the central directory from the archive tail: finds the End Of
     * Central Directory record (validating it ends exactly at the archive
     * end), then follows the ZIP64 locator/record when the 32-bit fields
     * overflow — any archive whose central directory sits past 4 GB, which
     * is every 10 GB split of moon's.
     */
    private fun findCentralDir(
        totalSize: Long,
        partStarts: LongArray,
        readAt: (Long, Int) -> ByteArray,
    ): CentralDir? {
        val tailSize = minOf(totalSize, TAIL_BYTES.toLong()).toInt()
        val tailBase = totalSize - tailSize
        val tail = try {
            readAt(tailBase, tailSize)
        } catch (e: Exception) {
            return null
        }
        if (tail.size < 22) return null
        // Search backwards for the EOCD signature; the true record is the
        // one whose comment ends exactly at the archive end.
        var eocdAt = -1
        var i = tail.size - 22
        while (i >= 0) {
            if (u32(tail, i) == SIG_EOCD.toLong()) {
                val commentLen = u16(tail, i + 20) ?: break
                if (i + 22 + commentLen == tail.size) {
                    eocdAt = i
                    break
                }
            }
            i--
        }
        if (eocdAt < 0) return null
        val cdDisk = u16(tail, eocdAt + 6) ?: return null
        val totalEntries = u16(tail, eocdAt + 10) ?: return null
        val cdSize32 = u32(tail, eocdAt + 12) ?: return null
        val cdOffset32 = u32(tail, eocdAt + 16) ?: return null
        // ZIP64 overflow: follow the locator + record just before the EOCD.
        if (cdOffset32 == U32_MAX || cdSize32 == U32_MAX || totalEntries == U16_MAX) {
            val locAt = eocdAt - 20
            if (locAt < 0) return null
            if (u32(tail, locAt) != SIG_ZIP64_LOCATOR.toLong()) return null
            val zip64Disk = u32(tail, locAt + 4) ?: return null
            val zip64Off = u64(tail, locAt + 8) ?: return null
            val rec = try {
                readAt(diskStart(partStarts, zip64Disk.toInt()) + zip64Off, 56)
            } catch (e: Exception) {
                return null
            }
            if (rec.size < 56 || u32(rec, 0) != SIG_ZIP64_EOCD.toLong()) return null
            // ZIP64 EOCD: sig(4) size(8) verMade(2) verNeed(2) disk(4)
            // cdDisk(4) entriesDisk(8) entriesTotal(8) cdSize(8) cdOffset(8).
            val z64CdDisk = u32(rec, 20) ?: return null
            val entries = u64(rec, 32) ?: return null
            val cdOffset = u64(rec, 48) ?: return null
            return CentralDir(
                start = diskStart(partStarts, z64CdDisk.toInt()) + cdOffset,
                entries = entries,
            )
        }
        return CentralDir(
            start = diskStart(partStarts, cdDisk) + cdOffset32,
            entries = totalEntries.toLong(),
        )
    }

    private data class CentralEntry(
        val method: Int,
        val compSize: Long,
        val uncompSize: Long,
        val name: String,
        /** Global offset of this entry's local header. */
        val localHeaderOffset: Long,
    )

    /**
     * Scans central-directory entries from [cd] (whose byte 0 is global
     * offset [base]) until [want] matches or entries run out. The central
     * directory is authoritative for sizes — it is what the bit-3 fallback
     * and the multi-entry search read.
     */
    private fun scanCentral(
        cd: ByteArray,
        maxEntries: Long,
        partStarts: LongArray,
        want: (CentralEntry) -> Boolean,
    ): CentralEntry? {
        var pos = 0
        var seen = 0L
        while (pos + 46 <= cd.size && seen < maxEntries) {
            if (u32(cd, pos) != SIG_CENTRAL.toLong()) return null
            val method = u16(cd, pos + 10) ?: return null
            val comp32 = u32(cd, pos + 20) ?: return null
            val uncomp32 = u32(cd, pos + 24) ?: return null
            val nameLen = u16(cd, pos + 28) ?: return null
            val extraLen = u16(cd, pos + 30) ?: return null
            val commentLen = u16(cd, pos + 32) ?: return null
            val diskStart = u16(cd, pos + 34) ?: return null
            val localOff32 = u32(cd, pos + 42) ?: return null
            val name = ascii(cd, pos + 46, nameLen) ?: return null
            val extraStart = pos + 46 + nameLen
            if (extraStart + extraLen > cd.size) return null
            val extra = cd.copyOfRange(extraStart, extraStart + extraLen)
            val z64 = Zip64Reader(extra)
            var uncomp = uncomp32
            var comp = comp32
            var localOff = localOff32
            if (uncomp == U32_MAX) uncomp = z64.next64() ?: return null
            if (comp == U32_MAX) comp = z64.next64() ?: return null
            if (localOff == U32_MAX) localOff = z64.next64() ?: return null
            // (Disk start from ZIP64 ignored: >64k disks can't happen here;
            // the 16-bit disk field is used as-is, clamped by diskStart().)
            val entry = CentralEntry(
                method = method,
                compSize = comp,
                uncompSize = uncomp,
                name = name,
                localHeaderOffset = diskStart(partStarts, diskStart) + localOff,
            )
            if (want(entry)) return entry
            pos += 46 + nameLen + extraLen + commentLen
            seen++
        }
        return null
    }

    /** Global start offset of disk [n] (clamped to the last disk). */
    private fun diskStart(partStarts: LongArray, n: Int): Long {
        if (partStarts.isEmpty()) return 0L
        return partStarts[n.coerceIn(0, partStarts.size - 1)]
    }

    private fun centralEntry(
        cd: CentralDir,
        totalSize: Long,
        partStarts: LongArray,
        readAt: (Long, Int) -> ByteArray,
        want: (CentralEntry) -> Boolean,
    ): CentralEntry? {
        if (cd.start < 0 || cd.start >= totalSize) return null
        val bytes = try {
            readAt(cd.start, minOf(totalSize - cd.start, MAX_CD_BYTES.toLong()).toInt())
        } catch (e: Exception) {
            return null
        }
        if (bytes.isEmpty()) return null
        return scanCentral(bytes, cd.entries, partStarts, want)
    }

    private fun playableOrNull(offset: Long, size: Long, name: String, totalSize: Long): Probe.Playable? {
        if (offset < 0L || size <= 0L || offset + size > totalSize) return null
        return Probe.Playable(Info(dataOffset = offset, videoSize = size, innerName = name))
    }

    private fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in VIDEO_EXTS

    /**
     * Probes a joined (possibly split) zip for a streamable inner video.
     *
     * @param totalSize joined size of all parts.
     * @param partSizes size of each part, in play order (disk 0, 1, …).
     * @param readAt reads [size] bytes at global joined [offset]; may return
     *   fewer bytes at EOF. The probe catches anything it throws.
     */
    fun probe(
        totalSize: Long,
        partSizes: List<Long>,
        readAt: (offset: Long, size: Int) -> ByteArray,
    ): Probe {
        if (totalSize <= 0L || partSizes.isEmpty()) return Probe.Unreadable
        val partStarts = LongArray(partSizes.size).also { arr ->
            var acc = 0L
            partSizes.forEachIndexed { i, s -> arr[i] = acc; acc += s }
        }
        val head = try {
            readAt(0L, minOf(totalSize, HEAD_BYTES.toLong()).toInt())
        } catch (e: Exception) {
            return Probe.Unreadable
        }
        if (head.isEmpty()) return Probe.Unreadable
        val first = parseLocal(head, 0L) ?: return Probe.Unreadable
        if (first.method != METHOD_STORED) return Probe.Compressed
        // Fast path: the first entry is the video (the usual media zip).
        if (isVideo(first.name)) {
            if (!first.hasDataDescriptor && first.uncompSize > 0L) {
                return playableOrNull(first.dataOffset, first.uncompSize, first.name, totalSize)
                    ?: Probe.Unreadable
            }
            // Sizes live in the central directory — match the entry by name.
            val cd = findCentralDir(totalSize, partStarts, readAt) ?: return Probe.Unreadable
            val sizes = centralEntry(cd, totalSize, partStarts, readAt) { it.name == first.name }
                ?: return Probe.Unreadable
            if (sizes.method != METHOD_STORED) return Probe.Compressed
            return playableOrNull(first.dataOffset, sizes.uncompSize, first.name, totalSize)
                ?: Probe.Unreadable
        }
        // Multi-entry zip: the video isn't first — find it in the directory.
        val cd = findCentralDir(totalSize, partStarts, readAt) ?: return Probe.Unreadable
        val entry = centralEntry(cd, totalSize, partStarts, readAt) {
            it.method == METHOD_STORED && isVideo(it.name)
        } ?: return Probe.Unreadable
        if (entry.uncompSize <= 0L) return Probe.Unreadable
        // The data offset comes from THAT entry's own local header (its
        // extra field can differ in length from the central copy).
        val localHead = try {
            readAt(entry.localHeaderOffset, HEAD_BYTES)
        } catch (e: Exception) {
            return Probe.Unreadable
        }
        val local = parseLocal(localHead, entry.localHeaderOffset) ?: return Probe.Unreadable
        return playableOrNull(local.dataOffset, entry.uncompSize, entry.name, totalSize)
            ?: Probe.Unreadable
    }

    /** Probe results by joined-file key — Telegram files never change, so a probe stays valid. */
    private val probeCache = ConcurrentHashMap<String, Probe>()
    private const val MAX_CACHE_ENTRIES = 256

    /**
     * Cached probe: [key] identifies the joined file (first part channel +
     * message + joined size). Playable and Compressed cache — the bytes
     * they were read from never change. Unreadable does NOT cache: a
     * transient read failure must not disable zip playback for the rest of
     * the session, and re-probing is one cheap header read.
     */
    fun probeCached(
        key: String,
        totalSize: Long,
        partSizes: List<Long>,
        readAt: (offset: Long, size: Int) -> ByteArray,
    ): Probe {
        probeCache[key]?.let { return it }
        val result = probe(totalSize, partSizes, readAt)
        if (result != Probe.Unreadable) {
            if (probeCache.size >= MAX_CACHE_ENTRIES) probeCache.clear()
            probeCache[key] = result
        }
        return result
    }
}
