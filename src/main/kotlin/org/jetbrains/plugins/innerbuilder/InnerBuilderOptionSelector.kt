package org.jetbrains.plugins.innerbuilder

import com.intellij.codeInsight.generation.PsiFieldMember
import com.intellij.ide.util.MemberChooser
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.NonFocusableCheckBox
import java.awt.event.ItemEvent
import java.util.function.Consumer
import javax.swing.JCheckBox
import javax.swing.JComponent

object InnerBuilderOptionSelector {
    private val RENDERER = DropdownListCellRenderer()
    private val OPTIONS = createGeneratorOptions()
    private fun createGeneratorOptions(): List<SelectorOption> {
        val options: MutableList<SelectorOption> = ArrayList()
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.FINAL_SETTERS,
                "Generate builder methods for final fields",
                'f'
            )
        )
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.NEW_BUILDER_METHOD,
                "Generate static builder method",
                'n'
            )
        )
        options.add(
            DropdownSelectorOption(
                InnerBuilderOption.STATIC_BUILDER_DROPDOWN,
                "Static builder naming",
                "Select what the static builder method should look like.",
                listOf(
                    DropdownSelectorOptionValue.newBuilder()
                        .withCaption("newBuilder()")
                        .withOption(InnerBuilderOption.STATIC_BUILDER_NEW_BUILDER_NAME)
                        .build(),
                    DropdownSelectorOptionValue.newBuilder()
                        .withCaption("builder()")
                        .withOption(InnerBuilderOption.STATIC_BUILDER_BUILDER_NAME)
                        .build(),
                    DropdownSelectorOptionValue.newBuilder()
                        .withCaption("new[ClassName]()")
                        .withOption(InnerBuilderOption.STATIC_BUILDER_NEW_CLASS_NAME)
                        .build(),
                    DropdownSelectorOptionValue.newBuilder()
                        .withCaption("new[ClassName]Builder()")
                        .withOption(InnerBuilderOption.STATIC_BUILDER_NEW_CLASS_NAME_BUILDER)
                        .build()
                )
            )
        )
        options.add(
            DropdownSelectorOption(
                InnerBuilderOption.BUILDER_METHOD_LOCATION_DROPDOWN,
                "Builder method location",
                "Select where the builder method should be located.",
                listOf(
                    DropdownSelectorOptionValue.newBuilder()
                        .withCaption("Inside parent class")
                        .withOption(InnerBuilderOption.BUILDER_METHOD_IN_PARENT_CLASS)
                        .build(),
                    DropdownSelectorOptionValue.newBuilder()
                        .withCaption("Inside generated Builder class")
                        .withOption(InnerBuilderOption.BUILDER_METHOD_IN_BUILDER)
                        .build()
                )
            )
        )
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.COPY_CONSTRUCTOR,
                "Generate builder copy constructor",
                'o'
            )
        )
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.WITH_NOTATION,
                "Use 'with...' notation",
                'w', "Generate builder methods that start with 'with', for example: "
                        + "builder.withName(String name)"
            )
        )
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.SET_NOTATION,
                "Use 'set...' notation",
                't', "Generate builder methods that start with 'set', for example: "
                        + "builder.setName(String name)"
            )
        )
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.JSR305_ANNOTATIONS,
                "Add JSR-305 @Nonnull annotation",
                'j', "Add @Nonnull annotations to generated methods and parameters, for example: "
                        + "@Nonnull public Builder withName(@Nonnull String name) { ... }"
            )
        )
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.PMD_AVOID_FIELD_NAME_MATCHING_METHOD_NAME_ANNOTATION,
                "Add @SuppressWarnings(\"PMD.AvoidFieldNameMatchingMethodName\") annotation",
                'p',
                "Add @SuppressWarnings(\"PMD.AvoidFieldNameMatchingMethodName\") annotation to the generated Builder class"
            )
        )
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.WITH_JAVADOC,
                "Add Javadoc",
                'c',
                "Add Javadoc to generated builder class and methods"
            )
        )
        options.add(
            CheckboxSelectorOption(
                InnerBuilderOption.FIELD_NAMES,
                "Use field names in setter",
                's',
                "Generate builder methods that has the same parameter names in setter methods as field names, for example: builder.withName(String fieldName)"
            )
        )
        return options
    }

    fun selectFieldsAndOptions(
        members: List<PsiFieldMember?>?,
        project: Project?
    ): List<PsiFieldMember?>? {
        if (members.isNullOrEmpty()) {
            return null
        }
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return members
        }
        val optionCheckBoxes = buildOptions()
        val memberArray = members.toTypedArray<PsiFieldMember?>()
        val chooser = MemberChooser(
            memberArray,
            false,  // allowEmptySelection
            true,  // allowMultiSelection
            project!!, null, optionCheckBoxes
        )
        chooser.title = "Select Fields and Options for the Builder"
        chooser.selectElements(memberArray)
        return if (chooser.showAndGet()) {
            chooser.selectedElements
        } else null
    }

    private fun buildOptions(): Array<JComponent?> {
        val propertiesComponent = PropertiesComponent.getInstance()
        val optionCount = OPTIONS.size
        val checkBoxesArray = arrayOfNulls<JComponent>(optionCount)
        for (i in 0 until optionCount) {
            checkBoxesArray[i] = buildOptions(propertiesComponent, OPTIONS[i])
        }
        return checkBoxesArray
    }

    private fun buildOptions(
        propertiesComponent: PropertiesComponent,
        selectorOption: SelectorOption
    ): JComponent {
        return if (selectorOption is CheckboxSelectorOption) {
            buildCheckbox(propertiesComponent, selectorOption)
        } else buildDropdown(
            propertiesComponent,
            selectorOption as DropdownSelectorOption
        )
    }

    private fun buildCheckbox(
        propertiesComponent: PropertiesComponent,
        selectorOption: CheckboxSelectorOption
    ): JComponent {
        val optionCheckBox: JCheckBox = NonFocusableCheckBox(selectorOption.caption)
        optionCheckBox.setMnemonic(selectorOption.mnemonic)
        optionCheckBox.toolTipText = selectorOption.toolTip
        val optionProperty = selectorOption.option.property
        optionCheckBox.isSelected = propertiesComponent.isTrueValue(optionProperty)
        optionCheckBox.addItemListener { _: ItemEvent? ->
            propertiesComponent.setValue(
                optionProperty, optionCheckBox.isSelected.toString()
            )
        }
        return optionCheckBox
    }

    private fun buildDropdown(
        propertiesComponent: PropertiesComponent,
        selectorOption: DropdownSelectorOption
    ): JComponent {
        val comboBox: ComboBox<DropdownSelectorOptionValue> = ComboBox<DropdownSelectorOptionValue>()
        comboBox.isEditable = false
        comboBox.renderer = RENDERER
        selectorOption.values.forEach(Consumer { item: DropdownSelectorOptionValue? -> comboBox.addItem(item) })
        comboBox.selectedItem = setSelectedComboBoxItem(propertiesComponent, selectorOption)
        comboBox.addItemListener { event: ItemEvent ->
            setPropertiesComponentValue(
                propertiesComponent,
                selectorOption,
                event
            )
        }
        val labeledComponent = LabeledComponent.create(comboBox, selectorOption.caption)
        labeledComponent.toolTipText = selectorOption.toolTip
        return labeledComponent
    }

    private fun setPropertiesComponentValue(
        propertiesComponent: PropertiesComponent,
        selectorOption: DropdownSelectorOption,
        itemEvent: ItemEvent
    ) {
        val value = itemEvent.item as DropdownSelectorOptionValue
        propertiesComponent.setValue(selectorOption.option.property, value.option?.property)
    }

    private fun setSelectedComboBoxItem(
        propertiesComponent: PropertiesComponent,
        selectorOption: DropdownSelectorOption
    ): DropdownSelectorOptionValue? {
        val selectedValue = propertiesComponent.getValue(selectorOption.option.property)
        return selectorOption.values
            .stream()
            .filter { it: DropdownSelectorOptionValue? -> it?.option?.property == selectedValue }
            .findFirst()
            .orElse(selectorOption.values[0])
    }
}
