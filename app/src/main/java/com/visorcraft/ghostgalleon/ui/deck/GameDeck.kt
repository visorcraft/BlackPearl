package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.art.ArtTile
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.BrowseFeedback
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.GameDetails
import com.visorcraft.ghostgalleon.library.HiddenRoms
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.library.SessionMath
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomLauncher
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.GridSlots
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.state.DeckState

class GameDeck(
    private val activity: AppCompatActivity,
    private val state: DeckState,
    private val settings: Settings,
    private val library: AppLibrary,
    private val iconLoader: AppIconLoader,
    private val roms: List<RomEntry>,
) : Deck {

    // One carousel entry: a curated-grid app (key = package name) or a
    // scanned ROM (key = "rom:<id>"). Labels render on the card; the hero
    // panel follows the key via DeckState.selectedKey.
    private data class CarouselEntry(
        val key: String,
        val label: String,
        val appPackage: String?,
        val rom: RomEntry?,
    )

    // Curated apps (when browse mode is ALL and no platform/genre filter/search),
    // then browsed ROMs via LibraryBrowse (platform / genre / search / recent /
    // top / A–Z / unplayed / fav). Ranked modes interleave apps by the same maps.
    // Genre is ROM-only: when set, app interleaving is skipped.
    private val entries: List<CarouselEntry> by lazy {
        // Drop power-user modes if chrome toggles turned them off mid-session.
        val q = settings.browseChrome.sanitize(state.libraryBrowse)
        if (q != state.libraryBrowse) {
            state.setLibraryBrowse(q, force = true)
        }
        val appsOk = q.platformId == null && q.genre.isNullOrBlank()
        val browsed = LibraryBrowse.browseRoms(
            roms, q,
            lastLaunchedMs = settings.lastLaunchedMs,
            favorites = settings.favorites,
            collections = settings.collections,
            playtimeMs = settings.playtimeMs,
            hiddenRomIds = settings.hiddenRomIds,
            nowMs = System.currentTimeMillis(),
        ).map {
            CarouselEntry(SlotKey.rom(it.id), it.name, null, it)
        }
        when {
            q.mode == LibraryBrowse.Mode.COLLECTION -> {
                // Walk member keys in user order (apps + ROMs interleaved).
                val name = q.collectionName.orEmpty()
                val keys = settings.collections[name].orEmpty()
                val byPkg = library.curated(settings).associateBy { it.packageName }
                val byRomId = roms.associateBy { it.id }
                val hidden = settings.hiddenRomIds
                val needle = q.text.trim()
                keys.mapNotNull { k ->
                    val entry = SlotKey.romId(k)?.let { id ->
                        if (id in hidden) return@mapNotNull null
                        val rom = byRomId[id] ?: return@mapNotNull null
                        if (q.platformId != null && rom.platformId != q.platformId) {
                            return@mapNotNull null
                        }
                        if (!LibraryBrowse.matchesGenre(rom, q.genre)) {
                            return@mapNotNull null
                        }
                        CarouselEntry(SlotKey.rom(rom.id), rom.name, null, rom)
                    } ?: byPkg[k]?.let {
                        // Platform/genre chips are ROM-only — drop apps when set.
                        if (q.platformId != null || !q.genre.isNullOrBlank()) {
                            return@mapNotNull null
                        }
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                    if (needle.isNotEmpty() && entry != null) {
                        val hit = entry.label.contains(needle, ignoreCase = true) ||
                            entry.key.contains(needle, ignoreCase = true)
                        if (!hit) return@mapNotNull null
                    }
                    entry
                }
            }
            q.mode == LibraryBrowse.Mode.RECENT &&
                appsOk && q.text.isBlank() -> {
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.lastLaunchedMs.keys
                    .filter { !SlotKey.isRom(it) && it in byPkg }
                val recentApps = LibraryBrowse.orderByRecent(
                    appKeys, settings.lastLaunchedMs,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                // Apps + ROMs both ordered by recency: merge by lastLaunched.
                (recentApps + browsed).sortedByDescending {
                    settings.lastLaunchedMs[it.key] ?: 0L
                }
            }
            q.mode == LibraryBrowse.Mode.PLAYED_THIS_WEEK &&
                appsOk && q.text.isBlank() -> {
                val now = System.currentTimeMillis()
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.lastLaunchedMs.keys
                    .filter { !SlotKey.isRom(it) && it in byPkg }
                val weekApps = LibraryBrowse.filterPlayedInWindow(
                    appKeys, settings.lastLaunchedMs, nowMs = now,
                    windowMs = LibraryBrowse.WEEK_WINDOW_MS,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                (weekApps + browsed).sortedByDescending {
                    settings.lastLaunchedMs[it.key] ?: 0L
                }
            }
            q.mode == LibraryBrowse.Mode.PLAYED_THIS_MONTH &&
                appsOk && q.text.isBlank() -> {
                val now = System.currentTimeMillis()
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.lastLaunchedMs.keys
                    .filter { !SlotKey.isRom(it) && it in byPkg }
                val monthApps = LibraryBrowse.filterPlayedInWindow(
                    appKeys, settings.lastLaunchedMs, nowMs = now,
                    windowMs = LibraryBrowse.MONTH_WINDOW_MS,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                (monthApps + browsed).sortedByDescending {
                    settings.lastLaunchedMs[it.key] ?: 0L
                }
            }
            q.mode == LibraryBrowse.Mode.MOST_PLAYED &&
                appsOk && q.text.isBlank() -> {
                val byPkg = library.curated(settings)
                    .associateBy { it.packageName }
                val appKeys = settings.playtimeMs
                    .filter { (k, v) ->
                        v > 0L && !SlotKey.isRom(k) && k in byPkg
                    }
                    .keys
                    .toList()
                val topApps = LibraryBrowse.orderByPlaytime(
                    appKeys, settings.playtimeMs,
                ).mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
                (topApps + browsed).sortedByDescending {
                    settings.playtimeMs[it.key] ?: 0L
                }
            }
            // Recently installed: all non-hidden launchable apps by firstInstallTime
            // (not just curated grid). Platform chips are ROM-only → empty here.
            q.mode == LibraryBrowse.Mode.RECENTLY_INSTALLED &&
                appsOk -> {
                val apps = library.visible(settings).let { list ->
                    if (q.text.isBlank()) list
                    else {
                        val needle = q.text.trim()
                        list.filter {
                            it.label.contains(needle, ignoreCase = true) ||
                                it.packageName.contains(needle, ignoreCase = true)
                        }
                    }
                }
                val installMap = apps.associate { it.packageName to it.firstInstallMs }
                val ordered = LibraryBrowse.orderByInstallTime(
                    apps.map { it.packageName },
                    installMap,
                )
                val byPkg = apps.associateBy { it.packageName }
                ordered.mapNotNull { pkg ->
                    byPkg[pkg]?.let {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                }
            }
            // Games: CATEGORY_GAME apps (all visible, not only curated) + ROMs.
            q.mode == LibraryBrowse.Mode.GAMES &&
                appsOk -> {
                val apps = LibraryBrowse.filterGameApps(library.visible(settings)) { it.isGame }
                    .let { list ->
                        if (q.text.isBlank()) list
                        else {
                            val needle = q.text.trim()
                            list.filter {
                                it.label.contains(needle, ignoreCase = true) ||
                                    it.packageName.contains(needle, ignoreCase = true)
                            }
                        }
                    }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                LibraryBrowse.orderByName(apps + browsed) { it.label }
            }
            q.mode == LibraryBrowse.Mode.ALPHA &&
                appsOk && q.text.isBlank() -> {
                val apps = library.curated(settings).map {
                    CarouselEntry(it.packageName, it.label, it.packageName, null)
                }
                LibraryBrowse.orderByName(apps + browsed) { it.label }
            }
            q.mode == LibraryBrowse.Mode.UNPLAYED &&
                appsOk && q.text.isBlank() -> {
                val apps = library.curated(settings)
                    .filter {
                        LibraryBrowse.isUnplayed(it.packageName, settings.lastLaunchedMs)
                    }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                LibraryBrowse.orderByName(apps + browsed) { it.label }
            }
            q.mode == LibraryBrowse.Mode.FAVORITES &&
                appsOk -> {
                val favApps = library.curated(settings)
                    .filter { it.packageName in settings.favorites }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                favApps + browsed
            }
            q.mode == LibraryBrowse.Mode.ALL &&
                appsOk &&
                q.text.isBlank() -> {
                library.curated(settings).map {
                    CarouselEntry(it.packageName, it.label, it.packageName, null)
                } + browsed
            }
            // Unified text search: include matching curated apps when there is
            // no platform/genre chip (those filters are ROM-only).
            q.text.isNotBlank() && appsOk &&
                q.mode != LibraryBrowse.Mode.COLLECTION -> {
                val needle = q.text.trim()
                val matchedApps = library.curated(settings)
                    .filter {
                        it.label.contains(needle, ignoreCase = true) ||
                            it.packageName.contains(needle, ignoreCase = true)
                    }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                // Keep A–Z / unplayed semantics under search when those chips
                // are active; otherwise curated order + browsed ROMs.
                when (q.mode) {
                    LibraryBrowse.Mode.ALPHA ->
                        LibraryBrowse.orderByName(matchedApps + browsed) { it.label }
                    LibraryBrowse.Mode.UNPLAYED -> {
                        val apps = matchedApps.filter {
                            LibraryBrowse.isUnplayed(it.key, settings.lastLaunchedMs)
                        }
                        LibraryBrowse.orderByName(apps + browsed) { it.label }
                    }
                    else -> matchedApps + browsed
                }
            }
            else -> browsed
        }
    }
    private val nav get() = CarouselNavigation(entries.size)
    private val dockNav get() = DockNavigation(
        DockSlots.visibleCount(dockMoveWorking ?: settings.dockSlots), 0, 1)
    private var recycler: RecyclerView? = null
    private var dockBar: DockBar? = null
    private var hintView: TextView? = null
    private var rootView: FrameLayout? = null
    /** Letter-jump chips (A–Z / #) when ALPHA/UNPLAYED; repainted on selection. */
    private var letterChipViews: List<Pair<Char, TextView>> = emptyList()

    // Modals (at most one at a time): dock slot menu, app picker.
    private var slotMenu: SlotMenu? = null
    private var picker: AppPicker? = null

    // Dock move mode: a lifted dock tile swaps slots left/right until
    // dropped (saved to settings) or cancelled (working copy discarded).
    private var dockMoveIndex: Int? = null
    private var dockMoveWorking: MutableList<String?>? = null

    private fun selectedIndex(): Int =
        entries.indexOfFirst { it.key == state.selectedKey }.coerceAtLeast(0)

    override fun primaryView(context: Context): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // FrameLayout root so modals (dock slot menu, app picker) can sit
        // on top of the whole deck.
        val platformFilter = state.libraryBrowse.platformId
        val root = FrameLayout(context).apply {
            // Per-platform visual cue when a platform chip is active.
            setBackgroundColor(
                platformFilter
                    ?.takeIf { com.visorcraft.ghostgalleon.rom.PlatformLook.hasFilter(it) }
                    ?.let { com.visorcraft.ghostgalleon.rom.PlatformLook.wallpaperTint(it) }
                    ?: Color.BLACK,
            )
            clipChildren = false
            clipToPadding = false
        }
        rootView = root
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        platformFilter?.takeIf { com.visorcraft.ghostgalleon.rom.PlatformLook.hasFilter(it) }?.let { pid ->
            content.addView(TextView(context).apply {
                text = "Platform · " +
                    com.visorcraft.ghostgalleon.rom.PlatformLook.filterBadge(pid)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(com.visorcraft.ghostgalleon.rom.PlatformLook.accentColor(pid))
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        state.libraryBrowse.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { genre ->
            content.addView(TextView(context).apply {
                text = "Genre · $genre"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(settings.accentColor)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        content.addView(
            buildBrowseBar(context, ::dp),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        // A–Z / New (unplayed) rails: 3DS-style letter jump strip under chips.
        buildLetterJumpBar(context, ::dp)?.let { letterBar ->
            content.addView(
                letterBar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = CardAdapter(context, dp(settings.cardSizeDp), dp(12), dp(8))
            LinearSnapHelper().attachToRecyclerView(this)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            clipChildren = false
            clipToPadding = false
        }
        recycler = rv
        content.addView(rv, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (settings.showHints) {
            val hints = HintBar.build(context) as TextView
            hints.text = HintBar.textFor(state.dockSlot != null)
            hintView = hints
            content.addView(hints, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Same dock bar as the grid deck (no page dots in game mode):
        // NAV DOWN from the carousel focuses it, UP returns.
        val bar = DockBar(
            activity, settings, library, iconLoader, roms,
            slots = { dockMoveWorking ?: settings.dockSlots },
            onTap = ::onDockTap,
            onLongPress = ::onDockLongPress,
        )
        dockBar = bar
        content.addView(bar.build(context, pageDots = null))
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Compact clock/battery overlay (system status bar is hidden).
        if (settings.browseChrome.deckStatusPill) {
            root.addView(
                StatusPill.build(context, compact = true),
                StatusPill.overlayLayoutParams(context),
            )
        }
        // A rebuild while the dock holds focus must repaint the ring
        // immediately — updateFocus otherwise only runs on selection updates.
        bar.updateFocus(state.dockSlot)
        rv.post { scrollSelectionToCenter(rv) }
        return root
    }

    // Centers the selected card deterministically: cancel competing
    // scrolls, jump near the target if it is not laid out yet, then glide
    // the residual distance to the exact snap center once layout catches up.
    private fun scrollSelectionToCenter(rv: RecyclerView) {
        rv.stopScroll()
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val index = selectedIndex()
        if (lm.findViewByPosition(index) == null) {
            lm.scrollToPositionWithOffset(index, rv.width / 2)
        }
        rv.post {
            val view = lm.findViewByPosition(index) ?: return@post
            val distance = LinearSnapHelper().calculateDistanceToFinalSnap(lm, view)
            if (distance != null && (distance[0] != 0 || distance[1] != 0)) {
                rv.smoothScrollBy(distance[0], distance[1])
            }
        }
    }

    override fun updateSelection(): Boolean {
        val rv = recycler ?: return false
        // Rebind visible cards so ring/scale move to the new selection,
        // then run the existing scroll-to-center alignment.
        rv.adapter?.notifyDataSetChanged()
        scrollSelectionToCenter(rv)
        paintLetterJumpSelection()
        // Dock focus is a selection change too: repaint the dock ring (and
        // the lifted tile's pulse during a dock move) and switch the hint
        // bar between carousel and dock actions.
        dockBar?.updateFocus(state.dockSlot, dockMoveIndex)
        hintView?.text = if (dockMoveIndex != null) {
            HintBar.MOVE_TEXT
        } else {
            HintBar.textFor(state.dockSlot != null)
        }
        return true
    }

    private fun paintLetterJumpSelection() {
        if (letterChipViews.isEmpty()) return
        val selectedLetter = entries.getOrNull(selectedIndex())
            ?.label
            ?.let { LibraryBrowse.letterBucket(it) }
        letterChipViews.forEach { (letter, tv) ->
            val on = letter == selectedLetter
            tv.setTextColor(if (on) Color.BLACK else Color.WHITE)
            tv.setBackgroundColor(
                if (on) settings.accentColor
                else TileBackgrounds.chipIdleColor(activity),
            )
        }
    }

    override fun handleAction(action: Action): Boolean {
        slotMenu?.let { return it.handleAction(action) }
        picker?.let { return it.handleAction(action) }
        dockMoveIndex?.let { return handleDockMoveAction(action, it) }
        state.dockSlot?.let { return handleDockAction(action, it) }
        return when (action) {
            Action.CONFIRM -> {
                // Launch the DeckState selection, not merely carousel index 0
                // when selectedKey is outside the current filter (Random /
                // Continue / chip changes can select a key not in entries).
                val key = state.selectedKey
                if (key != null) {
                    launchSlotKey(activity, state, roms, key)
                }
                true
            }
            Action.NAV_LEFT, Action.NAV_RIGHT, Action.PAGE_PREV, Action.PAGE_NEXT -> {
                val newIndex = nav.move(selectedIndex(), action)
                entries.getOrNull(newIndex)?.let { state.select(it.key) }
                recycler?.smoothScrollToPosition(newIndex)
                true
            }
            // NAV DOWN leaves the carousel and focuses the dock.
            Action.NAV_DOWN -> {
                state.focusDock(0)
                true
            }
            else -> false
        }
    }

    /**
     * Letter jump strip for A–Z ordered rails (ALPHA + UNPLAYED). Tapping a
     * letter selects the first carousel entry in that bucket and recenters.
     * Hidden when the rail is empty or mode is not letter-ordered.
     */
    private fun buildLetterJumpBar(context: Context, dp: (Int) -> Int): View? {
        val mode = state.libraryBrowse.mode
        if (mode != LibraryBrowse.Mode.ALPHA && mode != LibraryBrowse.Mode.UNPLAYED) {
            letterChipViews = emptyList()
            return null
        }
        val labels = entries.map { it.label }
        val letters = LibraryBrowse.presentLetterIndex(labels)
        if (letters.isEmpty()) {
            letterChipViews = emptyList()
            return null
        }
        val selectedLetter = entries.getOrNull(selectedIndex())
            ?.label
            ?.let { LibraryBrowse.letterBucket(it) }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(0), dp(12), dp(6))
        }
        val chips = mutableListOf<Pair<Char, TextView>>()
        letters.forEachIndexed { i, letter ->
            if (i > 0) {
                row.addView(View(context), LinearLayout.LayoutParams(dp(4), 1))
            }
            val on = letter == selectedLetter
            val chip = TextView(context).apply {
                text = letter.toString()
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(if (on) Color.BLACK else Color.WHITE)
                setBackgroundColor(
                    if (on) settings.accentColor
                    else TileBackgrounds.chipIdleColor(context),
                )
                setPadding(dp(10), dp(4), dp(10), dp(4))
                contentDescription = "Jump to $letter"
                setOnClickListener {
                    val idx = LibraryBrowse.firstIndexForLetter(labels, letter)
                    val key = entries.getOrNull(idx)?.key ?: return@setOnClickListener
                    state.select(key, force = true)
                    Toast.makeText(activity, letter.toString(), Toast.LENGTH_SHORT).show()
                }
            }
            chips.add(letter to chip)
            row.addView(chip)
        }
        letterChipViews = chips
        return android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    // Browse chip row: All / Recent / Favorites + platform filters + search.
    private fun buildBrowseBar(context: Context, dp: (Int) -> Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        fun chip(
            label: String,
            selected: Boolean,
            onLongClick: (() -> Unit)? = null,
            onClick: () -> Unit,
        ): TextView =
            TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                setBackgroundColor(
                    if (selected) settings.accentColor
                    else TileBackgrounds.chipIdleColor(context))
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { onClick() }
                if (onLongClick != null) {
                    setOnLongClickListener {
                        onLongClick()
                        true
                    }
                }
            }
        val q = state.libraryBrowse
        fun setQuery(next: LibraryBrowse.BrowseQuery, force: Boolean = false) {
            state.setLibraryBrowse(next, force = force)
        }
        // All = full reset: clear platform/genre/search/collection AND jump the
        // carousel to the first unrestricted entry. Keeping the prior NDS
        // selection centered made "All" look like a no-op (same cards still
        // on screen even though the filter was cleared).
        row.addView(chip("All", q.mode == LibraryBrowse.Mode.ALL && q.platformId == null &&
            q.genre.isNullOrBlank() &&
            q.text.isBlank() && q.collectionName == null) {
            val live = app().settings
            val firstKey = library.curated(live).firstOrNull()?.packageName
                ?: HiddenRoms.listed(roms, settings.hiddenRomIds)
                    .firstOrNull()?.let { SlotKey.rom(it.id) }
            setQuery(LibraryBrowse.BrowseQuery(), force = true)
            if (firstKey != null) state.select(firstKey, force = true)
        })
        row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
        val chrome = settings.browseChrome
        fun addGap() {
            row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
        }
        fun setBrowse(next: LibraryBrowse.BrowseQuery) {
            val qNext = chrome.sanitize(next)
            setQuery(qNext)
            toastIfEmptyBrowse(qNext)
        }
        row.addView(
            chip(
                LibraryBrowse.labeledChip(
                    "Recent",
                    LibraryBrowse.recentCount(settings.lastLaunchedMs),
                ),
                q.mode == LibraryBrowse.Mode.RECENT,
            ) {
                setBrowse(q.copy(
                    mode = LibraryBrowse.Mode.RECENT,
                    platformId = null,
                    genre = null,
                    collectionName = null,
                ))
            },
        )
        val nowMs = System.currentTimeMillis()
        if (chrome.weekRail) {
            addGap()
            row.addView(
                chip(
                    LibraryBrowse.labeledChip(
                        "Week",
                        LibraryBrowse.playedInWindowCount(
                            settings.lastLaunchedMs, nowMs,
                            LibraryBrowse.WEEK_WINDOW_MS,
                        ),
                    ),
                    q.mode == LibraryBrowse.Mode.PLAYED_THIS_WEEK,
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK,
                        platformId = null,
                        genre = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.monthRail) {
            addGap()
            row.addView(
                chip(
                    LibraryBrowse.labeledChip(
                        "Month",
                        LibraryBrowse.playedInWindowCount(
                            settings.lastLaunchedMs, nowMs,
                            LibraryBrowse.MONTH_WINDOW_MS,
                        ),
                    ),
                    q.mode == LibraryBrowse.Mode.PLAYED_THIS_MONTH,
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.PLAYED_THIS_MONTH,
                        platformId = null,
                        genre = null,
                        collectionName = null,
                    ))
                },
            )
        }
        if (chrome.installedRail) {
            addGap()
            row.addView(chip("Installed", q.mode == LibraryBrowse.Mode.RECENTLY_INSTALLED) {
                setBrowse(q.copy(
                    mode = LibraryBrowse.Mode.RECENTLY_INSTALLED,
                    platformId = null,
                    genre = null,
                    collectionName = null,
                ))
            })
        }
        if (chrome.gamesRail) {
            addGap()
            row.addView(chip("Games", q.mode == LibraryBrowse.Mode.GAMES) {
                setBrowse(q.copy(
                    mode = LibraryBrowse.Mode.GAMES,
                    platformId = null,
                    genre = null,
                    collectionName = null,
                ))
            })
        }
        if (chrome.topRail) {
            addGap()
            row.addView(
                chip(
                    LibraryBrowse.labeledChip(
                        "Top",
                        LibraryBrowse.topPlayedCount(settings.playtimeMs),
                    ),
                    q.mode == LibraryBrowse.Mode.MOST_PLAYED,
                ) {
                    setBrowse(q.copy(
                        mode = LibraryBrowse.Mode.MOST_PLAYED,
                        platformId = null,
                        genre = null,
                        collectionName = null,
                    ))
                },
            )
        }
        addGap()
        row.addView(chip("Continue", false) {
            // Live settings: GameDeck holds a construction-time snapshot, so
            // lastLaunchedMs from `settings` can be empty after launches until
            // the next SETTINGS rebuild — Continue would silently toast-null.
            val live = app().settings
            val available = buildList {
                addAll(
                    HiddenRoms.listed(roms, live.hiddenRomIds)
                        .map { SlotKey.rom(it.id) },
                )
                addAll(library.curated(live).map { it.packageName })
                addAll(live.gridSlots.filterNotNull())
                addAll(live.dockSlots.filterNotNull())
                addAll(live.lastLaunchedMs.keys)
            }
            val cont = LibraryBrowse.continueKey(available, live.lastLaunchedMs)
            if (cont == null) {
                Toast.makeText(activity, "Nothing to continue", Toast.LENGTH_SHORT).show()
            } else {
                // RECENT rail puts cont at the front after rebuild. force on
                // both sides so re-tapping Continue still scrolls/rebinds when
                // the key is already selected or browse was already RECENT.
                setQuery(
                    LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.RECENT),
                    force = true,
                )
                state.select(cont, force = true)
                val label = continueLabel(cont, live)
                Toast.makeText(activity, "Continue: $label", Toast.LENGTH_SHORT).show()
            }
        })
        if (chrome.randomChip) {
            addGap()
            row.addView(chip("Random", false) {
                pickRandomEntry()
            })
        }
        addGap()
        row.addView(
            chip(
                LibraryBrowse.labeledChip("Fav", settings.favorites.size),
                q.mode == LibraryBrowse.Mode.FAVORITES,
            ) {
                setBrowse(q.copy(
                    mode = LibraryBrowse.Mode.FAVORITES,
                    platformId = null,
                    genre = null,
                    collectionName = null,
                ))
            },
        )
        if (chrome.alphaRail) {
            addGap()
            row.addView(chip("A–Z", q.mode == LibraryBrowse.Mode.ALPHA) {
                setBrowse(q.copy(
                    mode = LibraryBrowse.Mode.ALPHA,
                    platformId = null,
                    genre = null,
                    collectionName = null,
                ))
            })
        }
        if (chrome.unplayedRail) {
            addGap()
            row.addView(chip("New", q.mode == LibraryBrowse.Mode.UNPLAYED) {
                setBrowse(q.copy(
                    mode = LibraryBrowse.Mode.UNPLAYED,
                    platformId = null,
                    genre = null,
                    collectionName = null,
                ))
            })
        }
        if (chrome.collectionRails) {
            LibraryBrowse.presentCollectionRails(settings.collections).forEach { name ->
                if (name.equals("Favorites", ignoreCase = true)) return@forEach
                addGap()
                val selected = q.mode == LibraryBrowse.Mode.COLLECTION &&
                    q.collectionName == name
                val members = settings.collections[name]?.size ?: 0
                row.addView(
                    chip(
                        LibraryBrowse.labeledChip(name, members),
                        selected,
                        onLongClick = { showCollectionManageDialog(name) },
                    ) {
                        setBrowse(
                            LibraryBrowse.BrowseQuery(
                                mode = LibraryBrowse.Mode.COLLECTION,
                                collectionName = name,
                            ),
                        )
                    },
                )
            }
        }
        if (chrome.platformChips) {
            LibraryBrowse.presentPlatformCounts(roms, settings.hiddenRomIds).forEach { (pid, count) ->
                addGap()
                val short = Platforms.byId(pid)?.shortName ?: pid
                row.addView(chip(LibraryBrowse.labeledChip(short, count), q.platformId == pid) {
                    setBrowse(
                        q.copy(
                            mode = LibraryBrowse.Mode.ALL,
                            platformId = if (q.platformId == pid) null else pid,
                            collectionName = null,
                        ),
                    )
                })
            }
        }
        // Genre chips (opt-in): gamelist meta, ROM-only filter.
        if (chrome.genreChips) {
            LibraryBrowse.presentGenres(roms, settings.hiddenRomIds).forEach { genre ->
                addGap()
                val selected = q.genre?.equals(genre, ignoreCase = true) == true
                row.addView(chip(genre, selected) {
                    setBrowse(
                        q.copy(
                            mode = LibraryBrowse.Mode.ALL,
                            genre = if (selected) null else genre,
                            collectionName = null,
                        ),
                    )
                })
            }
        }
        addGap()
        row.addView(chip(if (q.text.isBlank()) "Search" else "\"${q.text}\"", q.text.isNotBlank()) {
            openSearchDialog()
        })
        addGap()
        row.addView(chip(
            if (state.multiSelectEnabled) "Select (${state.multiSelectKeys.size})" else "Select",
            state.multiSelectEnabled,
        ) {
            if (state.multiSelectEnabled) {
                showBulkActions()
            } else {
                state.setMultiSelectEnabled(true)
            }
        })
        // Horizontal scroll so many platforms don't crush the bar.
        return android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun showBulkActions() {
        val n = state.multiSelectKeys.size
        val romCount = state.multiSelectKeys.count {
            com.visorcraft.ghostgalleon.settings.SlotKey.isRom(it)
        }
        val activeCol = activeCollectionName()
        val labels = mutableListOf(
            "Favorite selected ($n)",
            "Pin selected to grid ($n)",
            "Pin selected to dock ($n)",
            "Add to collection…",
            "Hide selected ROMs ($romCount)",
        )
        if (activeCol != null) {
            labels.add("Remove from $activeCol ($n)")
        }
        labels.add("Clear selection")
        labels.add("Cancel select mode")
        android.app.AlertDialog.Builder(activity)
            .setTitle("Bulk actions")
            .setItems(labels.toTypedArray()) { _, which ->
                val label = labels[which]
                when {
                    label.startsWith("Favorite selected") -> {
                        val fav = com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkFavorite(
                            settings.favorites, state.multiSelectKeys, add = true)
                        app().updateSettings(settings.copy(favorites = fav))
                        state.clearMultiSelect()
                    }
                    label.startsWith("Pin selected to grid") -> {
                        val slots = com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkPinToGrid(
                            settings.gridSlots, state.multiSelectKeys)
                        app().updateSettings(settings.copy(gridSlots = slots))
                        state.clearMultiSelect()
                        Toast.makeText(activity, "Pinned to grid", Toast.LENGTH_SHORT).show()
                    }
                    label.startsWith("Pin selected to dock") -> {
                        val (dock, added) =
                            com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkPinToDock(
                                settings.dockSlots, state.multiSelectKeys)
                        app().updateSettings(settings.copy(dockSlots = dock))
                        state.clearMultiSelect()
                        Toast.makeText(
                            activity,
                            if (added > 0) "Pinned $added to dock" else "Dock full or already pinned",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    label.startsWith("Add to collection") ->
                        promptAddToCollection(state.multiSelectKeys.toList(), clearMulti = true)
                    label.startsWith("Hide selected") -> {
                        val (hidden, added) =
                            com.visorcraft.ghostgalleon.library.MultiSelectOps.bulkHideRoms(
                                settings.hiddenRomIds, state.multiSelectKeys,
                            )
                        if (added == 0) {
                            Toast.makeText(
                                activity,
                                "No ROMs in selection",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            app().updateSettings(settings.copy(hiddenRomIds = hidden))
                            state.clearMultiSelect()
                            Toast.makeText(
                                activity,
                                "Hidden $added ROM(s)",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    label.startsWith("Remove from") && activeCol != null -> {
                        removeFromCollection(
                            activeCol,
                            state.multiSelectKeys.toList(),
                            clearMulti = true,
                        )
                    }
                    label == "Clear selection" -> state.setMultiSelectKeys(emptySet())
                    label == "Cancel select mode" -> state.clearMultiSelect()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Long-press a collection rail chip: rename, delete, or open the rail.
     */
    private fun showCollectionManageDialog(name: String) {
        val count = settings.collections[name]?.size ?: 0
        val labels = arrayOf(
            "Open ($count)",
            "Rename…",
            "Delete collection",
            "Cancel",
        )
        android.app.AlertDialog.Builder(activity)
            .setTitle(name)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> state.setLibraryBrowse(
                        LibraryBrowse.BrowseQuery(
                            mode = LibraryBrowse.Mode.COLLECTION,
                            collectionName = name,
                        ),
                        force = true,
                    )
                    1 -> promptRenameCollection(name)
                    2 -> {
                        val next = CollectionsOps.deleteCollection(settings.collections, name)
                        app().updateSettings(settings.copy(collections = next))
                        // Leave collection filter if it pointed at the deleted rail.
                        if (state.libraryBrowse.collectionName == name) {
                            state.setLibraryBrowse(LibraryBrowse.BrowseQuery(), force = true)
                        }
                        Toast.makeText(activity, "Deleted $name", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun promptRenameCollection(from: String) {
        val input = android.widget.EditText(activity).apply {
            setText(from)
            setSelection(from.length)
            hint = "Name"
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle("Rename collection")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val to = input.text?.toString().orEmpty()
                val next = CollectionsOps.renameCollection(settings.collections, from, to)
                app().updateSettings(settings.copy(collections = next))
                val dest = to.trim()
                if (dest.isNotEmpty() &&
                    state.libraryBrowse.collectionName == from
                ) {
                    state.setLibraryBrowse(
                        LibraryBrowse.BrowseQuery(
                            mode = LibraryBrowse.Mode.COLLECTION,
                            collectionName = dest,
                        ),
                        force = true,
                    )
                }
                Toast.makeText(activity, "Renamed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Add [keys] (package or rom: ids) to an existing or new collection.
     * When [clearMulti] is true, exits multi-select after a successful add.
     */
    private fun promptAddToCollection(keys: List<String>, clearMulti: Boolean = false) {
        if (keys.isEmpty()) {
            Toast.makeText(activity, "Nothing selected", Toast.LENGTH_SHORT).show()
            return
        }
        val names = LibraryBrowse.presentCollectionRails(settings.collections).toMutableList()
        names.add(0, "+ New collection")
        android.app.AlertDialog.Builder(activity)
            .setTitle("Add to collection")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    val input = android.widget.EditText(activity).apply { hint = "Name" }
                    android.app.AlertDialog.Builder(activity)
                        .setTitle("New collection")
                        .setView(input)
                        .setPositiveButton("Create") { _, _ ->
                            val name = input.text?.toString().orEmpty()
                            var cols = CollectionsOps.createCollection(settings.collections, name)
                            cols = CollectionsOps.bulkAddToCollection(cols, name, keys)
                            app().updateSettings(settings.copy(collections = cols))
                            if (clearMulti) state.clearMultiSelect()
                            Toast.makeText(activity, "Added to $name", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    val name = names[which]
                    val cols = CollectionsOps.bulkAddToCollection(
                        settings.collections, name, keys)
                    app().updateSettings(settings.copy(collections = cols))
                    if (clearMulti) state.clearMultiSelect()
                    Toast.makeText(activity, "Added to $name", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    /** Human label for a continue/slot key (ROM name or app label). */
    private fun continueLabel(key: String, live: Settings): String {
        SlotKey.romId(key)?.let { id ->
            roms.firstOrNull { it.id == id }?.name?.let { return it }
        }
        library.curated(live).firstOrNull { it.packageName == key }?.label?.let { return it }
        return key.substringAfterLast(':').ifBlank { key }
    }

    /**
     * Select a random visible library item. Switches browse mode to ALL so
     * the pick lands in the carousel and A/CONFIRM launches the same key
     * (not a stale filter list).
     */
    private fun pickRandomEntry() {
        val live = app().settings
        val pool = buildList {
            addAll(library.curated(live).map { it.packageName })
            addAll(
                HiddenRoms.listed(roms, live.hiddenRomIds)
                    .map { SlotKey.rom(it.id) },
            )
        }
        val key = LibraryBrowse.pickRandom(pool) { size ->
            java.util.concurrent.ThreadLocalRandom.current().nextInt(size)
        }
        if (key == null) {
            Toast.makeText(activity, "Library empty", Toast.LENGTH_SHORT).show()
            return
        }
        // Full library view so the selection is present in entries after rebuild.
        state.setLibraryBrowse(LibraryBrowse.BrowseQuery(), force = true)
        state.select(key, force = true)
        Toast.makeText(activity, "Random pick", Toast.LENGTH_SHORT).show()
    }

    private fun openSearchDialog() {
        val input = android.widget.EditText(activity).apply {
            setText(state.libraryBrowse.text)
            hint = "Name, genre, developer, year…"
            setSingleLine()
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle("Search library")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val text = input.text?.toString().orEmpty()
                val next = state.libraryBrowse.copy(text = text)
                state.setLibraryBrowse(next)
                val n = estimateCarouselSize(next)
                Toast.makeText(
                    activity,
                    BrowseFeedback.searchApplied(n, text),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNeutralButton("Clear") { _, _ ->
                state.setLibraryBrowse(state.libraryBrowse.copy(text = ""))
                Toast.makeText(
                    activity,
                    BrowseFeedback.searchApplied(0, ""),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Toast when a rail/filter would show zero cards. */
    private fun toastIfEmptyBrowse(q: LibraryBrowse.BrowseQuery) {
        val hint = BrowseFeedback.emptyHint(q) ?: return
        if (estimateCarouselSize(q) == 0) {
            Toast.makeText(activity, hint, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Lightweight size estimate for feedback toasts (apps + ROMs, same gates
     * as the carousel for search/platform/genre).
     */
    private fun estimateCarouselSize(q: LibraryBrowse.BrowseQuery): Int {
        val live = app().settings
        val now = System.currentTimeMillis()
        val browsed = LibraryBrowse.browseRoms(
            roms, q,
            lastLaunchedMs = live.lastLaunchedMs,
            favorites = live.favorites,
            collections = live.collections,
            playtimeMs = live.playtimeMs,
            hiddenRomIds = live.hiddenRomIds,
            nowMs = now,
        )
        val appsOk = q.platformId == null && q.genre.isNullOrBlank()
        if (q.mode == LibraryBrowse.Mode.RECENTLY_INSTALLED) {
            val apps = library.visible(live)
            if (q.text.isBlank()) return apps.size
            val needle = q.text.trim()
            return apps.count {
                it.label.contains(needle, ignoreCase = true) ||
                    it.packageName.contains(needle, ignoreCase = true)
            }
        }
        if (q.mode == LibraryBrowse.Mode.COLLECTION) {
            val name = q.collectionName.orEmpty()
            val keys = live.collections[name].orEmpty()
            if (keys.isEmpty()) return 0
            // Member keys that still resolve (apps or visible ROMs).
            val hidden = live.hiddenRomIds
            val byRom = roms.associateBy { it.id }
            val curated = library.curated(live).map { it.packageName }.toSet()
            return keys.count { k ->
                SlotKey.romId(k)?.let { id -> id !in hidden && id in byRom } == true ||
                    (!SlotKey.isRom(k) && k in curated)
            }
        }
        var n = browsed.size
        if (!appsOk) return n
        when (q.mode) {
            LibraryBrowse.Mode.ALL,
            LibraryBrowse.Mode.GAMES,
            LibraryBrowse.Mode.ALPHA,
            -> {
                val apps = when (q.mode) {
                    LibraryBrowse.Mode.GAMES ->
                        LibraryBrowse.filterGameApps(library.curated(live)) { it.isGame }
                    LibraryBrowse.Mode.UNPLAYED -> emptyList()
                    else -> library.curated(live)
                }
                n += if (q.text.isBlank()) {
                    apps.size
                } else {
                    val needle = q.text.trim()
                    apps.count {
                        it.label.contains(needle, ignoreCase = true) ||
                            it.packageName.contains(needle, ignoreCase = true)
                    }
                }
            }
            LibraryBrowse.Mode.RECENT,
            LibraryBrowse.Mode.PLAYED_THIS_WEEK,
            LibraryBrowse.Mode.PLAYED_THIS_MONTH,
            LibraryBrowse.Mode.MOST_PLAYED,
            LibraryBrowse.Mode.FAVORITES,
            -> {
                // Apps interleave; count positive last-launch / playtime / fav keys.
                val byPkg = library.curated(live).associateBy { it.packageName }
                n += when (q.mode) {
                    LibraryBrowse.Mode.FAVORITES ->
                        live.favorites.count { !SlotKey.isRom(it) && it in byPkg }
                    LibraryBrowse.Mode.MOST_PLAYED ->
                        live.playtimeMs.count { (k, v) ->
                            v > 0L && !SlotKey.isRom(k) && k in byPkg
                        }
                    LibraryBrowse.Mode.PLAYED_THIS_WEEK ->
                        LibraryBrowse.filterPlayedInWindow(
                            live.lastLaunchedMs.keys.filter { !SlotKey.isRom(it) && it in byPkg },
                            live.lastLaunchedMs,
                            nowMs = now,
                            windowMs = LibraryBrowse.WEEK_WINDOW_MS,
                        ).size
                    LibraryBrowse.Mode.PLAYED_THIS_MONTH ->
                        LibraryBrowse.filterPlayedInWindow(
                            live.lastLaunchedMs.keys.filter { !SlotKey.isRom(it) && it in byPkg },
                            live.lastLaunchedMs,
                            nowMs = now,
                            windowMs = LibraryBrowse.MONTH_WINDOW_MS,
                        ).size
                    else -> // RECENT
                        live.lastLaunchedMs.keys.count { !SlotKey.isRom(it) && it in byPkg }
                }
            }
            else -> {}
        }
        return n
    }

    // Apps launch through their package intent, ROMs through the platform
    // template; both open on the non-interactive display.
    private fun launch(entry: CarouselEntry, playerId: String? = null) {
        launchSlotKey(activity, state, roms, entry.key, playerId = playerId)
    }

    private fun app(): GhostGalleonApp = activity.application as GhostGalleonApp

    private fun toggleFavorite(key: String) {
        val next = CollectionsOps.toggleFavorite(settings.favorites, key)
        val cols = if (key in next) {
            CollectionsOps.addToCollection(settings.collections, "Favorites", key)
        } else {
            CollectionsOps.removeFromCollection(settings.collections, "Favorites", key)
        }
        app().updateSettings(settings.copy(favorites = next, collections = cols))
        Toast.makeText(
            activity,
            if (key in next) "Added to favorites" else "Removed from favorites",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun openWithMenu(entry: CarouselEntry) {
        val rom = entry.rom ?: return
        val platform = Platforms.byId(rom.platformId) ?: return
        val pm = activity.packageManager
        val installed = PlayerResolver.installedPlayers(platform) { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (installed.isEmpty()) {
            Toast.makeText(activity, "No players installed", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = installed.map { it.displayName }.toTypedArray()
        android.app.AlertDialog.Builder(activity)
            .setTitle("Open with")
            .setItems(labels) { _, which ->
                val player = installed[which]
                // Persist as platform default when chosen.
                app().updateSettings(
                    settings.copy(
                        defaultPlayers = settings.defaultPlayers +
                            (rom.platformId to player.id),
                    ),
                    notify = false,
                )
                launch(entry, playerId = player.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Per-ROM preferred player profile (settings.romProfiles). */
    private fun showPlayerProfileMenu(rom: RomEntry) {
        val platform = Platforms.byId(rom.platformId) ?: return
        val players = platform.players
        if (players.isEmpty()) {
            Toast.makeText(activity, "No players for platform", Toast.LENGTH_SHORT).show()
            return
        }
        val current = settings.romProfiles[rom.id]
        val labels = players.map { p ->
            val mark = if (p.id == current) " ✓" else ""
            p.displayName + mark
        } + listOf(
            if (current == null) "Platform default ✓" else "Platform default",
        )
        android.app.AlertDialog.Builder(activity)
            .setTitle("Player profile")
            .setItems(labels.toTypedArray()) { _, which ->
                val nextProfiles = if (which >= players.size) {
                    RomProfiles.clearProfile(settings.romProfiles, rom.id)
                } else {
                    RomProfiles.setProfile(settings.romProfiles, rom.id, players[which].id)
                }
                app().updateSettings(settings.copy(romProfiles = nextProfiles))
                Toast.makeText(activity, "Player saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setArtOverride(rom: RomEntry) {
        val host = activity as? com.visorcraft.ghostgalleon.ui.BaseDeckActivity
        if (host == null) {
            Toast.makeText(activity, "Cannot open image picker", Toast.LENGTH_SHORT).show()
            return
        }
        host.requestCustomIcon { uri ->
            val next = settings.artOverrides + (rom.id to uri.toString())
            // Drop mem+disk for this rom so override is re-decoded (source
            // stamps also reject mismatched cache on the next load).
            app().artCache.invalidate(rom.id)
            app().updateSettings(settings.copy(artOverrides = next))
            Toast.makeText(activity, "Artwork set", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToGrid(key: String) {
        val filled = CollectionsOps.bulkFillSlots(settings.gridSlots, listOf(key))
        app().updateSettings(settings.copy(gridSlots = filled))
        Toast.makeText(activity, "Added to grid", Toast.LENGTH_SHORT).show()
    }

    private fun pinToDock(key: String) {
        val live = app().settings
        val result = DockSlots.pinKey(live.dockSlots, key)
        when (result.status) {
            DockSlots.PinStatus.ALREADY ->
                Toast.makeText(activity, "Already in dock", Toast.LENGTH_SHORT).show()
            DockSlots.PinStatus.FULL ->
                Toast.makeText(activity, "Dock is full", Toast.LENGTH_SHORT).show()
            DockSlots.PinStatus.PINNED ->
                updateDockSlots(result.slots, "Pinned to dock")
        }
    }

    private fun unpinFromDock(key: String) {
        val live = app().settings
        if (!DockSlots.containsKey(live.dockSlots, key)) {
            Toast.makeText(activity, "Not in dock", Toast.LENGTH_SHORT).show()
            return
        }
        updateDockSlots(DockSlots.unpinKey(live.dockSlots, key), "Unpinned from dock")
    }

    private fun hideRom(rom: RomEntry) {
        val next = HiddenRoms.hide(settings.hiddenRomIds, rom.id)
        app().updateSettings(settings.copy(hiddenRomIds = next))
        Toast.makeText(activity, "Hidden: ${rom.name}", Toast.LENGTH_SHORT).show()
    }

    /** Named collection rail under browse, or Favorites when Fav is selected. */
    private fun activeCollectionName(): String? =
        CollectionsOps.activeCollectionName(
            state.libraryBrowse.mode.name,
            state.libraryBrowse.collectionName,
        )

    /**
     * Drop [keys] from [name]. Favorites also clears the favorites set.
     * Leaves the collection filter when the rail is deleted (emptied).
     */
    private fun removeFromCollection(
        name: String,
        keys: List<String>,
        clearMulti: Boolean = false,
    ) {
        if (keys.isEmpty()) {
            Toast.makeText(activity, "Nothing selected", Toast.LENGTH_SHORT).show()
            return
        }
        val n = name.trim()
        val cols = CollectionsOps.bulkRemoveFromCollection(settings.collections, n, keys)
        val favs = if (n.equals("Favorites", ignoreCase = true)) {
            CollectionsOps.bulkRemoveFavorites(settings.favorites, keys)
        } else {
            settings.favorites
        }
        app().updateSettings(settings.copy(favorites = favs, collections = cols))
        if (clearMulti) state.clearMultiSelect()
        // Emptied user collection is dropped by bulkRemove — leave the filter.
        val stillThere = n.equals("Favorites", ignoreCase = true) || n in cols
        if (!stillThere &&
            state.libraryBrowse.mode == LibraryBrowse.Mode.COLLECTION &&
            state.libraryBrowse.collectionName == n
        ) {
            state.setLibraryBrowse(LibraryBrowse.BrowseQuery(), force = true)
        }
        val label = if (keys.size == 1) "Removed from $n" else "Removed ${keys.size} from $n"
        Toast.makeText(activity, label, Toast.LENGTH_SHORT).show()
    }

    /** Named COLLECTION rail only (ordered list); not the unordered Fav set. */
    private fun reorderCollectionName(): String? {
        val q = state.libraryBrowse
        return if (CollectionsOps.canReorderCollection(q.mode.name, q.collectionName)) {
            q.collectionName!!.trim()
        } else {
            null
        }
    }

    private fun reorderInCollection(name: String, key: String, choice: SlotMenu.Choice) {
        val next = when (choice) {
            SlotMenu.Choice.MOVE_TO_TOP ->
                CollectionsOps.moveMemberToEdge(settings.collections, name, key, toFront = true)
            SlotMenu.Choice.MOVE_TO_END ->
                CollectionsOps.moveMemberToEdge(settings.collections, name, key, toFront = false)
            SlotMenu.Choice.MOVE_UP ->
                CollectionsOps.moveMemberBy(settings.collections, name, key, delta = -1)
            SlotMenu.Choice.MOVE_DOWN ->
                CollectionsOps.moveMemberBy(settings.collections, name, key, delta = 1)
            else -> settings.collections
        }
        app().updateSettings(settings.copy(collections = next))
        val toast = when (choice) {
            SlotMenu.Choice.MOVE_TO_TOP -> "Moved to top"
            SlotMenu.Choice.MOVE_TO_END -> "Moved to end"
            SlotMenu.Choice.MOVE_UP -> "Moved up"
            SlotMenu.Choice.MOVE_DOWN -> "Moved down"
            else -> "Reordered"
        }
        Toast.makeText(activity, toast, Toast.LENGTH_SHORT).show()
    }

    private fun showDetails(entry: CarouselEntry) {
        val key = entry.key
        val rom = entry.rom
        val body = GameDetails.body(
            GameDetails.Input(
                title = entry.label,
                key = key,
                kind = if (rom != null) "ROM" else "App",
                platformId = rom?.platformId,
                genre = rom?.genre,
                developer = rom?.developer,
                year = rom?.year,
                rating = rom?.rating,
                lastLaunchedMs = settings.lastLaunchedMs[key],
                playtimeMs = settings.playtimeMs[key] ?: 0L,
                favorite = key in settings.favorites,
                collections = GameDetails.collectionsContaining(settings.collections, key),
                nowMs = System.currentTimeMillis(),
            ),
        )
        val builder = android.app.AlertDialog.Builder(activity)
            .setTitle("Details")
            .setMessage(body)
            .setPositiveButton("OK", null)
        // Apps: optional jump to system package details.
        if (rom == null && !SlotKey.isRom(key)) {
            builder.setNeutralButton("App info") { _, _ -> openAppInfo(key) }
        }
        builder.show()
    }

    private fun openAppInfo(packageName: String) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        ).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure {
                Toast.makeText(activity, "Cannot open app info", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openEntryMenu(entry: CarouselEntry) {
        val key = entry.key
        val fav = key in settings.favorites
        val activeCol = activeCollectionName()
        val reorderCol = reorderCollectionName()
        val isApp = entry.rom == null && !SlotKey.isRom(key)
        val choices = buildList {
            add(SlotMenu.Choice.DETAILS)
            if (DockSlots.containsKey(settings.dockSlots, key)) {
                add(SlotMenu.Choice.UNPIN_FROM_DOCK)
            } else {
                add(SlotMenu.Choice.PIN_TO_DOCK)
            }
            add(if (fav) SlotMenu.Choice.UNFAVORITE else SlotMenu.Choice.FAVORITE)
            add(SlotMenu.Choice.ADD_TO_COLLECTION)
            if (activeCol != null) {
                add(SlotMenu.Choice.REMOVE_FROM_COLLECTION)
            }
            if (reorderCol != null) {
                add(SlotMenu.Choice.MOVE_TO_TOP)
                add(SlotMenu.Choice.MOVE_UP)
                add(SlotMenu.Choice.MOVE_DOWN)
                add(SlotMenu.Choice.MOVE_TO_END)
            }
            if (isApp) {
                add(SlotMenu.Choice.APP_INFO)
            }
            if (entry.rom != null) {
                add(SlotMenu.Choice.OPEN_WITH)
                add(SlotMenu.Choice.PLAYER)
                add(SlotMenu.Choice.SET_ART)
                add(SlotMenu.Choice.ADD_TO_GRID)
                add(SlotMenu.Choice.HIDE)
            }
            add(SlotMenu.Choice.CANCEL)
        }
        val menu = SlotMenu(activity, settings.accentColor, choices) { choice ->
            closeSlotMenu()
            when (choice) {
                SlotMenu.Choice.DETAILS -> showDetails(entry)
                SlotMenu.Choice.PIN_TO_DOCK -> pinToDock(key)
                SlotMenu.Choice.UNPIN_FROM_DOCK -> unpinFromDock(key)
                SlotMenu.Choice.APP_INFO -> openAppInfo(key)
                SlotMenu.Choice.FAVORITE, SlotMenu.Choice.UNFAVORITE -> toggleFavorite(key)
                SlotMenu.Choice.ADD_TO_COLLECTION -> promptAddToCollection(listOf(key))
                SlotMenu.Choice.REMOVE_FROM_COLLECTION ->
                    activeCol?.let { removeFromCollection(it, listOf(key)) }
                SlotMenu.Choice.MOVE_TO_TOP,
                SlotMenu.Choice.MOVE_UP,
                SlotMenu.Choice.MOVE_DOWN,
                SlotMenu.Choice.MOVE_TO_END,
                -> reorderCol?.let { reorderInCollection(it, key, choice) }
                SlotMenu.Choice.OPEN_WITH -> openWithMenu(entry)
                SlotMenu.Choice.PLAYER -> entry.rom?.let { showPlayerProfileMenu(it) }
                SlotMenu.Choice.SET_ART -> entry.rom?.let { setArtOverride(it) }
                SlotMenu.Choice.ADD_TO_GRID -> addToGrid(key)
                SlotMenu.Choice.HIDE -> entry.rom?.let { hideRom(it) }
                else -> {}
            }
        }
        slotMenu = menu
        rootView?.addView(menu.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // --- Dock interactions (same behavior as the grid deck's dock) ---

    private fun updateDockSlots(slots: List<String?>, toast: String? = null) {
        val app = activity.application as GhostGalleonApp
        app.updateSettings(app.settings.copy(dockSlots = slots))
        toast?.let { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
    }

    private fun onDockTap(index: Int) {
        val bar = dockBar ?: return
        when {
            dockMoveIndex != null -> dropDockMove(tapSlot = index)
            bar.isBlank(index) -> openDockPicker(index)
            else -> bar.keyAt(index)?.let { launchSlotKey(activity, state, roms, it) }
        }
    }

    private fun onDockLongPress(index: Int) {
        if (dockMoveIndex == null && dockBar?.isBlank(index) == false) {
            openDockSlotMenu(index)
        }
    }

    private fun openDockSlotMenu(index: Int) {
        state.focusDock(index)
        val choices = listOf(
            SlotMenu.Choice.MOVE, SlotMenu.Choice.REMOVE, SlotMenu.Choice.CANCEL)
        val menu = SlotMenu(activity, settings.accentColor, choices) { choice ->
            closeSlotMenu()
            when (choice) {
                SlotMenu.Choice.MOVE -> startDockMove(index)
                SlotMenu.Choice.REMOVE -> {
                    val next = DockSlots.remove(settings.dockSlots, index)
                    updateDockSlots(next, "Removed from dock")
                    // Removal compacts and can shrink the visible row;
                    // keep the dock focus on a rendered slot.
                    state.dockSlot?.let { focused ->
                        val last = DockSlots.visibleCount(next) - 1
                        if (focused > last) state.focusDock(last)
                    }
                }
                else -> {}
            }
        }
        slotMenu = menu
        rootView?.addView(menu.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun closeSlotMenu() {
        slotMenu?.let { rootView?.removeView(it.view) }
        slotMenu = null
    }

    private fun startDockMove(index: Int) {
        dockMoveIndex = index
        dockMoveWorking = settings.dockSlots.toMutableList()
        // The dock slots STAY visible during a dock move (they are the
        // swap targets); the hint moves to the hint bar.
        hintView?.text = HintBar.MOVE_TEXT
        state.focusDock(index)
        dockBar?.updateFocus(index, moving = index)
    }

    private fun handleDockMoveAction(action: Action, from: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT -> {
                val to = dockNav.move(from, action)
                if (to != from) {
                    val working = dockMoveWorking ?: return true
                    val tmp = working[from]
                    working[from] = working[to]
                    working[to] = tmp
                    dockMoveIndex = to
                    dockBar?.rebind()
                    state.focusDock(to)
                    dockBar?.updateFocus(to, moving = to)
                }
            }
            Action.CONFIRM -> dropDockMove()
            Action.BACK -> cancelDockMove()
            else -> {}
        }
        return true // dock move mode swallows every action
    }

    private fun dropDockMove(tapSlot: Int? = null) {
        val from = dockMoveIndex ?: return
        val working = dockMoveWorking ?: return
        var finalSlot = from
        if (tapSlot != null && tapSlot in working.indices && tapSlot != from) {
            val tmp = working[from]
            working[from] = working[tapSlot]
            working[tapSlot] = tmp
            finalSlot = tapSlot
        }
        val slots = working.toList()
        dockMoveIndex = null
        dockMoveWorking = null
        hintView?.text = HintBar.textFor(state.dockSlot != null)
        // Compact first: the working copy may park the tile on the visible
        // "+" placeholder, which is not a real storage slot — focus the
        // tile's post-compact position.
        val compacted = DockSlots.compact(slots)
        val droppedKey = slots.getOrNull(finalSlot)
        updateDockSlots(compacted)
        state.focusDock(
            if (droppedKey != null) compacted.indexOf(droppedKey) else finalSlot)
    }

    private fun cancelDockMove() {
        dockMoveIndex = null
        dockMoveWorking = null
        hintView?.text = HintBar.textFor(state.dockSlot != null)
        // Slots still show the discarded working copy: repopulate.
        dockBar?.rebind()
        dockBar?.updateFocus(state.dockSlot)
    }

    // Dock-focused input: LEFT/RIGHT walk the slots, UP/BACK return to the
    // carousel (the carousel selection was left untouched, so re-selecting
    // its key just clears the dock focus), A launches or opens the picker.
    private fun handleDockAction(action: Action, dockIndex: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT ->
                state.focusDock(dockNav.move(dockIndex, action))
            Action.NAV_UP, Action.BACK ->
                // Re-selecting the carousel key just clears the dock focus
                // (select notifies when dockSlot was set, even for an
                // unchanged key); the fallback covers an empty carousel.
                state.select(entries.getOrNull(selectedIndex())?.key ?: state.selectedKey)
            Action.CONFIRM -> {
                val bar = dockBar
                if (bar == null || bar.isBlank(dockIndex)) {
                    openDockPicker(dockIndex)
                } else {
                    bar.keyAt(dockIndex)?.let { launchSlotKey(activity, state, roms, it) }
                }
            }
            else -> {}
        }
        return true // dock focus swallows every action
    }

    private fun openDockPicker(slot: Int) {
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            title = "Add to dock",
            onPick = { key ->
                closePicker()
                val app = activity.application as GhostGalleonApp
                updateDockSlots(DockSlots.fill(app.settings.dockSlots, slot, key))
            },
            onHide = { packageName ->
                closePicker()
                hideApp(packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        rootView?.addView(appPicker.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // Hiding removes the app from the picker/all-apps lists only; dock and
    // grid slots that already hold it keep the tile (still launchable).
    private fun hideApp(packageName: String) {
        val app = activity.application as GhostGalleonApp
        app.updateSettings(app.settings.copy(
            hiddenPackages = app.settings.hiddenPackages + packageName))
        Toast.makeText(activity, "App hidden", Toast.LENGTH_SHORT).show()
    }

    private fun closePicker() {
        picker?.let {
            rootView?.removeView(it.view)
            rootView?.let { root ->
                activity.getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(root.windowToken, 0)
            }
        }
        picker = null
    }

    private inner class CardAdapter(
        private val context: Context,
        private val cardSize: Int,
        private val cardSpacing: Int,
        private val cellPadding: Int,
    ) : RecyclerView.Adapter<CardAdapter.CardHolder>() {

        inner class CardHolder(val root: LinearLayout) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
            // Transparent full-height slot so the card surface wraps the
            // icon+label and stays vertically centered in the carousel.
            val slot = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
            }
            slot.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                marginStart = cardSpacing / 2
                marginEnd = cardSpacing / 2
            }
            return CardHolder(slot)
        }

        override fun getItemCount() = entries.size

        override fun onBindViewHolder(holder: CardHolder, position: Int) {
            val entry = entries[position]
            holder.root.removeAllViews()
            // While the dock holds focus the carousel shows NO ring — the
            // focused dock slot carries it instead.
            val focused = entry.key == state.selectedKey && state.dockSlot == null
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
                background = if (focused) {
                    TileBackgrounds.selected(context, settings.accentColor)
                } else {
                    TileBackgrounds.card(context)
                }
                if (focused) {
                    scaleX = 1.1f
                    scaleY = 1.1f
                }
            }
            // ROM cards show cached artwork over the platform placeholder
            // (async fill, no decode on the UI thread); without art the
            // placeholder shows through — a cheap draw even deep in the ROM
            // section.
            val art: View = entry.rom?.let {
                ArtTile.view(
                    context,
                    (activity.application as GhostGalleonApp).artCache,
                    it,
                    targetPx = cardSize,
                    artOverrides = settings.artOverrides,
                )
            } ?: ImageView(context).apply {
                CustomIcon.bind(
                    this, iconLoader,
                    (activity.application as GhostGalleonApp).artCache,
                    settings, entry.appPackage!!, cardSize)
            }
            card.addView(art, LinearLayout.LayoutParams(cardSize, cardSize))
            card.addView(TextView(context).apply {
                text = entry.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            // Playtime / last-played + Fav/Dock status (no extra chrome chips).
            val meta = SessionMath.cardMetaLine(
                settings.lastLaunchedMs[entry.key],
                settings.playtimeMs[entry.key] ?: 0L,
                System.currentTimeMillis(),
                favorite = entry.key in settings.favorites,
                inDock = DockSlots.containsKey(settings.dockSlots, entry.key),
            )
            card.addView(TextView(context).apply {
                text = meta
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(0x99FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            holder.root.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            // Multi-select: tap toggles membership. Otherwise tap-to-focus /
            // second tap launches. Long-press opens the entry menu (or
            // enters multi-select with this key).
            holder.root.setOnClickListener {
                if (state.multiSelectEnabled) {
                    state.toggleMultiSelectKey(entry.key)
                    return@setOnClickListener
                }
                if (state.selectedKey == entry.key && state.dockSlot == null) {
                    launch(entry)
                } else {
                    state.select(entry.key)
                }
            }
            holder.root.setOnLongClickListener {
                if (state.multiSelectEnabled) {
                    state.toggleMultiSelectKey(entry.key)
                } else {
                    state.select(entry.key)
                    openEntryMenu(entry)
                }
                true
            }
            // Selection ring for multi-select.
            if (state.multiSelectEnabled && entry.key in state.multiSelectKeys) {
                card.alpha = 1f
                card.foreground = android.graphics.drawable.ColorDrawable(0x4400AAFF)
            }
        }
    }
}
