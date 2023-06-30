package org.jetbrains.plugins.innerbuilder

class CheckboxSelectorOption : SelectorOption {
    override val option: InnerBuilderOption
    override val caption: String
    val mnemonic: Char
    override var toolTip: String? = null
        private set

    constructor(option: InnerBuilderOption, caption: String, mnemonic: Char) {
        this.option = option
        this.caption = caption
        this.mnemonic = mnemonic
    }

    constructor(option: InnerBuilderOption, caption: String, mnemonic: Char, toolTip: String?) {
        this.option = option
        this.caption = caption
        this.mnemonic = mnemonic
        this.toolTip = toolTip
    }
}
