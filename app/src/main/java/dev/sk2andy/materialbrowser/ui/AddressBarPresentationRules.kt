package dev.sk2andy.materialbrowser.ui

internal enum class AddressBarPresentation {
    Docked,
    Compact,
    Expanded,
    Overview,
    CommandFeedback,
}

internal object AddressBarPresentationRules {
    fun resolve(
        docked: Boolean,
        compact: Boolean,
        editing: Boolean,
        showingCommandFeedback: Boolean,
        showingTabOverview: Boolean = false,
    ): AddressBarPresentation = when {
        showingTabOverview -> AddressBarPresentation.Overview
        showingCommandFeedback -> AddressBarPresentation.CommandFeedback
        editing -> AddressBarPresentation.Expanded
        docked -> AddressBarPresentation.Docked
        compact -> AddressBarPresentation.Compact
        else -> AddressBarPresentation.Expanded
    }
}
internal object AddressBarControlRules {
    fun tabCountLabel(tabCount: Int): String =
        if (tabCount > 99) "∞" else tabCount.coerceAtLeast(0).toString()

    /**
     * @param startsFullWidth the bar was hidden before editing (the fast action button replaces
     * it), so there are no resting actions to fade out and the editor is full width at once.
     */
    fun editorUsesFullWidth(
        editing: Boolean,
        addressFieldFocused: Boolean,
        imeVisible: Boolean,
        startsFullWidth: Boolean = false,
    ): Boolean = editing && (startsFullWidth || addressFieldFocused && imeVisible)
}
