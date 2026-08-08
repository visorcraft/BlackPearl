package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.art.ArtTile
import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.library.CollectionsOps
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.rom.RomProfiles
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.Folders
import com.visorcraft.ghostgalleon.settings.GridSlots
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.state.DeckState
import com.visorcraft.ghostgalleon.ui.BaseDeckActivity

// The curated grid: renders settings.gridSlots — ordered, null = blank
// ("+") cell — padded up to a whole number of rows (GridSlots.paddedCount)
// so an incomplete row never shows. Padded cells are ordinary blank slots:
// focusable, they open the app picker, and filling one extends the stored
// list. Blank slots open the app picker; filled slots launch on confirm
// and offer Move/Remove on long-press.
//
// settings.gridDirection picks the viewport axis: "vertical" (default) is
// one scrolling GridView; "horizontal" (3DS-style) lays fixed-size page
// panels left to right in a page-aligned horizontal scroller. Slot
// indices, GridNavigation math, and page-dot semantics are identical in
// both — only the scroll axis changes.
class GridDeck(
    private val activity: AppCompatActivity,
    private val state: DeckState,
    private val settings: Settings,
    private val library: AppLibrary,
    private val iconLoader: AppIconLoader,
    private val roms: List<RomEntry>,
) : Deck {

    // Rendered slot count: the stored list padded up to complete rows.
    // Navigation, page dots, and ensure-visible all use this padded count.
    private val slotCount get() =
        GridSlots.paddedCount(settings.gridSlots.size, settings.gridColumns)
    private val isHorizontal get() = settings.gridDirection == "horizontal"
    private val nav get() = GridNavigation(
        slotCount, settings.gridColumns, pageGeometry?.visibleRows ?: 1)
    // Slot tiles resolve through the FULL cache: a hidden app that already
    // occupies a slot stays rendered and launchable there (hiding only
    // removes it from the picker and other all-apps lists).
    private val visibleByPkg by lazy {
        library.all(settings).associateBy { it.packageName }
    }
    // ROM slots hold "rom:<entry id>" values (SlotKey); resolve them against
    // the ROM index snapshot. A ROM that drops out of the library renders as
    // a dimmed "Missing" tile but keeps its slot value (removable via
    // long-press).
    private val romByKey by lazy { roms.associateBy { SlotKey.rom(it.id) } }

    // Move mode: a lifted tile swaps contents slot-by-slot until dropped
    // (saved to settings) or cancelled (working copy discarded).
    private var moveIndex: Int? = null
    private var moveWorking: MutableList<String?>? = null
    private var pulseAnimator: ObjectAnimator? = null

    // Modals (at most one at a time).
    private var slotMenu: SlotMenu? = null
    private var picker: AppPicker? = null
    private var folderPanel: FolderPanel? = null
    private var rootView: FrameLayout? = null

    // Live view state captured by primaryView() so updateSelection() can
    // move the selection ring without rebuilding the whole view tree.
    private var gridView: GridView? = null
    private var gridAdapter: GridAdapter? = null
    private var pagerScroll: HorizontalScrollView? = null
    private var pageGrids: List<GridView> = emptyList()
    private var pageDotsView: LinearLayout? = null
    private var pageGeometry: PageGeometry? = null
    private var dockBar: DockBar? = null
    private var hintView: TextView? = null

    private val dockMove = DockMoveState()

    private val dockNav get() = DockNavigation(
        DockSlots.visibleCount(dockMove.working ?: settings.dockSlots),
        slotCount, settings.gridColumns)

    private class PageGeometry(
        val cellsPerPage: Int,
        val visibleRows: Int,
        val cellHeight: Int,
        val spacing: Int,
        val paddingV: Int,
    )

    // During move mode the working copy is what the grid renders.
    private fun slotPackages(): List<String?> = moveWorking ?: settings.gridSlots

    private fun selectedIndex(): Int =
        (moveIndex ?: state.selectedSlot).coerceIn(0, (slotCount - 1).coerceAtLeast(0))

    override fun updateSelection(): Boolean {
        pageGeometry ?: return false
        if (isHorizontal) {
            pagerScroll ?: return false
        } else {
            gridView ?: return false
            gridAdapter ?: return false
        }
        // Keep the selected tile on screen, then move the ring/scale to it.
        ensureSelectionVisible()
        rebindVisibleCells()
        updatePageDots()
        // Dock focus is a selection change too: repaint the dock ring (and
        // the lifted tile's pulse during a dock move) and switch the hint
        // bar between grid and dock actions.
        dockBar?.updateFocus(state.dockSlot, dockMove.index)
        hintView?.text = if (dockMove.active) {
            HintBar.MOVE_TEXT
        } else {
            HintBar.textFor(state.dockSlot != null)
        }
        return true
    }

    // Moves the ring/scale to the new selection by updating the visible
    // cells in place. Deliberately NOT adapter.notifyDataSetChanged():
    // every notify arms AdapterView.rememberSyncState, and because the grid
    // auto-resurrects an internal selection at position 0 (checkFocus), the
    // data-change layout's LAYOUT_SYNC restores position 0's stale top —
    // reverting any scroll that detached row 0 and stranding our selection
    // off-screen. Cells (re)attached while scrolling are bound with the
    // current selection by getView, so only the already-visible cells need
    // this pass. In move mode the cell CONTENTS may also have swapped, so
    // visible cells are fully re-populated.
    private fun rebindVisibleCells(repopulate: Boolean = moveIndex != null) {
        pulseAnimator?.cancel()
        pulseAnimator = null
        val selected = selectedIndex()
        forEachAttachedCell { frame, position, adapter ->
            frame.alpha = 1f
            if (repopulate) {
                adapter.populateFrame(frame, position)
            } else {
                applySelectionVisuals(frame, position == selected)
            }
            if (position == moveIndex) startPulse(frame)
        }
    }

    // Iterates the currently attached cells with their absolute slot index,
    // across either the single vertical grid or every horizontal page panel
    // (page panels fit exactly, so all their cells stay attached).
    private fun forEachAttachedCell(block: (FrameLayout, Int, GridAdapter) -> Unit) {
        if (isHorizontal) {
            val cellsPerPage = pageGeometry?.cellsPerPage ?: return
            pageGrids.forEachIndexed { page, grid ->
                val adapter = grid.adapter as? GridAdapter ?: return@forEachIndexed
                val first = page * cellsPerPage + grid.firstVisiblePosition
                for (i in 0 until grid.childCount) {
                    val position = first + i
                    if (position >= slotCount) break
                    val frame = grid.getChildAt(i) as? FrameLayout ?: continue
                    block(frame, position, adapter)
                }
            }
        } else {
            val grid = gridView ?: return
            val adapter = gridAdapter ?: return
            val first = grid.firstVisiblePosition
            for (i in 0 until grid.childCount) {
                val position = first + i
                if (position >= slotCount) break
                val frame = grid.getChildAt(i) as? FrameLayout ?: continue
                block(frame, position, adapter)
            }
        }
    }

    // Accent ring + 1.08 scale for the selected cell, plain card for the
    // rest. Shared by the adapter bind and the in-place rebind so both
    // paths paint identical visuals. While the dock holds focus the grid
    // shows NO ring — the focused dock slot carries it instead.
    private fun applySelectionVisuals(frame: FrameLayout, selected: Boolean) {
        val cell = frame.getChildAt(0)
        cell.background = if (selected && state.dockSlot == null) {
            TileBackgrounds.selected(frame.context, settings.accentColor)
        } else {
            TileBackgrounds.card(frame.context)
        }
        val scale = if (selected && state.dockSlot == null) 1.08f else 1f
        frame.scaleX = scale
        frame.scaleY = scale
    }

    // The lifted tile pulses its alpha so it reads as "in hand".
    private fun startPulse(frame: FrameLayout) {
        pulseAnimator = ObjectAnimator.ofFloat(frame, View.ALPHA, 1f, 0.55f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    // Keeps the selected cell on screen. Vertical: scroll minimally so the
    // selected cell is fully visible (above the viewport -> up just enough,
    // below -> down just enough, already visible -> no scroll at all),
    // derived from the live grid so a stale cached offset can never strand
    // the selection. Horizontal: snap the viewport so the selected slot's
    // whole page is in view (ensure-visible on X, page-aligned).
    private fun ensureSelectionVisible() {
        val geometry = pageGeometry ?: return
        if (isHorizontal) {
            val pager = pagerScroll ?: return
            if (pager.width == 0) return
            val target = (selectedIndex() / geometry.cellsPerPage) * pager.width
            if (pager.scrollX != target) pager.smoothScrollTo(target, 0)
            return
        }
        val grid = gridView ?: return
        val columns = settings.gridColumns
        val row = selectedIndex() / columns
        val stride = geometry.cellHeight + geometry.spacing
        val rowTop = geometry.paddingV + row * stride
        val firstChild = grid.getChildAt(0)
        val scrollPx = if (firstChild != null) {
            (grid.firstVisiblePosition / columns) * stride -
                firstChild.top + geometry.paddingV
        } else {
            0
        }
        val delta = EnsureVisibleScroll.delta(
            rowTop, rowTop + geometry.cellHeight, scrollPx, scrollPx + grid.height)
        if (delta != 0) grid.scrollListBy(delta)
    }

    // Repaints the existing dot views: current page accent, the rest dimmed.
    // Page count cannot change on a selection-only update.
    private fun updatePageDots() {
        val dots = pageDotsView ?: return
        val geometry = pageGeometry ?: return
        val currentPage = (selectedIndex() / geometry.cellsPerPage) + 1
        for (i in 0 until dots.childCount) {
            val page = i + 1
            dots.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (page == currentPage) settings.accentColor else 0x4DFFFFFF)
            }
        }
    }

    // One 8dp dot per page: current page in the accent color, the rest
    // dimmed. A single-page grid needs no dots.
    private fun buildPageDots(context: Context, dots: LinearLayout, totalPages: Int) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val cellsPerPage = pageGeometry?.cellsPerPage ?: 1
        val currentPage = (selectedIndex() / cellsPerPage) + 1
        dots.removeAllViews()
        if (totalPages > 1) {
            for (page in 1..totalPages) {
                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            if (page == currentPage) settings.accentColor
                            else 0x4DFFFFFF
                        )
                    }
                }
                dots.addView(dot, LinearLayout.LayoutParams(
                    dp(8), dp(8)).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                })
            }
        }
    }

    // Tap-to-focus: first tap on an unfocused slot only moves the
    // selection; tapping the selected filled slot launches it, and a blank
    // slot opens the picker on first tap when focused. During move mode a
    // tap drops the lifted tile on that slot. Folder tiles open the member list.
    private fun onSlotTap(position: Int) {
        when {
            moveIndex != null -> dropMove(tapSlot = position)
            state.selectedSlot == position -> {
                val key = settings.gridSlots.getOrNull(position)
                when {
                    key == null -> openPicker(position)
                    SlotKey.isFolder(key) -> openFolder(key)
                    else -> launchSlotKey(activity, state, roms, key)
                }
            }
            else -> state.selectSlot(
                position, settings.gridSlots.getOrNull(position))
        }
    }

    // Long-press opens Move/Remove (filled) or New folder (blank).
    private fun onSlotLongPress(position: Int) {
        if (moveIndex == null) openSlotMenu(position)
    }

    private fun memberLabel(key: String): String {
        SlotKey.romId(key)?.let { id ->
            return roms.firstOrNull { it.id == id }?.name ?: key
        }
        if (SlotKey.isFolder(key)) {
            val fid = SlotKey.folderId(key)
            return settings.folders[fid]?.name ?: key
        }
        return visibleByPkg[key]?.label ?: key
    }

    private fun openFolder(folderKey: String) {
        val fid = SlotKey.folderId(folderKey) ?: return
        val spec = settings.folders[fid]
        val name = spec?.name ?: fid
        val members = Folders.members(settings.folders, fid).map { k ->
            k to memberLabel(k)
        }
        closeFolderPanel()
        val panel = FolderPanel(
            activity,
            settings.accentColor,
            title = name,
            members = members,
            onLaunch = { memberKey ->
                closeFolderPanel()
                launchSlotKey(activity, state, roms, memberKey)
            },
            onClose = { closeFolderPanel() },
            onRemoveMember = { memberKey ->
                val app = activity.application as GhostGalleonApp
                val folders = Folders.removeMember(app.settings.folders, fid, memberKey)
                app.updateSettings(app.settings.copy(folders = folders))
                Toast.makeText(activity, "Removed from folder", Toast.LENGTH_SHORT).show()
            },
        )
        folderPanel = panel
        rootView?.addView(panel.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun closeFolderPanel() {
        folderPanel?.let { rootView?.removeView(it.view) }
        folderPanel = null
    }

    override fun primaryView(context: Context): View {
        // FrameLayout root so an optional dimmed wallpaper can sit behind
        // the whole grid stack; content stays transparent when one is set.
        // Modals (slot menu, app picker) are added on top of this root.
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = false
            clipToPadding = false
        }
        rootView = root
        settings.wallpaperUri?.let { uri ->
            // The ImageView goes in immediately (black behind the grid);
            // decode+downscale runs off the UI thread and fills it in.
            val wallpaperView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.35f
            }
            root.addView(wallpaperView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
            loadWallpaperAsync(context, uri, wallpaperView)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        // Page dots live in the dock bar center; the layout listener below
        // repopulates them once the page geometry is known.
        val pageDots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        // The frame clips the grid's partial over-drawn bottom row while
        // the grid itself stays unclipped so selected cells can scale over
        // their neighbours into the grid padding.
        val gridFrame = FrameLayout(context)
        val gridArea = if (isHorizontal) {
            buildHorizontalPager(context, pageDots)
        } else {
            buildVerticalGrid(context, pageDots)
        }
        gridFrame.addView(gridArea, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(gridFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // Bottom stack: HintBar above the dock bar, so the dock sits at the
        // very bottom edge. The hint text follows focus —
        // grid actions normally, dock actions while the dock is focused.
        if (settings.showHints) {
            val hints = HintBar.build(context) as TextView
            hints.text = HintBar.textFor(state.dockSlot != null)
            hintView = hints
            content.addView(hints, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        // Apps-only dock bar (90% width, centered): fixed slots, "+" for
        // empties, page dots at the end.
        val bar = DockBar(
            activity, settings, library, iconLoader, roms,
            slots = { dockMove.working ?: settings.dockSlots },
            onTap = ::onDockTap,
            onLongPress = ::onDockLongPress,
        )
        dockBar = bar
        content.addView(bar.build(context, pageDots))
        root.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Optional clock/battery overlay (Settings → Display & Grid → chrome).
        if (settings.browseChrome.deckStatusPill) {
            root.addView(
                StatusPill.build(context, compact = true),
                StatusPill.overlayLayoutParams(context),
            )
        }
        // Dual: Swap/Settings only on the larger panel, pinned bottom-left /
        // bottom-right so dock+hints never push them mid-screen. Single: same.
        if (shouldHostSystemChromeIcons(activity)) {
            attachSystemChromeOverlay(root, context, activity, state)
        }
        // A rebuild while the dock holds focus (settings save, mode
        // toggle) must repaint the ring immediately — updateFocus
        // otherwise only runs on selection-only updates.
        bar.updateFocus(state.dockSlot)
        return root
    }

    // Default "vertical" mode: one GridView scrolling top to bottom.
    private fun buildVerticalGrid(context: Context, pageDots: LinearLayout): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val columns = settings.gridColumns
        val gridPadH = dp(16)
        val gridPadV = dp(16)
        // Extra top room so the selected cell's 4dp stroke + 1.08 scale
        // over-draw never hits the grid's top edge on the first row.
        val gridPadTop = gridPadV + dp(8)
        val spacing = dp(12)
        val cellPadding = dp(8)
        val labelHeight = if (settings.showLabels) dp(20) else 0

        val grid = GridView(context).apply {
            numColumns = columns
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = spacing
            verticalSpacing = spacing
            setPadding(gridPadH, gridPadTop, gridPadH, gridPadV)
            clipChildren = false
            clipToPadding = false
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            // Items stay non-focusable so d-pad routing is unchanged.
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                onSlotTap(position)
            }
            onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
                onSlotLongPress(position)
                true
            }
        }
        // Size cells from the grid's measured bounds so rows always fit
        // exactly: no half-clipped row and no overlap with the bottom bars.
        // The listener STAYS registered: a rebuild that races the closing
        // IME (picker -> settings update) lays out once with the window
        // still shrunk by the keyboard, then again at full height. Only a
        // changed height recomputes, so this converges without loops.
        grid.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            private var lastHeight = -1
            override fun onGlobalLayout() {
                if (grid.height == 0 || grid.height == lastHeight) return
                lastHeight = grid.height
                val cellWidth =
                    (grid.width - gridPadH * 2 - spacing * (columns - 1)) / columns
                val iconSize = (cellWidth * 0.6f).toInt()
                val guessCellHeight = iconSize + labelHeight + cellPadding * 2
                val visibleRows =
                    ((grid.height - gridPadTop - gridPadV + spacing) /
                        (guessCellHeight + spacing)).coerceAtLeast(1)
                val cellHeight =
                    (grid.height - gridPadTop - gridPadV -
                        spacing * (visibleRows - 1)) / visibleRows
                val adapter = (grid.adapter as? GridAdapter)
                    ?: GridAdapter(context, cellWidth, iconSize, cellHeight, cellPadding)
                        .also { grid.adapter = it }
                adapter.updateMetrics(cellWidth, iconSize, cellHeight)
                // Cells already attached carry the previous height in their
                // LayoutParams; restamp them so a geometry change (IME
                // race) actually resizes the visible tiles.
                for (i in 0 until grid.childCount) {
                    grid.getChildAt(i).layoutParams =
                        AbsListView.LayoutParams(cellWidth, cellHeight)
                }
                val cellsPerPage = columns * visibleRows
                // Capture live view state for selection-only updates.
                gridView = grid
                gridAdapter = adapter
                pageDotsView = pageDots
                pageGeometry = PageGeometry(
                    cellsPerPage, visibleRows, cellHeight, spacing, gridPadTop)
                val totalPages =
                    ((slotCount + cellsPerPage - 1) / cellsPerPage).coerceAtLeast(1)
                buildPageDots(context, pageDots, totalPages)
                grid.requestLayout()
                // Show the selection: scroll minimally (after children
                // attach) so the selected cell is fully visible, wherever
                // it sits in the grid.
                grid.post { ensureSelectionVisible() }
            }
        })
        return grid
    }

    // "horizontal" mode (3DS-style): fixed-size page panels laid out left
    // to right inside a horizontal scroller. Each page panel is a
    // GridView of columns x visibleRows cells, exactly viewport-sized and
    // non-scrolling. Selection changes snap the viewport a whole page so
    // the selected slot's page is in view. Touch scrolling is disabled: the
    // viewport is always page-aligned, so the dots never lie.
    private fun buildHorizontalPager(context: Context, pageDots: LinearLayout): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val columns = settings.gridColumns
        val gridPadH = dp(16)
        val gridPadV = dp(16)
        val gridPadTop = gridPadV + dp(8)
        val spacing = dp(12)
        val cellPadding = dp(8)
        val labelHeight = if (settings.showLabels) dp(20) else 0

        val pager = object : HorizontalScrollView(context) {
            override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false
            override fun onTouchEvent(ev: MotionEvent?): Boolean = false
        }
        pager.isHorizontalFadingEdgeEnabled = false
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        pager.addView(row, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Same persistent-listener discipline as the vertical grid, keyed
        // on the measured page bounds: an IME-race rebuild re-lays out at
        // full height and recomputes pages/dots exactly once.
        pager.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            private var lastWidth = -1
            private var lastHeight = -1
            override fun onGlobalLayout() {
                if (pager.width == 0 || pager.height == 0) return
                if (pager.width == lastWidth && pager.height == lastHeight) return
                lastWidth = pager.width
                lastHeight = pager.height
                val pageWidth = pager.width
                val cellWidth =
                    (pageWidth - gridPadH * 2 - spacing * (columns - 1)) / columns
                val iconSize = (cellWidth * 0.6f).toInt()
                val guessCellHeight = iconSize + labelHeight + cellPadding * 2
                val visibleRows =
                    ((pager.height - gridPadTop - gridPadV + spacing) /
                        (guessCellHeight + spacing)).coerceAtLeast(1)
                val cellHeight =
                    (pager.height - gridPadTop - gridPadV -
                        spacing * (visibleRows - 1)) / visibleRows
                val cellsPerPage = columns * visibleRows
                val totalPages =
                    ((slotCount + cellsPerPage - 1) / cellsPerPage).coerceAtLeast(1)
                row.removeAllViews()
                val grids = mutableListOf<GridView>()
                for (page in 0 until totalPages) {
                    val offset = page * cellsPerPage
                    val count = (slotCount - offset).coerceAtMost(cellsPerPage)
                    val pageGrid = GridView(context).apply {
                        numColumns = columns
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        horizontalSpacing = spacing
                        verticalSpacing = spacing
                        setPadding(gridPadH, gridPadTop, gridPadH, gridPadV)
                        clipChildren = false
                        clipToPadding = false
                        selector = android.graphics.drawable.ColorDrawable(
                            Color.TRANSPARENT)
                        adapter = GridAdapter(
                            context, cellWidth, iconSize, cellHeight, cellPadding,
                            slotOffset = offset, fixedCount = count)
                        onItemClickListener =
                            AdapterView.OnItemClickListener { _, _, position, _ ->
                                onSlotTap(offset + position)
                            }
                        onItemLongClickListener =
                            AdapterView.OnItemLongClickListener { _, _, position, _ ->
                                onSlotLongPress(offset + position)
                                true
                            }
                    }
                    grids.add(pageGrid)
                    row.addView(pageGrid, LinearLayout.LayoutParams(
                        pageWidth, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                // Capture live view state for selection-only updates.
                pagerScroll = pager
                pageGrids = grids
                pageDotsView = pageDots
                pageGeometry = PageGeometry(
                    cellsPerPage, visibleRows, cellHeight, spacing, gridPadTop)
                buildPageDots(context, pageDots, totalPages)
                // Snap to the selected slot's page once laid out.
                pager.post { ensureSelectionVisible() }
            }
        })
        return pager
    }

    // Kicks the wallpaper decode onto a background executor so view build
    // never blocks on I/O + bitmap work. The result is applied on the main
    // thread only when it is still current: the target view must still be
    // attached and the wallpaper setting unchanged (a settings rebuild
    // races the decode, and the stale bitmap must not land on a recycled
    // view).
    private fun loadWallpaperAsync(context: Context, uriString: String, target: ImageView) {
        WALLPAPER_EXECUTOR.execute {
            val bitmap = loadWallpaper(context, uriString) ?: return@execute
            target.post {
                val current = (activity.application as GhostGalleonApp).settings.wallpaperUri
                if (target.isAttachedToWindow && current == uriString) {
                    target.setImageBitmap(bitmap)
                }
            }
        }
    }

    // Decodes the SAF-picked wallpaper downscaled to roughly display size;
    // null on any failure (revoked grant, deleted file) so the grid simply
    // falls back to black. Runs on WALLPAPER_EXECUTOR, never the UI thread.
    private fun loadWallpaper(context: Context, uriString: String): android.graphics.Bitmap? =
        runCatching {
            val uri = android.net.Uri.parse(uriString)
            val metrics = context.resources.displayMetrics
            val bounds = android.graphics.BitmapFactory.Options()
                .apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= metrics.widthPixels &&
                bounds.outHeight / (sample * 2) >= metrics.heightPixels
            ) {
                sample *= 2
            }
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()

    private fun updateDockSlots(slots: List<String?>, toast: String? = null) {
        val app = activity.application as GhostGalleonApp
        DockActions.persist(activity, app, slots, toast)
    }

    private fun onDockTap(index: Int) {
        val bar = dockBar ?: return
        when {
            dockMove.active -> dropDockMove(tapSlot = index)
            bar.isBlank(index) -> openDockPicker(index)
            else -> bar.keyAt(index)?.let { launchSlotKey(activity, state, roms, it) }
        }
    }

    private fun onDockLongPress(index: Int) {
        if (!dockMove.active && moveIndex == null &&
            dockBar?.isBlank(index) == false
        ) {
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
                    val app = activity.application as GhostGalleonApp
                    val next = DockActions.removeAt(app, index)
                    updateDockSlots(next, "Removed from dock")
                    DockActions.clampFocus(state.dockSlot, next)?.let { state.focusDock(it) }
                }
                else -> {}
            }
        }
        slotMenu = menu
        DeckOverlays.attach(rootView, menu.view)
    }

    private fun startDockMove(index: Int) {
        dockMove.start(index, settings.dockSlots)
        hintView?.text = HintBar.MOVE_TEXT
        state.focusDock(index)
        dockBar?.updateFocus(index, moving = index)
    }

    private fun handleDockMoveAction(action: Action, from: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT -> {
                val to = dockNav.move(from, action)
                if (to != from) {
                    val next = dockMove.swap(from, to) ?: return true
                    dockBar?.rebind()
                    state.focusDock(next)
                    dockBar?.updateFocus(next, moving = next)
                }
            }
            Action.CONFIRM -> dropDockMove()
            Action.BACK -> cancelDockMove()
            else -> {}
        }
        return true
    }

    private fun dropDockMove(tapSlot: Int? = null) {
        val result = dockMove.drop(tapSlot) ?: return
        hintView?.text = HintBar.textFor(state.dockSlot != null)
        updateDockSlots(result.compacted)
        state.focusDock(result.focusIndex)
    }

    private fun cancelDockMove() {
        dockMove.clear()
        hintView?.text = HintBar.textFor(state.dockSlot != null)
        dockBar?.rebind()
        dockBar?.updateFocus(state.dockSlot)
    }

    private fun handleDockAction(action: Action, dockIndex: Int): Boolean {
        when (action) {
            Action.NAV_LEFT, Action.NAV_RIGHT ->
                state.focusDock(dockNav.move(dockIndex, action))
            Action.NAV_UP, Action.BACK -> {
                val target = dockNav.exitToGrid(dockIndex)
                state.selectSlot(target, settings.gridSlots.getOrNull(target))
            }
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
        return true
    }

    private fun openDockPicker(slot: Int) {
        val app = activity.application as GhostGalleonApp
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            title = "Add to dock",
            onPick = { key ->
                closePicker()
                updateDockSlots(DockActions.fill(app, slot, key))
            },
            onHide = { packageName ->
                closePicker()
                DeckOverlays.hideApp(activity, packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        DeckOverlays.attach(rootView, appPicker.view)
    }

    private fun pinToDock(key: String) {
        val app = activity.application as GhostGalleonApp
        DockActions.pin(activity, app, key) { slots, toast -> updateDockSlots(slots, toast) }
    }

    private fun unpinFromDock(key: String) {
        val app = activity.application as GhostGalleonApp
        DockActions.unpin(activity, app, key) { slots, toast -> updateDockSlots(slots, toast) }
    }

    private fun promptAddToCollection(key: String) {
        val app = activity.application as GhostGalleonApp
        CollectionDialogs.promptAdd(activity, app, listOf(key))
    }

    private fun updateGridSlots(slots: List<String?>, focusSlot: Int) {
        val app = activity.application as GhostGalleonApp
        app.updateSettings(app.settings.copy(gridSlots = slots))
        state.selectSlot(focusSlot, slots.getOrNull(focusSlot))
    }

    private fun openWithForRom(rom: RomEntry) {
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
                val app = activity.application as GhostGalleonApp
                app.updateSettings(
                    app.settings.copy(
                        defaultPlayers = app.settings.defaultPlayers +
                            (rom.platformId to player.id),
                    ),
                    notify = false,
                )
                launchSlotKey(
                    activity, state, roms, SlotKey.rom(rom.id),
                    playerId = player.id,
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Per-ROM preferred player profile (persists in settings.romProfiles). */
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
                val app = activity.application as GhostGalleonApp
                val nextProfiles = if (which >= players.size) {
                    RomProfiles.clearProfile(app.settings.romProfiles, rom.id)
                } else {
                    RomProfiles.setProfile(
                        app.settings.romProfiles, rom.id, players[which].id)
                }
                app.updateSettings(app.settings.copy(romProfiles = nextProfiles))
                Toast.makeText(activity, "Player saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFolderAt(slot: Int) {
        val app = activity.application as GhostGalleonApp
        val id = Folders.nextId(app.settings.folders)
        val folders = Folders.create(app.settings.folders, id, "Folder")
        val key = Folders.key(id)
        val slots = GridSlots.fill(app.settings.gridSlots, slot, key)
        app.updateSettings(app.settings.copy(folders = folders, gridSlots = slots))
        state.selectSlot(slot, key)
        Toast.makeText(activity, "Folder created", Toast.LENGTH_SHORT).show()
    }

    private fun renameFolder(folderId: String) {
        val current = settings.folders[folderId]?.name ?: folderId
        val input = android.widget.EditText(activity).apply {
            setText(current)
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0x66FFFFFF)
            hint = "Folder name"
            setSingleLine()
        }
        val container = FrameLayout(activity).apply {
            val margin = (20 * resources.displayMetrics.density).toInt()
            setPadding(margin, (12 * resources.displayMetrics.density).toInt(), margin, 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("Rename folder")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val app = activity.application as GhostGalleonApp
                app.updateSettings(app.settings.copy(
                    folders = Folders.rename(app.settings.folders, folderId, name)))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addFolderMember(folderId: String) {
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            title = "Add to folder",
            onPick = { key ->
                closePicker()
                val app = activity.application as GhostGalleonApp
                app.updateSettings(app.settings.copy(
                    folders = Folders.addMember(app.settings.folders, folderId, key)))
                Toast.makeText(activity, "Added to folder", Toast.LENGTH_SHORT).show()
            },
            onHide = { packageName ->
                closePicker()
                DeckOverlays.hideApp(activity, packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        DeckOverlays.attach(rootView, appPicker.view)
    }

    private fun removeFolderSlot(slot: Int, folderKey: String) {
        val folderId = SlotKey.folderId(folderKey)
        val app = activity.application as GhostGalleonApp
        val slots = GridSlots.remove(app.settings.gridSlots, slot)
        val folders = if (folderId != null) {
            Folders.delete(app.settings.folders, folderId)
        } else {
            app.settings.folders
        }
        app.updateSettings(app.settings.copy(gridSlots = slots, folders = folders))
        state.selectSlot(slot, null)
    }

    // --- Slot menu (Move / Pin to dock / Rename / Custom icon / Remove) ---

    private fun openSlotMenu(slot: Int) {
        state.selectSlot(slot, settings.gridSlots.getOrNull(slot))
        val key = settings.gridSlots.getOrNull(slot)
        val isFolder = SlotKey.isFolder(key)
        val isRom = key != null && SlotKey.isRom(key)
        val isApp = key != null && !isRom && !isFolder
        val fav = key != null && key in settings.favorites
        val choices = buildList {
            when {
                key == null -> {
                    add(SlotMenu.Choice.NEW_FOLDER)
                    add(SlotMenu.Choice.CANCEL)
                }
                isFolder -> {
                    add(SlotMenu.Choice.MOVE)
                    add(SlotMenu.Choice.RENAME)
                    add(SlotMenu.Choice.ADD_MEMBER)
                    add(SlotMenu.Choice.REMOVE)
                    add(SlotMenu.Choice.CANCEL)
                }
                else -> {
                    add(SlotMenu.Choice.DETAILS)
                    add(SlotMenu.Choice.COPY_TITLE)
                    if (isRom && key != null && gridRelatedOptions(key).isNotEmpty()) {
                        add(SlotMenu.Choice.BROWSE_RELATED)
                    }
                    if (key != null) {
                        if (com.visorcraft.ghostgalleon.library.LibraryBrowse.isUnplayed(
                                key,
                                settings.lastLaunchedMs,
                            )
                        ) {
                            add(SlotMenu.Choice.MARK_PLAYED)
                        }
                        val stats = com.visorcraft.ghostgalleon.library.PlayStats(
                            lastLaunchedMs = settings.lastLaunchedMs,
                            totalPlaytimeMs = settings.playtimeMs,
                        )
                        if (com.visorcraft.ghostgalleon.library.SessionMath.hasStats(stats, key)) {
                            add(SlotMenu.Choice.CLEAR_PLAY_STATS)
                        }
                    }
                    add(SlotMenu.Choice.MOVE)
                    if (key != null && DockSlots.containsKey(settings.dockSlots, key)) {
                        add(SlotMenu.Choice.UNPIN_FROM_DOCK)
                    } else {
                        add(SlotMenu.Choice.PIN_TO_DOCK)
                    }
                    add(if (fav) SlotMenu.Choice.UNFAVORITE else SlotMenu.Choice.FAVORITE)
                    add(SlotMenu.Choice.ADD_TO_COLLECTION)
                    if (isRom) {
                        add(SlotMenu.Choice.OPEN_WITH)
                        add(SlotMenu.Choice.PLAYER)
                        add(SlotMenu.Choice.SET_ART)
                        add(SlotMenu.Choice.HIDE)
                    }
                    if (isApp) {
                        add(SlotMenu.Choice.APP_INFO)
                        add(SlotMenu.Choice.RENAME)
                        if (key in settings.customNames) add(SlotMenu.Choice.RESET_NAME)
                        add(SlotMenu.Choice.CUSTOM_ICON)
                        if (key in settings.customIcons) add(SlotMenu.Choice.RESET_ICON)
                    }
                    add(SlotMenu.Choice.REMOVE)
                    add(SlotMenu.Choice.CANCEL)
                }
            }
        }
        val menu = SlotMenu(activity, settings.accentColor, choices) { choice ->
            closeSlotMenu()
            when (choice) {
                SlotMenu.Choice.DETAILS -> key?.let { k -> showGridDetails(k) }
                SlotMenu.Choice.COPY_TITLE -> key?.let { k ->
                    copyGridTitleToClipboard(gridEntryLabel(k))
                }
                SlotMenu.Choice.BROWSE_RELATED -> key?.let { k ->
                    showGridBrowseRelated(k)
                }
                SlotMenu.Choice.MARK_PLAYED -> key?.let { k -> markGridAsPlayed(k) }
                SlotMenu.Choice.CLEAR_PLAY_STATS -> key?.let { k -> clearGridPlayStats(k) }
                SlotMenu.Choice.APP_INFO -> key?.let { k ->
                    if (!SlotKey.isRom(k) && !SlotKey.isFolder(k)) openAppInfo(k)
                }
                SlotMenu.Choice.MOVE -> startMove(slot)
                SlotMenu.Choice.PIN_TO_DOCK ->
                    settings.gridSlots.getOrNull(slot)?.let(::pinToDock)
                SlotMenu.Choice.UNPIN_FROM_DOCK ->
                    settings.gridSlots.getOrNull(slot)?.let(::unpinFromDock)
                SlotMenu.Choice.FAVORITE, SlotMenu.Choice.UNFAVORITE -> key?.let { k ->
                    val app = activity.application as GhostGalleonApp
                    val next = com.visorcraft.ghostgalleon.library.CollectionsOps
                        .toggleFavorite(app.settings.favorites, k)
                    app.updateSettings(app.settings.copy(favorites = next))
                }
                SlotMenu.Choice.ADD_TO_COLLECTION -> key?.let { k ->
                    promptAddToCollection(k)
                }
                SlotMenu.Choice.OPEN_WITH -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    val rom = roms.firstOrNull { it.id == id } ?: return@let
                    openWithForRom(rom)
                }
                SlotMenu.Choice.PLAYER -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    val rom = roms.firstOrNull { it.id == id } ?: return@let
                    showPlayerProfileMenu(rom)
                }
                SlotMenu.Choice.SET_ART -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    (activity as? BaseDeckActivity)?.requestCustomIcon { uri ->
                        val app = activity.application as GhostGalleonApp
                        // Drop mem+disk so the override is re-decoded.
                        app.artCache.invalidate(id)
                        app.updateSettings(app.settings.copy(
                            artOverrides = app.settings.artOverrides + (id to uri.toString())))
                    }
                }
                SlotMenu.Choice.HIDE -> key?.let { k ->
                    val id = SlotKey.romId(k) ?: return@let
                    val app = activity.application as GhostGalleonApp
                    val next = com.visorcraft.ghostgalleon.library.HiddenRoms
                        .hide(app.settings.hiddenRomIds, id)
                    app.updateSettings(app.settings.copy(hiddenRomIds = next))
                    Toast.makeText(activity, "Hidden from library", Toast.LENGTH_SHORT).show()
                }
                SlotMenu.Choice.NEW_FOLDER -> createFolderAt(slot)
                SlotMenu.Choice.ADD_MEMBER -> key?.let { k ->
                    SlotKey.folderId(k)?.let(::addFolderMember)
                }
                SlotMenu.Choice.RENAME -> when {
                    isFolder -> SlotKey.folderId(key)?.let(::renameFolder)
                    else -> key?.let(::showRenameDialog)
                }
                SlotMenu.Choice.RESET_NAME -> key?.let { pkg ->
                    val app = activity.application as GhostGalleonApp
                    app.updateSettings(app.settings.copy(
                        customNames = app.settings.customNames - pkg))
                }
                SlotMenu.Choice.CUSTOM_ICON -> key?.let { pkg ->
                    (activity as? BaseDeckActivity)?.requestCustomIcon { uri ->
                        val app = activity.application as GhostGalleonApp
                        app.updateSettings(app.settings.copy(
                            customIcons = app.settings.customIcons +
                                (pkg to uri.toString())))
                    }
                }
                SlotMenu.Choice.RESET_ICON -> key?.let { pkg ->
                    val app = activity.application as GhostGalleonApp
                    app.updateSettings(app.settings.copy(
                        customIcons = app.settings.customIcons - pkg))
                }
                SlotMenu.Choice.REMOVE -> when {
                    isFolder && key != null -> removeFolderSlot(slot, key)
                    else -> updateGridSlots(GridSlots.remove(settings.gridSlots, slot), slot)
                }
                SlotMenu.Choice.CANCEL,
                SlotMenu.Choice.ADD_TO_GRID,
                SlotMenu.Choice.REMOVE_FROM_COLLECTION,
                SlotMenu.Choice.MOVE_TO_TOP,
                SlotMenu.Choice.MOVE_UP,
                SlotMenu.Choice.MOVE_DOWN,
                SlotMenu.Choice.MOVE_TO_END,
                -> {}
            }
        }
        slotMenu = menu
        rootView?.addView(menu.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun gridEntryLabel(key: String): String {
        SlotKey.romId(key)?.let { id ->
            roms.firstOrNull { it.id == id }?.name?.let { return it }
        }
        return settings.customNames[key]
            ?: visibleByPkg[key]?.label
            ?: key.substringAfterLast(':').ifBlank { key }
    }

    private fun copyGridTitleToClipboard(title: String) {
        val text = title.trim().ifEmpty { return }
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
        if (clipboard == null) {
            Toast.makeText(activity, "Clipboard unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("title", text),
        )
        Toast.makeText(activity, "Copied title", Toast.LENGTH_SHORT).show()
    }

    private fun markGridAsPlayed(key: String) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val now = System.currentTimeMillis()
        val next = com.visorcraft.ghostgalleon.library.SessionMath.stampLastPlayed(
            com.visorcraft.ghostgalleon.library.PlayStats(
                live.lastLaunchedMs,
                live.playtimeMs,
            ),
            key,
            now,
        )
        app.updateSettings(live.copy(lastLaunchedMs = next.lastLaunchedMs))
        Toast.makeText(activity, "Marked as played", Toast.LENGTH_SHORT).show()
    }

    private fun clearGridPlayStats(key: String) {
        val app = activity.application as GhostGalleonApp
        val live = app.settings
        val stats = com.visorcraft.ghostgalleon.library.PlayStats(
            lastLaunchedMs = live.lastLaunchedMs,
            totalPlaytimeMs = live.playtimeMs,
        )
        if (!com.visorcraft.ghostgalleon.library.SessionMath.hasStats(stats, key)) {
            Toast.makeText(activity, "No play stats", Toast.LENGTH_SHORT).show()
            return
        }
        val label = gridEntryLabel(key)
        android.app.AlertDialog.Builder(activity)
            .setTitle("Clear play stats")
            .setMessage("Remove last played and playtime for $label?")
            .setPositiveButton("Clear") { _, _ ->
                val next = com.visorcraft.ghostgalleon.library.SessionMath.clearStats(
                    com.visorcraft.ghostgalleon.library.PlayStats(
                        live.lastLaunchedMs,
                        live.playtimeMs,
                    ),
                    key,
                )
                app.updateSettings(
                    live.copy(
                        lastLaunchedMs = next.lastLaunchedMs,
                        playtimeMs = next.totalPlaytimeMs,
                    ),
                )
                Toast.makeText(activity, "Cleared stats", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun gridRelatedOptions(key: String): List<com.visorcraft.ghostgalleon.library.GameDetails.RelatedOption> {
        val rom = SlotKey.romId(key)?.let { id -> roms.firstOrNull { it.id == id } }
            ?: return emptyList()
        val chrome = settings.browseChrome
        return com.visorcraft.ghostgalleon.library.GameDetails.relatedOptions(
            platformId = rom.platformId,
            genre = rom.genre,
            developer = rom.developer,
            year = rom.year,
            allowPlatform = chrome.platformChips,
            allowGenre = chrome.genreChips,
            allowDeveloper = chrome.developerChips,
            allowYear = chrome.yearChips,
        )
    }

    /**
     * Jump to Game Mode with a related meta filter (platform/genre/…). Used
     * from Grid long-press / Details so Browse related works in both decks.
     */
    private fun applyGridRelated(
        option: com.visorcraft.ghostgalleon.library.GameDetails.RelatedOption,
    ) {
        val next = settings.browseChrome.sanitize(
            com.visorcraft.ghostgalleon.library.GameDetails.toBrowseQuery(
                option,
                sort = state.libraryBrowse.sort,
            ),
        )
        state.setLibraryBrowse(next, force = true)
        state.setMode(com.visorcraft.ghostgalleon.state.UIMode.GAME)
        Toast.makeText(activity, option.label, Toast.LENGTH_SHORT).show()
    }

    private fun showGridBrowseRelated(key: String) {
        val options = gridRelatedOptions(key)
        if (options.isEmpty()) return
        if (options.size == 1) {
            applyGridRelated(options[0])
            return
        }
        val labels = options.map { it.label }.toTypedArray()
        android.app.AlertDialog.Builder(activity)
            .setTitle("Browse related")
            .setItems(labels) { _, which ->
                if (which in options.indices) applyGridRelated(options[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGridDetails(key: String) {
        val rom = SlotKey.romId(key)?.let { id -> roms.firstOrNull { it.id == id } }
        val title = when {
            rom != null -> rom.name
            else -> settings.customNames[key]
                ?: visibleByPkg[key]?.label
                ?: key
        }
        val body = com.visorcraft.ghostgalleon.library.GameDetails.body(
            com.visorcraft.ghostgalleon.library.GameDetails.Input(
                title = title,
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
                collections = com.visorcraft.ghostgalleon.library.GameDetails
                    .collectionsContaining(settings.collections, key),
                nowMs = System.currentTimeMillis(),
            ),
        )
        val related = if (rom != null) gridRelatedOptions(key) else emptyList()
        val builder = android.app.AlertDialog.Builder(activity)
            .setTitle("Details")
            .setMessage(body)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy title") { _, _ ->
                copyGridTitleToClipboard(title)
            }
        when {
            rom == null && !SlotKey.isRom(key) && !SlotKey.isFolder(key) ->
                builder.setNegativeButton("App info") { _, _ -> openAppInfo(key) }
            related.isNotEmpty() ->
                builder.setNegativeButton("Related…") { _, _ ->
                    showGridBrowseRelated(key)
                }
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

    // Rename dialog: dark modal with an EditText prefilled with the current
    // display name (custom name when set, else the app label). Saving an
    // EMPTY field removes the override (same as "Reset name"); Cancel keeps
    // everything as-is. The settings update hot-reloads every surface.
    private fun showRenameDialog(packageName: String) {
        val current = settings.customNames[packageName]
            ?: visibleByPkg[packageName]?.label
            ?: packageName
        val input = android.widget.EditText(activity).apply {
            setText(current)
            setSelectAllOnFocus(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0x66FFFFFF)
            hint = "Display name (empty = reset)"
            setSingleLine()
        }
        val container = FrameLayout(activity).apply {
            val margin = (20 * resources.displayMetrics.density).toInt()
            setPadding(margin, (12 * resources.displayMetrics.density).toInt(), margin, 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("Rename ${visibleByPkg[packageName]?.label ?: packageName}")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                val app = activity.application as GhostGalleonApp
                val names = app.settings.customNames
                app.updateSettings(app.settings.copy(
                    customNames = if (name.isEmpty() || name == current) {
                        names - packageName
                    } else {
                        names + (packageName to name)
                    }))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeSlotMenu() {
        DeckOverlays.detach(rootView, slotMenu?.view)
        slotMenu = null
    }

    // --- Move mode ---

    private fun startMove(slot: Int) {
        moveIndex = slot
        // The working copy covers the PADDED slot range so the lifted tile
        // can swap into a padded blank; dropping persists the extension.
        moveWorking = MutableList(slotCount) { settings.gridSlots.getOrNull(it) }
        dockBar?.showMoveHint(true)
        rebindVisibleCells()
    }

    private fun handleMoveAction(action: Action, from: Int): Boolean {
        when (action) {
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
            Action.PAGE_PREV, Action.PAGE_NEXT -> {
                val to = nav.move(from, action)
                if (to != from) {
                    // 3DS behavior: the lifted tile swaps contents with
                    // whatever is in the target slot.
                    val working = moveWorking ?: return true
                    val tmp = working[from]
                    working[from] = working[to]
                    working[to] = tmp
                    moveIndex = to
                    ensureSelectionVisible()
                    rebindVisibleCells()
                    updatePageDots()
                }
            }
            Action.CONFIRM -> dropMove()
            Action.BACK -> cancelMove()
            else -> {}
        }
        return true // move mode swallows every action
    }

    // Drops the lifted tile: optionally after one last swap to a tapped
    // slot, then persists the working copy (settings -> full hot reload).
    private fun dropMove(tapSlot: Int? = null) {
        val from = moveIndex ?: return
        val working = moveWorking ?: return
        val finalSlot = if (tapSlot != null && tapSlot in working.indices) {
            if (tapSlot != from) {
                val tmp = working[from]
                working[from] = working[tapSlot]
                working[tapSlot] = tmp
            }
            tapSlot
        } else {
            from
        }
        val slots = working.toList()
        endMoveVisuals()
        updateGridSlots(slots, finalSlot)
    }

    private fun cancelMove() {
        endMoveVisuals()
        // Visible cells still show the discarded working copy: repopulate.
        rebindVisibleCells(repopulate = true)
        updatePageDots()
    }

    private fun endMoveVisuals() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        moveIndex = null
        moveWorking = null
        dockBar?.showMoveHint(false)
    }

    // --- App picker ---

    private fun openPicker(slot: Int) {
        val appPicker = AppPicker(
            activity,
            settings.accentColor,
            library.visible(settings),
            roms,
            iconLoader,
            onPick = { key ->
                closePicker()
                updateGridSlots(GridSlots.fill(settings.gridSlots, slot, key), slot)
            },
            onHide = { packageName ->
                closePicker()
                DeckOverlays.hideApp(activity, packageName)
            },
            onClose = { closePicker() },
        )
        picker = appPicker
        DeckOverlays.attach(rootView, appPicker.view)
    }

    private fun closePicker() {
        DeckOverlays.detach(rootView, picker?.view)
        DeckOverlays.hideIme(activity, rootView)
        picker = null
    }

    override fun handleAction(action: Action): Boolean {
        slotMenu?.let { return it.handleAction(action) }
        folderPanel?.let { return it.handleAction(action) }
        picker?.let { return it.handleAction(action) }
        moveIndex?.let { return handleMoveAction(action, it) }
        dockMove.index?.let { return handleDockMoveAction(action, it) }
        state.dockSlot?.let { return handleDockAction(action, it) }
        return when (action) {
            Action.CONFIRM -> {
                val slot = selectedIndex()
                val key = settings.gridSlots.getOrNull(slot)
                when {
                    key == null -> openPicker(slot)
                    SlotKey.isFolder(key) -> openFolder(key)
                    else -> launchSlotKey(activity, state, roms, key)
                }
                true
            }
            Action.NAV_UP, Action.NAV_DOWN, Action.NAV_LEFT, Action.NAV_RIGHT,
            Action.PAGE_PREV, Action.PAGE_NEXT -> {
                // NAV DOWN on the last row leaves the grid and focuses the
                // dock (same column, clamped into the dock row).
                if (action == Action.NAV_DOWN && dockNav.isLastRow(selectedIndex())) {
                    state.focusDock(dockNav.enterFromGrid(selectedIndex()))
                    return true
                }
                val target = nav.move(selectedIndex(), action)
                state.selectSlot(target, settings.gridSlots.getOrNull(target))
                true
            }
            else -> false
        }
    }

    private inner class GridAdapter(
        private val context: Context,
        cellWidth: Int,
        iconSize: Int,
        cellHeight: Int,
        private val cellPadding: Int,
        // Horizontal page panels bind a fixed slice of the slot list; the
        // vertical grid uses the defaults (whole list from slot 0).
        private val slotOffset: Int = 0,
        private val fixedCount: Int? = null,
    ) : BaseAdapter() {
        // Mutable: the layout listener restamps these when the grid's
        // measured height changes (IME-race rebuild) without needing an
        // adapter notify.
        private var cellWidth = cellWidth
        private var iconSize = iconSize
        private var cellHeight = cellHeight

        fun updateMetrics(cellWidth: Int, iconSize: Int, cellHeight: Int) {
            this.cellWidth = cellWidth
            this.iconSize = iconSize
            this.cellHeight = cellHeight
        }

        override fun getCount() = fixedCount ?: slotCount
        // Padded tail cells are past the end of the stored list: null.
        override fun getItem(position: Int) = slotPackages().getOrNull(slotOffset + position)
        override fun getItemId(position: Int) = (slotOffset + position).toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val frame = FrameLayout(context)
            populateFrame(frame, slotOffset + position)
            frame.layoutParams = AbsListView.LayoutParams(cellWidth, cellHeight)
            return frame
        }

        // (Re)builds a cell for the slot at [position]: a filled tile
        // (app icon or ROM platform tile + optional label + dock badge) or
        // a blank "+" tile, then the current selection visuals. Called by
        // getView and by the in-place rebind during move mode, where
        // contents swap.
        fun populateFrame(frame: FrameLayout, position: Int) {
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()

            frame.removeAllViews()
            val key = slotPackages().getOrNull(position)
            val romEntry = key?.takeIf(SlotKey::isRom)?.let { romByKey[it] }
            val isFolder = SlotKey.isFolder(key)
            val appEntry = key
                ?.takeUnless { SlotKey.isRom(it) || SlotKey.isFolder(it) }
                ?.let { visibleByPkg[it] }
            when {
                romEntry != null -> {
                    frame.addView(buildRomCell(romEntry), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                isFolder && key != null -> {
                    frame.addView(buildFolderCell(key), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                appEntry != null -> {
                    frame.addView(buildFilledCell(appEntry), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                SlotKey.isRom(key) -> {
                    // Unresolvable ROM slot (deleted ROM, card out, library
                    // changed): the platform tile at 40% alpha with a dim
                    // "Missing" label. The slot stays filled — long-press
                    // still offers Move/Remove and launch toasts.
                    frame.addView(buildMissingCell(key!!), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
                else -> {
                    // Blank slot (or a value that no longer resolves): empty
                    // card with a large dim "+".
                    frame.addView(TextView(context).apply {
                        text = "+"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                        setTextColor(0x44FFFFFF)
                        gravity = Gravity.CENTER
                    }, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }
            // Frame wrapper so pinned tiles can carry an accent badge dot at
            // their top-right corner; ROM and app keys pin identically.
            val filled = key != null && (romEntry != null || appEntry != null || isFolder)
            if (filled && key in settings.dockSlots) {
                val badge = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(settings.accentColor)
                    }
                }
                // When a favorite star is also shown, park the dock dot at
                // top-start so it does not cover the ★ at top-end.
                val dockGravity = if (key in settings.favorites) {
                    Gravity.TOP or Gravity.START
                } else {
                    Gravity.TOP or Gravity.END
                }
                frame.addView(badge, FrameLayout.LayoutParams(
                    dp(10), dp(10), dockGravity).apply {
                    topMargin = dp(4)
                    if (dockGravity and Gravity.END == Gravity.END) marginEnd = dp(4)
                    else marginStart = dp(4)
                })
            }
            // Favorite badge: small ★ at top-end on filled favorited slots.
            if (filled && key in settings.favorites) {
                frame.addView(TextView(context).apply {
                    text = "★"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(0xFFFFD54F.toInt())
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                }, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END,
                ).apply {
                    topMargin = dp(2)
                    marginEnd = dp(4)
                })
            }
            applySelectionVisuals(frame, position == selectedIndex())
        }

        private fun buildFolderCell(key: String): View {
            val folderId = SlotKey.folderId(key)
            val spec = folderId?.let { settings.folders[it] }
            val name = spec?.name ?: folderId ?: "Folder"
            val count = spec?.members?.size ?: 0
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            val glyph = TextView(context).apply {
                text = "📁"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(0xFF2A2A38.toInt())
                    cornerRadius = 12 * context.resources.displayMetrics.density
                }
            }
            cell.addView(glyph, LinearLayout.LayoutParams(iconSize, iconSize))
            if (settings.showLabels) {
                cell.addView(TextView(context).apply {
                    text = name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            cell.addView(TextView(context).apply {
                text = if (count == 1) "1 item" else "$count items"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(0x88FFFFFF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            return cell
        }

        // ROM tile: cached artwork over the platform placeholder (async
        // fill; placeholder shows until/unless art arrives) plus the ROM
        // name label under the same showLabels setting as apps.
        private fun buildRomCell(entry: RomEntry): View {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            cell.addView(
                ArtTile.view(
                    context,
                    (activity.application as GhostGalleonApp).artCache,
                    entry,
                    targetPx = iconSize,
                    artOverrides = settings.artOverrides,
                ),
                LinearLayout.LayoutParams(iconSize, iconSize))
            if (settings.showLabels) {
                val label = TextView(context).apply {
                    text = entry.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                cell.addView(label, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            return cell
        }

        // "Missing" tile for a rom:<id> slot whose entry no longer resolves
        // (deleted ROM, card out, library changed): the platform placeholder
        // at 40% alpha with a dim "Missing" label, so it reads as a broken
        // reference instead of a blank "+" while staying a normal filled
        // slot (long-press menu, launch toast, selection ring).
        private fun buildMissingCell(key: String): View {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            val tile = PlatformTile.view(
                context, SlotKey.platformIdOf(key) ?: "rom")
            tile.alpha = 0.4f
            cell.addView(tile, LinearLayout.LayoutParams(iconSize, iconSize))
            cell.addView(TextView(context).apply {
                text = "Missing"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(0x66FFFFFF)
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
            return cell
        }

        private fun buildFilledCell(entry: AppEntry): View {
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
            }
            val icon = ImageView(context)
            CustomIcon.bind(
                icon, iconLoader,
                (activity.application as GhostGalleonApp).artCache,
                settings, entry.packageName, iconSize)
            cell.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
            // Optional app-name label ("Show app names" setting).
            if (settings.showLabels) {
                val label = TextView(context).apply {
                    text = entry.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                cell.addView(label, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            return cell
        }
    }

    private companion object {
        // Serializes wallpaper decodes; a new decode simply queues behind
        // a stale one (which is then dropped by the main-thread guard).
        val WALLPAPER_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}
