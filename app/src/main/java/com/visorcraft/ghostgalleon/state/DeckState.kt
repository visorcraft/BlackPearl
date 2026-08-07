package com.visorcraft.ghostgalleon.state

import com.visorcraft.ghostgalleon.library.LibraryBrowse

class DeckState {

    fun interface DeckStateListener {
        fun onDeckStateChanged(state: DeckState)
    }

    // What kind of mutation produced the latest notification. Listeners use
    // this to pick a granular re-render (SELECTION) over a full rebuild.
    enum class Change { SELECTION, MODE, DISPLAY, SETTINGS }

    var mode: UIMode = UIMode.GRID
        private set

    var primaryDisplayId: Int = 0
        private set

    var selectedKey: String? = null
        private set

    // Curated-grid selection: the focused slot index. Kept alongside
    // selectedKey (the slot's package, null for blanks) so the companion
    // hero panel and the game carousel keep working unchanged.
    var selectedSlot: Int = 0
        private set

    // Dock focus: null while the grid/carousel holds focus; otherwise the
    // focused dock slot index. selectedSlot/selectedKey deliberately stay
    // put while the dock is focused, so NAV UP restores the exact
    // grid/carousel selection and the hero panel keeps showing it.
    var dockSlot: Int? = null
        private set

    // Game Mode library browse: platform filter, text query, All/Recent/Fav.
    var libraryBrowse: LibraryBrowse.BrowseQuery = LibraryBrowse.BrowseQuery()
        private set

    // Multi-select set of slot keys in Game Mode (empty = select mode off).
    var multiSelectKeys: Set<String> = emptySet()
        private set

    var multiSelectEnabled: Boolean = false
        private set

    var lastChange: Change = Change.SETTINGS
        private set

    private val listeners = mutableSetOf<DeckStateListener>()

    fun setMode(m: UIMode) {
        if (mode == m) return
        mode = m
        lastChange = Change.MODE
        notifyListeners()
    }

    fun toggleMode() = setMode(if (mode == UIMode.GRID) UIMode.GAME else UIMode.GRID)

    fun setPrimaryDisplayId(id: Int) {
        if (primaryDisplayId == id) return
        primaryDisplayId = id
        lastChange = Change.DISPLAY
        notifyListeners()
    }

    /**
     * Dual-only swap: flips between [primaryId] and [companionId] from the
     * current topology. No-op if ids are invalid or equal.
     */
    fun swapDisplays(primaryId: Int, companionId: Int) {
        if (primaryId == companionId) return
        val next = if (primaryDisplayId == primaryId) companionId else primaryId
        setPrimaryDisplayId(next)
    }

    /**
     * Test helper: swap with an alternate id (does not assume 0/1 topology).
     * Production UI uses GhostGalleonApp.swapInteractiveDisplay().
     */
    fun swapDisplaysWith(otherDisplayId: Int) {
        if (otherDisplayId == primaryDisplayId) return
        setPrimaryDisplayId(otherDisplayId)
    }

    /** Align primary to topology when current id is not in [validIds]. */
    fun ensurePrimaryIn(validIds: Collection<Int>, preferred: Int) {
        if (validIds.isEmpty()) return
        if (primaryDisplayId in validIds) return
        val next = if (preferred in validIds) preferred else validIds.first()
        setPrimaryDisplayId(next)
    }

    fun select(key: String?) {
        if (selectedKey == key && dockSlot == null) return
        selectedKey = key
        dockSlot = null
        lastChange = Change.SELECTION
        notifyListeners()
    }

    // Grid-slot selection: moving between two blank slots changes only the
    // slot index, so this notifies when either half changed. Selecting a
    // grid slot also leaves the dock (it is how NAV UP returns).
    fun selectSlot(index: Int, key: String?) {
        if (selectedSlot == index && selectedKey == key && dockSlot == null) return
        selectedSlot = index
        selectedKey = key
        dockSlot = null
        lastChange = Change.SELECTION
        notifyListeners()
    }

    // Moves focus into the dock at [index] (NAV DOWN from the grid's last
    // row or the carousel). The grid/carousel selection is untouched.
    fun focusDock(index: Int) {
        if (dockSlot == index) return
        dockSlot = index
        lastChange = Change.SELECTION
        notifyListeners()
    }

    fun addListener(l: DeckStateListener) {
        listeners.add(l)
    }

    fun removeListener(l: DeckStateListener) {
        listeners.remove(l)
    }

    fun setLibraryBrowse(query: LibraryBrowse.BrowseQuery) {
        if (libraryBrowse == query) return
        libraryBrowse = query
        lastChange = Change.SETTINGS
        notifyListeners()
    }

    fun setMultiSelectEnabled(enabled: Boolean) {
        if (multiSelectEnabled == enabled && (!enabled || multiSelectKeys.isEmpty())) {
            if (!enabled) {
                multiSelectEnabled = false
                multiSelectKeys = emptySet()
            }
            return
        }
        multiSelectEnabled = enabled
        if (!enabled) multiSelectKeys = emptySet()
        lastChange = Change.SETTINGS
        notifyListeners()
    }

    fun toggleMultiSelectKey(key: String) {
        multiSelectEnabled = true
        multiSelectKeys = if (key in multiSelectKeys) multiSelectKeys - key else multiSelectKeys + key
        lastChange = Change.SELECTION
        notifyListeners()
    }

    fun clearMultiSelect() {
        if (!multiSelectEnabled && multiSelectKeys.isEmpty()) return
        multiSelectEnabled = false
        multiSelectKeys = emptySet()
        lastChange = Change.SETTINGS
        notifyListeners()
    }

    fun setMultiSelectKeys(keys: Set<String>) {
        multiSelectEnabled = keys.isNotEmpty()
        multiSelectKeys = keys
        lastChange = Change.SETTINGS
        notifyListeners()
    }

    // Explicit re-notification with no field mutation (settings changed);
    // always a full rebuild.
    fun notifyChanged() {
        lastChange = Change.SETTINGS
        notifyListeners()
    }

    private fun notifyListeners() = listeners.forEach { it.onDeckStateChanged(this) }
}
