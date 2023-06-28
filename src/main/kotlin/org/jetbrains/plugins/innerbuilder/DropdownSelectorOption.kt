package org.jetbrains.plugins.innerbuilder

class DropdownSelectorOption(
    override val option: InnerBuilderOption,
    override val caption: String,
    override val toolTip: String,
    val values: List<DropdownSelectorOptionValue?>
) : SelectorOption
