package tech.bananajuice.adzuki.shared.mvi

import tech.bananajuice.adzuki.shared.automerge.Directive

sealed class DiffChange {
    abstract val selected: Boolean
    abstract fun toggle(): DiffChange

    data class Added(val directive: Directive, override val selected: Boolean = true) : DiffChange() {
        override fun toggle(): DiffChange = copy(selected = !selected)
    }

    data class Removed(val directive: Directive, override val selected: Boolean = true) : DiffChange() {
        override fun toggle(): DiffChange = copy(selected = !selected)
    }

    data class Modified(val oldDirective: Directive, val newDirective: Directive, override val selected: Boolean = true) : DiffChange() {
        override fun toggle(): DiffChange = copy(selected = !selected)
    }
}
