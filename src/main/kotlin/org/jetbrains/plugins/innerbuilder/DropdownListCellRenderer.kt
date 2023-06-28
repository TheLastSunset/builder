package org.jetbrains.plugins.innerbuilder

import com.intellij.ui.SimpleListCellRenderer
import javax.swing.JList

class DropdownListCellRenderer : SimpleListCellRenderer<DropdownSelectorOptionValue>() {
    override fun customize(
        list: JList<out DropdownSelectorOptionValue>,
        value: DropdownSelectorOptionValue,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        text = value.caption
    }
}
