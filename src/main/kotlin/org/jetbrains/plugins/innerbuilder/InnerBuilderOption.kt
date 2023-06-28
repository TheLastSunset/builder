package org.jetbrains.plugins.innerbuilder

import java.util.*

enum class InnerBuilderOption(property: String, val isBooleanProperty: Boolean = true) {
    FINAL_SETTERS("finalSetters"),
    NEW_BUILDER_METHOD("newBuilderMethod"),
    STATIC_BUILDER_DROPDOWN("staticBuilderDropdown", false),
    STATIC_BUILDER_NEW_BUILDER_NAME("staticBuilderNewBuilderName", false),
    STATIC_BUILDER_BUILDER_NAME("staticBuilderBuilderName", false),
    STATIC_BUILDER_NEW_CLASS_NAME("staticBuilderNewClassName", false),
    STATIC_BUILDER_NEW_CLASS_NAME_BUILDER("staticBuilderNewClassNameBuilder", false),
    BUILDER_METHOD_LOCATION_DROPDOWN("builderMethodDropdownLocation", false),
    BUILDER_METHOD_IN_PARENT_CLASS("builderMethodInParentClass", false),
    BUILDER_METHOD_IN_BUILDER("builderMethodInBuilder", false),
    COPY_CONSTRUCTOR("copyConstructor"),
    WITH_NOTATION("withNotation"),
    SET_NOTATION("setNotation"),
    JSR305_ANNOTATIONS("useJSR305Annotations"),
    PMD_AVOID_FIELD_NAME_MATCHING_METHOD_NAME_ANNOTATION("suppressAvoidFieldNameMatchingMethodName"),
    WITH_JAVADOC("withJavadoc"),
    FIELD_NAMES("fieldNames");

    val property: String

    init {
        this.property = String.format("GenerateInnerBuilder.%s", property)
    }

    companion object {
        fun findValue(value: String?): Optional<InnerBuilderOption> {
            return Arrays.stream(values())
                .filter { it: InnerBuilderOption -> it.property == value }
                .findFirst()
        }
    }
}
