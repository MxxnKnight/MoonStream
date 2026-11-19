package com.moon.moonstream.index.parse

import com.moon.moonstream.index.db.StreamFileEntity

/**
 * Split-file (.001, .002, …) grouping — shared by the in-app player, the
 * Detail screen and the addon server so all three join the same way.
 *
 * A split is a single video chopped into numbered byte ranges (HJSplit-style
 * raw splits, NOT 7-Zip archives — those are compressed and cannot stream).
 * Parts share a base name (the file name with the .NNN suffix stripped) and
 * the same season/episode. Part order is NUMERIC (.2 before .10), not
 * lexicographic — plain filename sorting puts .10 before .2.
 */
object SplitFiles {
    /** Trailing split suffix: `.001`, `.mkv.001`, `.mp4.002`, … */
    private val splitSuffix = Regex("""(?i)\.(?:(?:mkv|mp4|avi|webm)\.)?0*\d{1,3}$""")

    /** The trailing part number, if any. */
    private val partNumberRe = Regex("""(?i)0*(\d{1,3})$""")

    fun isSplitPart(fileName: String): Boolean = splitSuffix.containsMatchIn(fileName)

    /** File name with the split suffix removed (`Movie.mkv.001` → `Movie.mkv`). */
    fun baseName(fileName: String): String = splitSuffix.replace(fileName, "")

    fun partNumber(fileName: String): Int? =
        partNumberRe.find(fileName)?.groupValues?.get(1)?.toIntOrNull()

    /** Parts in play order: numeric part number first, file name as tiebreak. */
    fun sortParts(files: List<StreamFileEntity>): List<StreamFileEntity> =
        files.sortedWith(
            compareBy({ partNumber(it.fileName) ?: Int.MAX_VALUE }, { it.fileName }),
        )

    /**
     * The full part sequence for [file] within [files], in play order — or
     * just [file] when it isn't a split part, or when no siblings are in
     * the list (a lone .001 plays as the single part it is).
     */
    fun sequenceFor(files: List<StreamFileEntity>, file: StreamFileEntity): List<StreamFileEntity> {
        if (!isSplitPart(file.fileName)) return listOf(file)
        val base = baseName(file.fileName)
        val seq = sortParts(
            files.filter { f ->
                baseName(f.fileName) == base &&
                    f.season == file.season && f.episode == file.episode
            },
        )
        return if (seq.size > 1) seq else listOf(file)
    }

    /**
     * Groups [files] for the Detail screen: the parts of one split
     * file (same base name + season + episode) become one group, in
     * play order; everything else stays a group of one. Order is the
     * input order — a group sits where its first part sat. A lone
     * .001 (no siblings in the list) stays a single, like
     * sequenceFor.
     */
    fun groupFiles(files: List<StreamFileEntity>): List<List<StreamFileEntity>> {
        val runs = files.filter { isSplitPart(it.fileName) }
            .groupBy { Triple(baseName(it.fileName), it.season, it.episode) }
            .filterValues { it.size > 1 }
            .mapValues { sortParts(it.value) }
        val emitted = mutableSetOf<Triple<String, Int?, Int?>>()
        val out = mutableListOf<List<StreamFileEntity>>()
        for (f in files) {
            if (!isSplitPart(f.fileName)) {
                out.add(listOf(f))
                continue
            }
            val key = Triple(baseName(f.fileName), f.season, f.episode)
            val run = runs[key]
            if (run == null) {
                out.add(listOf(f)) // lone part, no siblings here
                continue
            }
            if (emitted.add(key)) out.add(run)
        }
        return out
    }
}
