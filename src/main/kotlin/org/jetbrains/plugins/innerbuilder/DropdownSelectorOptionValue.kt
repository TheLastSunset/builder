package org.jetbrains.plugins.innerbuilder

class DropdownSelectorOptionValue private constructor(builder: Builder) {
    var option: InnerBuilderOption?
    var caption: String?

    init {
        option = builder.option
        caption = builder.caption
    }

    class Builder {
        var option: InnerBuilderOption? = null
        var caption: String? = null
        fun withOption(`val`: InnerBuilderOption?): Builder {
            option = `val`
            return this
        }

        fun withCaption(`val`: String?): Builder {
            caption = `val`
            return this
        }

        fun build(): DropdownSelectorOptionValue {
            return DropdownSelectorOptionValue(this)
        }
    }

    companion object {
        fun newBuilder(): Builder {
            return Builder()
        }
    }
}
