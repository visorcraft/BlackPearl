package com.visorcraft.ghostgalleon.rom

/**
 * Switch library dedupe. A Switch folder typically holds
 * one base package per game plus its updates and DLC as separate files;
 * showing all of them in the carousel/picker triples the list. This pure
 * post-scan step groups the switch entries of a library and flags every
 * non-base entry `visibleInUi = false` when its group has a base entry.
 * Hidden entries stay in the stored library and remain addressable by id
 * (grid slots keep working); only UI lists filter them out.
 *
 * Grouping is two-key: every entry has a normalized-title key (all
 * bracket/parenthesis tags, symbol characters, and trailing version tokens
 * stripped) and, when the filename carries a bracketed title id
 * ([01006C300E9F0800]), a title-id key of its first 12 hex digits —
 * Switch title ids share that prefix across base/update/DLC (…000 base,
 * …800 update, DLC offsets beyond). Title groups that share a title id
 * are merged (release abbreviations like "Zelda BoTW" vs "Zelda Breath of
 * the Wild" DLC never share a normalized title). Finally, base-less
 * groups merge into the longest base group their title key extends on a
 * word boundary (VENOM-style "v-sid_meiers_civilization_vii_<pack>_dlc"
 * names carry no title id and no shared full title).
 *
 * Role: title id ending "800" or an Update/UPD/version-tag marker makes an
 * update; a DLC marker (or a title id that is neither …000 nor …800)
 * makes DLC; anything else is a base candidate. Within a group the
 * visible entry is the base candidate with the lowest version; a group
 * with no base at all keeps every entry visible (nothing to hide behind).
 */
object SwitchDedupe {

    private val TITLE_ID = Regex("""\[([0-9A-Fa-f]{16})\]""")
    private val VERSION_TAG = Regex("""\[v(\d+)\]""", RegexOption.IGNORE_CASE)
    private val BRACKET = Regex("""\[[^\]]*\]""")
    private val PAREN = Regex("""\([^)]*\)""")

    private enum class Role { BASE, UPDATE, DLC }

    private class Group {
        val entries = mutableListOf<RomEntry>()
        val roles = mutableMapOf<String, Role>() // entry id -> role
        var hasBase = false

        fun add(entry: RomEntry, role: Role) {
            if (roles.containsKey(entry.id)) return
            entries.add(entry)
            roles[entry.id] = role
            if (role == Role.BASE) hasBase = true
        }

        fun absorb(other: Group) {
            other.entries.forEach { add(it, other.roles.getValue(it.id)) }
            other.entries.clear()
        }
    }

    /** Entries with dedupe applied; non-switch entries pass through. */
    fun apply(entries: List<RomEntry>): List<RomEntry> {
        val switch = entries.filter { it.platformId == Platforms.SWITCH.id }
        if (switch.isEmpty()) return entries

        // Pass 1: group by normalized title.
        val groups = LinkedHashMap<String, Group>()
        switch.forEach { entry ->
            groups.getOrPut(normalizeTitle(entry.name)) { Group() }
                .add(entry, roleOf(entry.name))
        }
        // Pass 2: merge title groups that share a title-id prefix.
        switch.groupBy { titleIdKey(it.name) }.filterKeys { it != null }.values
            .forEach { sharing ->
                val keys = sharing.map { normalizeTitle(it.name) }.distinct()
                if (keys.size > 1) {
                    val targetKey = keys.firstOrNull { groups.getValue(it).hasBase }
                        ?: keys.first()
                    val target = groups.getValue(targetKey)
                    keys.filter { it != targetKey }.forEach { other ->
                        target.absorb(groups.getValue(other))
                        groups.remove(other)
                    }
                }
            }
        // Pass 3: base-less groups join the longest base group their title
        // key extends on a word boundary.
        val orphans = groups.entries.filter { !it.value.hasBase }
        orphans.forEach { (key, g) ->
            val targetKey = groups.keys
                .filter { base ->
                    groups.getValue(base).hasBase &&
                        key.length > base.length && key.startsWith(base) &&
                        key[base.length] == ' '
                }
                .maxByOrNull { it.length }
            if (targetKey != null) {
                groups.getValue(targetKey).absorb(g)
                groups.remove(key)
            }
        }

        val hidden = mutableSetOf<String>()
        groups.values.forEach { g ->
            if (!g.hasBase || g.entries.isEmpty()) return@forEach
            // Prefer the base with the lowest version; every other entry of
            // a group with a base is hidden.
            val keeper = g.entries
                .filter { g.roles[it.id] == Role.BASE }
                .minByOrNull { versionOf(it.name) }
            g.entries.forEach { if (it.id != keeper?.id) hidden.add(it.id) }
        }
        return entries.map { e ->
            if (e.id in hidden) e.copy(visibleInUi = false) else e
        }
    }

    /** First-12-hex title-id group key, or null when the name has no id. */
    internal fun titleIdKey(name: String): String? =
        TITLE_ID.find(name)?.groupValues?.get(1)?.uppercase()?.take(12)

    /**
     * Lowercased title with every […]/(…) tag, trailing version tokens,
     * and symbol characters stripped; whitespace/underscores collapsed.
     */
    internal fun normalizeTitle(name: String): String {
        var s = name.lowercase()
        s = BRACKET.replace(s, " ")
        s = PAREN.replace(s, " ")
        s = s.replace('_', ' ')
        // Hyphens are separators ("Thousand-Year" == "Thousand Year");
        // other symbol characters (®, ™, ©, –, — …) just drop out, so
        // tag-stripped titles from different release sources still match.
        s = s.replace('-', ' ')
        s = s.filter { it.isLetterOrDigit() || it.isWhitespace() }
        val tokens = s.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        // VENOM-style release prefix: "v-sid_meiers_…" / "v-mario_kart_…"
        // carry a leading "v" token that the update/DLC files share with
        // their base — drop it so all three group together.
        val unprefixed = if (tokens.firstOrNull() == "v") tokens.drop(1) else tokens
        val kept = unprefixed.dropLastWhile { isVersionToken(it) }
        return kept.joinToString(" ")
    }

    /** "v1.8.2", "v720896", "1.4.2", "1.0.4" — trailing version noise. */
    private fun isVersionToken(token: String): Boolean =
        token.matches(Regex("""v\d+(\.\d+)*""")) ||
            token.matches(Regex("""\d+\.\d+(\.\d+)*"""))

    private fun roleOf(name: String): Role {
        // Underscores are word characters in regex, so tokenize them away
        // before marker matching ("pack_dlc.nsp" must see "dlc").
        val lower = name.lowercase().replace('_', ' ')
        val hasDlc = Regex("""\bdlc\b""").containsMatchIn(lower)
        val id = TITLE_ID.find(name)?.groupValues?.get(1)?.uppercase()
        if (id != null) {
            return when {
                id.endsWith("800") -> Role.UPDATE
                id.endsWith("000") -> if (hasDlc) Role.DLC else Role.BASE
                else -> Role.DLC
            }
        }
        if (hasDlc) return Role.DLC
        val tagless = BRACKET.replace(lower, " ")
        if (Regex("""\b(update|upd)\b""").containsMatchIn(tagless)) return Role.UPDATE
        val tokens = tagless.trim().split(Regex("""\s+"""))
        if (tokens.lastOrNull()?.let { isVersionToken(it) } == true) return Role.UPDATE
        return Role.BASE
    }

    /** Version number from a [v12345] tag; 0 when absent (a plain base). */
    private fun versionOf(name: String): Long =
        VERSION_TAG.find(name)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
}
