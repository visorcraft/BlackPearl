package com.visorcraft.blackpearl.ui.deck

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
import com.visorcraft.blackpearl.BlackPearlApp
import com.visorcraft.blackpearl.art.ArtTile
import com.visorcraft.blackpearl.library.AppLibrary
import com.visorcraft.blackpearl.library.CollectionsOps
import com.visorcraft.blackpearl.library.LibraryBrowse
import com.visorcraft.blackpearl.rom.Platforms
import com.visorcraft.blackpearl.rom.PlatformTile
import com.visorcraft.blackpearl.rom.PlayerResolver
import com.visorcraft.blackpearl.rom.RomEntry
import com.visorcraft.blackpearl.rom.RomLauncher
import com.visorcraft.blackpearl.settings.Action
import com.visorcraft.blackpearl.settings.DockSlots
import com.visorcraft.blackpearl.settings.GridSlots
import com.visorcraft.blackpearl.settings.Settings
import com.visorcraft.blackpearl.settings.SlotKey
import com.visorcraft.blackpearl.state.DeckState

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

    // Curated apps (when browse mode is ALL and no platform filter/search),
    // then browsed ROMs via LibraryBrowse (platform / search / recent / fav).
    // Recent also interleaves recently launched apps (package keys).
    private val entries: List<CarouselEntry> by lazy {
        val q = state.libraryBrowse
        val browsed = LibraryBrowse.browseRoms(
            roms, q,
            lastLaunchedMs = settings.lastLaunchedMs,
            favorites = settings.favorites,
        ).map {
            CarouselEntry(SlotKey.rom(it.id), it.name, null, it)
        }
        when {
            q.mode == LibraryBrowse.Mode.RECENT &&
                q.platformId == null && q.text.isBlank() -> {
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
            q.mode == LibraryBrowse.Mode.FAVORITES &&
                q.platformId == null -> {
                val favApps = library.curated(settings)
                    .filter { it.packageName in settings.favorites }
                    .map {
                        CarouselEntry(it.packageName, it.label, it.packageName, null)
                    }
                favApps + browsed
            }
            q.mode == LibraryBrowse.Mode.ALL &&
                q.platformId == null &&
                q.text.isBlank() -> {
                library.curated(settings).map {
                    CarouselEntry(it.packageName, it.label, it.packageName, null)
                } + browsed
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
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = false
            clipToPadding = false
        }
        rootView = root
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        content.addView(
            buildBrowseBar(context, ::dp),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
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

    override fun handleAction(action: Action): Boolean {
        slotMenu?.let { return it.handleAction(action) }
        picker?.let { return it.handleAction(action) }
        dockMoveIndex?.let { return handleDockMoveAction(action, it) }
        state.dockSlot?.let { return handleDockAction(action, it) }
        return when (action) {
            Action.CONFIRM -> {
                entries.getOrNull(selectedIndex())?.let { launch(it) }
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

    // Browse chip row: All / Recent / Favorites + platform filters + search.
    private fun buildBrowseBar(context: Context, dp: (Int) -> Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                setBackgroundColor(
                    if (selected) settings.accentColor else 0xFF2A2A32.toInt())
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { onClick() }
            }
        val q = state.libraryBrowse
        fun setQuery(next: LibraryBrowse.BrowseQuery) {
            state.setLibraryBrowse(next)
        }
        row.addView(chip("All", q.mode == LibraryBrowse.Mode.ALL && q.platformId == null) {
            setQuery(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.ALL))
        })
        row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
        row.addView(chip("Recent", q.mode == LibraryBrowse.Mode.RECENT) {
            setQuery(q.copy(mode = LibraryBrowse.Mode.RECENT, platformId = null))
        })
        row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
        row.addView(chip("Fav", q.mode == LibraryBrowse.Mode.FAVORITES) {
            setQuery(q.copy(mode = LibraryBrowse.Mode.FAVORITES, platformId = null))
        })
        LibraryBrowse.presentPlatforms(roms).forEach { pid ->
            row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
            val label = Platforms.byId(pid)?.shortName ?: pid
            row.addView(chip(label, q.platformId == pid) {
                setQuery(
                    q.copy(
                        mode = LibraryBrowse.Mode.ALL,
                        platformId = if (q.platformId == pid) null else pid,
                    ),
                )
            })
        }
        row.addView(View(context), LinearLayout.LayoutParams(dp(6), 1))
        row.addView(chip(if (q.text.isBlank()) "Search" else "\"${q.text}\"", q.text.isNotBlank()) {
            openSearchDialog()
        })
        // Horizontal scroll so many platforms don't crush the bar.
        return android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun openSearchDialog() {
        val input = android.widget.EditText(activity).apply {
            setText(state.libraryBrowse.text)
            hint = "Search ROMs"
            setSingleLine()
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle("Search library")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                state.setLibraryBrowse(
                    state.libraryBrowse.copy(text = input.text?.toString().orEmpty()),
                )
            }
            .setNeutralButton("Clear") { _, _ ->
                state.setLibraryBrowse(state.libraryBrowse.copy(text = ""))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Apps launch through their package intent, ROMs through the platform
    // template; both open on the non-interactive display.
    private fun launch(entry: CarouselEntry, playerId: String? = null) {
        launchSlotKey(activity, state, roms, entry.key, playerId = playerId)
    }

    private fun app(): BlackPearlApp = activity.application as BlackPearlApp

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

    private fun setArtOverride(rom: RomEntry) {
        val host = activity as? com.visorcraft.blackpearl.ui.BaseDeckActivity
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

    private fun openEntryMenu(entry: CarouselEntry) {
        val key = entry.key
        val fav = key in settings.favorites
        val choices = buildList {
            add(if (fav) SlotMenu.Choice.UNFAVORITE else SlotMenu.Choice.FAVORITE)
            if (entry.rom != null) {
                add(SlotMenu.Choice.OPEN_WITH)
                add(SlotMenu.Choice.SET_ART)
                add(SlotMenu.Choice.ADD_TO_GRID)
            }
            add(SlotMenu.Choice.CANCEL)
        }
        val menu = SlotMenu(activity, settings.accentColor, choices) { choice ->
            closeSlotMenu()
            when (choice) {
                SlotMenu.Choice.FAVORITE, SlotMenu.Choice.UNFAVORITE -> toggleFavorite(key)
                SlotMenu.Choice.OPEN_WITH -> openWithMenu(entry)
                SlotMenu.Choice.SET_ART -> entry.rom?.let { setArtOverride(it) }
                SlotMenu.Choice.ADD_TO_GRID -> addToGrid(key)
                else -> {}
            }
        }
        slotMenu = menu
        rootView?.addView(menu.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // --- Dock interactions (same behavior as the grid deck's dock) ---

    private fun updateDockSlots(slots: List<String?>, toast: String? = null) {
        val app = activity.application as BlackPearlApp
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
                val app = activity.application as BlackPearlApp
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
        val app = activity.application as BlackPearlApp
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
                    (activity.application as BlackPearlApp).artCache,
                    it,
                    targetPx = cardSize,
                    artOverrides = settings.artOverrides,
                )
            } ?: ImageView(context).apply {
                CustomIcon.bind(
                    this, iconLoader,
                    (activity.application as BlackPearlApp).artCache,
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
            holder.root.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            // Tap-to-focus: first tap on an unfocused card only moves the
            // selection; tapping the already-selected card launches it.
            // The slot stays non-focusable so d-pad routing is unchanged.
            holder.root.setOnClickListener {
                if (state.selectedKey == entry.key && state.dockSlot == null) {
                    launch(entry)
                } else {
                    state.select(entry.key)
                }
            }
            // Long-press: favorite / open with / set art / add to grid.
            holder.root.setOnLongClickListener {
                state.select(entry.key)
                openEntryMenu(entry)
                true
            }
        }
    }
}
