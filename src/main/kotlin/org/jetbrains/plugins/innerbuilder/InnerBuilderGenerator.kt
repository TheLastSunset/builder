package org.jetbrains.plugins.innerbuilder

import com.intellij.codeInsight.generation.PsiFieldMember
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.NonNls
import java.util.*

class InnerBuilderGenerator private constructor(
    private val project: Project, private val file: PsiFile, private val editor: Editor,
    private val selectedFields: List<PsiFieldMember?>
) : Runnable {
    private val psiElementFactory: PsiElementFactory = JavaPsiFacade.getInstance(project).elementFactory

    override fun run() {
        val targetClass = InnerBuilderUtils.getStaticOrTopLevelClass(file, editor) ?: return
        val options: Set<InnerBuilderOption> = currentOptions()
        val builderClass = findOrCreateBuilderClass(targetClass)
        val builderType = psiElementFactory.createTypeFromText(BUILDER_CLASS_NAME, null)
        val constructor = generateConstructor(targetClass, builderType)
        addMethod(targetClass, null, constructor, true)
        val finalFields: MutableCollection<PsiFieldMember?> = ArrayList()
        val nonFinalFields: MutableCollection<PsiFieldMember?> = ArrayList()
        var lastAddedField: PsiElement? = null
        for (fieldMember in selectedFields) {
            lastAddedField = findOrCreateField(builderClass, fieldMember, lastAddedField)
            if (fieldMember!!.element.hasModifierProperty(PsiModifier.FINAL)
                && !options.contains(InnerBuilderOption.FINAL_SETTERS)
            ) {
                finalFields.add(fieldMember)
                PsiUtil.setModifierProperty((lastAddedField as PsiField?)!!, PsiModifier.FINAL, true)
            } else {
                nonFinalFields.add(fieldMember)
            }
        }
        if (options.contains(InnerBuilderOption.NEW_BUILDER_METHOD)) {
            val newBuilderMethod = generateNewBuilderMethod(builderType, targetClass, finalFields, options)
            val builderTarget =
                if (options.contains(InnerBuilderOption.BUILDER_METHOD_IN_PARENT_CLASS)) targetClass else builderClass
            addMethod(builderTarget, null, newBuilderMethod, false)
        }

        // builder constructor, accepting the final fields
        val builderConstructorMethod = generateBuilderConstructor(builderClass, finalFields, options)
        addMethod(builderClass, null, builderConstructorMethod, false)

        // builder copy constructor or static copy method
        if (options.contains(InnerBuilderOption.COPY_CONSTRUCTOR)) {
            if (options.contains(InnerBuilderOption.NEW_BUILDER_METHOD)) {
                val copyBuilderMethod = generateCopyBuilderMethod(
                    targetClass, builderType,
                    nonFinalFields, options
                )
                addMethod(targetClass, null, copyBuilderMethod, true)
            } else {
                val copyConstructorBuilderMethod = generateCopyConstructor(
                    targetClass, builderType,
                    selectedFields, options
                )
                addMethod(builderClass, null, copyConstructorBuilderMethod, true)
            }
        }

        // builder methods
        var lastAddedElement: PsiElement? = null
        for (member in nonFinalFields) {
            val setterMethod = generateBuilderSetter(builderType, member, options)
            lastAddedElement = addMethod(builderClass, lastAddedElement, setterMethod, false)
        }

        // builder.build() method
        val buildMethod = generateBuildMethod(targetClass, options)
        addMethod(builderClass, lastAddedElement, buildMethod, false)
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(file)
        CodeStyleManager.getInstance(project).reformat(builderClass)
    }

    private fun generateCopyBuilderMethod(
        targetClass: PsiClass, builderType: PsiType,
        fields: Collection<PsiFieldMember?>,
        options: Set<InnerBuilderOption>
    ): PsiMethod {
        val copyBuilderMethod =
            psiElementFactory.createMethod(getBuilderMethodName(targetClass, options), builderType)
        PsiUtil.setModifierProperty(copyBuilderMethod, PsiModifier.STATIC, true)
        PsiUtil.setModifierProperty(copyBuilderMethod, PsiModifier.PUBLIC, true)
        val targetClassType: PsiType = psiElementFactory.createType(targetClass)
        val parameter = psiElementFactory.createParameter("copy", targetClassType)
        val parameterModifierList = parameter.modifierList
        if (parameterModifierList != null && options.contains(InnerBuilderOption.JSR305_ANNOTATIONS)) {
            parameterModifierList.addAnnotation(JSR305_NONNULL)
        }
        copyBuilderMethod.parameterList.add(parameter)
        val copyBuilderBody = copyBuilderMethod.body
        if (copyBuilderBody != null) {
            val copyBuilderParameters = StringBuilder()
            for (fieldMember in selectedFields) {
                if (fieldMember!!.element.hasModifierProperty(PsiModifier.FINAL)
                    && !options.contains(InnerBuilderOption.FINAL_SETTERS)
                ) {
                    if (copyBuilderParameters.isNotEmpty()) {
                        copyBuilderParameters.append(", ")
                    }
                    copyBuilderParameters.append(String.format("copy.%s", fieldMember.element.name))
                }
            }
            if (options.contains(InnerBuilderOption.NEW_BUILDER_METHOD)) {
                val newBuilderStatement = psiElementFactory.createStatementFromText(
                    String.format(
                        "%s builder = new %s(%s);", builderType.presentableText,
                        builderType.presentableText, copyBuilderParameters
                    ),
                    copyBuilderMethod
                )
                copyBuilderBody.add(newBuilderStatement)
                addCopyBody(fields, copyBuilderMethod, "builder.")
                copyBuilderBody.add(psiElementFactory.createStatementFromText("return builder;", copyBuilderMethod))
            } else {
                val newBuilderStatement = psiElementFactory.createStatementFromText(
                    String.format(
                        "return new %s(%s);", builderType.presentableText,
                        copyBuilderParameters
                    ),
                    copyBuilderMethod
                )
                copyBuilderBody.add(newBuilderStatement)
            }
        }
        return copyBuilderMethod
    }

    private fun getBuilderMethodName(psiClass: PsiClass, options: Set<InnerBuilderOption>): String {
        if (options.contains(InnerBuilderOption.STATIC_BUILDER_NEW_BUILDER_NAME)) {
            return DEFAULT_BUILDER_METHOD_NAME
        }
        if (options.contains(InnerBuilderOption.STATIC_BUILDER_BUILDER_NAME)) {
            return BUILDER_METHOD_NAME
        }
        if (options.contains(InnerBuilderOption.STATIC_BUILDER_NEW_CLASS_NAME)) {
            return "new" + psiClass.name
        }
        return if (options.contains(InnerBuilderOption.STATIC_BUILDER_NEW_CLASS_NAME_BUILDER)) {
            "new" + psiClass.name + "Builder"
        } else DEFAULT_BUILDER_METHOD_NAME
    }

    private fun generateCopyConstructor(
        targetClass: PsiClass, builderType: PsiType,
        nonFinalFields: Collection<PsiFieldMember?>,
        options: Set<InnerBuilderOption>
    ): PsiMethod {
        val copyConstructor = psiElementFactory.createConstructor(builderType.presentableText)
        PsiUtil.setModifierProperty(copyConstructor, PsiModifier.PUBLIC, true)
        val targetClassType: PsiType = psiElementFactory.createType(targetClass)
        val constructorParameter = psiElementFactory.createParameter("copy", targetClassType)
        val parameterModifierList = constructorParameter.modifierList
        if (parameterModifierList != null && options.contains(InnerBuilderOption.JSR305_ANNOTATIONS)) {
            parameterModifierList.addAnnotation(JSR305_NONNULL)
        }
        copyConstructor.parameterList.add(constructorParameter)
        addCopyBody(nonFinalFields, copyConstructor, "this.")
        return copyConstructor
    }

    private fun addCopyBody(fields: Collection<PsiFieldMember?>, method: PsiMethod, qName: String) {
        val methodBody = method.body ?: return
        for (member in fields) {
            val field = member!!.element
            val assignStatement = psiElementFactory.createStatementFromText(
                String.format(
                    "%s%2\$s = copy.%3\$s;", qName, field.name, field.name
                ), method
            )
            methodBody.add(assignStatement)
        }
    }

    private fun generateBuilderConstructor(
        builderClass: PsiClass,
        finalFields: Collection<PsiFieldMember?>,
        options: Set<InnerBuilderOption>
    ): PsiMethod {
        val builderConstructor = psiElementFactory.createConstructor(builderClass.name!!)
        if (options.contains(InnerBuilderOption.NEW_BUILDER_METHOD)) {
            PsiUtil.setModifierProperty(builderConstructor, PsiModifier.PRIVATE, true)
        } else {
            PsiUtil.setModifierProperty(builderConstructor, PsiModifier.PUBLIC, true)
        }
        val builderConstructorBody = builderConstructor.body
        if (builderConstructorBody != null) {
            for (member in finalFields) {
                val field = member!!.element
                val fieldType = field.type
                val fieldName = field.name
                val parameter = psiElementFactory.createParameter(fieldName, fieldType)
                val parameterModifierList = parameter.modifierList
                val useJsr305 = options.contains(InnerBuilderOption.JSR305_ANNOTATIONS)
                if (!InnerBuilderUtils.isPrimitive(field) && parameterModifierList != null) {
                    if (useJsr305) parameterModifierList.addAnnotation(JSR305_NONNULL)
                }
                builderConstructor.parameterList.add(parameter)
                val assignStatement = psiElementFactory.createStatementFromText(
                    String.format(
                        "this.%1\$s = %1\$s;", fieldName
                    ), builderConstructor
                )
                builderConstructorBody.add(assignStatement)
            }
        }
        return builderConstructor
    }

    private fun generateNewBuilderMethod(
        builderType: PsiType, targetClass: PsiClass, finalFields: Collection<PsiFieldMember?>,
        options: Set<InnerBuilderOption>
    ): PsiMethod {
        val newBuilderMethod = psiElementFactory.createMethod(getBuilderMethodName(targetClass, options), builderType)
        PsiUtil.setModifierProperty(newBuilderMethod, PsiModifier.STATIC, true)
        PsiUtil.setModifierProperty(newBuilderMethod, PsiModifier.PUBLIC, true)
        val fieldList = StringBuilder()
        if (finalFields.isNotEmpty()) {
            for (member in finalFields) {
                val field = member!!.element
                val fieldType = field.type
                val fieldName = field.name
                val parameter = psiElementFactory.createParameter(fieldName, fieldType)
                val parameterModifierList = parameter.modifierList
                if (parameterModifierList != null) {
                    if (!InnerBuilderUtils.isPrimitive(field)) {
                        if (options.contains(InnerBuilderOption.JSR305_ANNOTATIONS)) parameterModifierList.addAnnotation(
                            JSR305_NONNULL
                        )
                    }
                }
                newBuilderMethod.parameterList.add(parameter)
                if (fieldList.isNotEmpty()) {
                    fieldList.append(", ")
                }
                fieldList.append(fieldName)
            }
        }
        val newBuilderMethodBody = newBuilderMethod.body
        if (newBuilderMethodBody != null) {
            val newStatement = psiElementFactory.createStatementFromText(
                String.format(
                    "return new %s(%s);", builderType.presentableText, fieldList
                ),
                newBuilderMethod
            )
            newBuilderMethodBody.add(newStatement)
        }
        return newBuilderMethod
    }

    private fun generateBuilderSetter(
        builderType: PsiType, member: PsiFieldMember?,
        options: Set<InnerBuilderOption>
    ): PsiMethod {
        val field = member!!.element
        val fieldType = field.type
        val fieldName = if (InnerBuilderUtils.hasOneLetterPrefix(field.name)) field.name[1].lowercaseChar()
            .toString() + field.name.substring(2) else field.name
        val methodName: String = if (options.contains(InnerBuilderOption.WITH_NOTATION)) {
            String.format("with%s", InnerBuilderUtils.capitalize(fieldName))
        } else if (options.contains(InnerBuilderOption.SET_NOTATION)) {
            String.format("set%s", InnerBuilderUtils.capitalize(fieldName))
        } else {
            fieldName
        }
        val parameterName =
            if (options.contains(InnerBuilderOption.FIELD_NAMES)) fieldName else (if (BUILDER_SETTER_DEFAULT_PARAMETER_NAME != fieldName) BUILDER_SETTER_DEFAULT_PARAMETER_NAME else BUILDER_SETTER_ALTERNATIVE_PARAMETER_NAME)
        val setterMethod = psiElementFactory.createMethod(methodName, builderType)
        val useJsr305 = options.contains(InnerBuilderOption.JSR305_ANNOTATIONS)
        if (useJsr305) setterMethod.modifierList.addAnnotation(JSR305_NONNULL)
        setterMethod.modifierList.setModifierProperty(PsiModifier.PUBLIC, true)
        val setterParameter = psiElementFactory.createParameter(parameterName, fieldType)
        if (fieldType !is PsiPrimitiveType) {
            val setterParameterModifierList = setterParameter.modifierList
            if (setterParameterModifierList != null) {
                if (useJsr305) setterParameterModifierList.addAnnotation(JSR305_NONNULL)
            }
        }
        setterMethod.parameterList.add(setterParameter)
        val setterMethodBody = setterMethod.body
        if (setterMethodBody != null) {
            val actualFieldName = if (options.contains(InnerBuilderOption.FIELD_NAMES)) "this.$fieldName" else fieldName
            val assignStatement = psiElementFactory.createStatementFromText(
                String.format(
                    "%s = %s;", actualFieldName, parameterName
                ), setterMethod
            )
            setterMethodBody.add(assignStatement)
            setterMethodBody.add(InnerBuilderUtils.createReturnThis(psiElementFactory, setterMethod))
        }
        setSetterComment(setterMethod, fieldName, parameterName)
        return setterMethod
    }

    private fun generateConstructor(targetClass: PsiClass, builderType: PsiType): PsiMethod {
        val constructor = psiElementFactory.createConstructor(targetClass.name!!)
        constructor.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
        val builderParameter = psiElementFactory.createParameter("builder", builderType)
        constructor.parameterList.add(builderParameter)
        val constructorBody = constructor.body
        if (constructorBody != null) {
            for (member in selectedFields) {
                val field = member!!.element
                val setterPrototype = PropertyUtil.generateSetterPrototype(field)
                val setter = targetClass.findMethodBySignature(setterPrototype, true)
                val fieldName = field.name
                var isFinal = false
                val modifierList = field.modifierList
                if (modifierList != null) {
                    isFinal = modifierList.hasModifierProperty(PsiModifier.FINAL)
                }
                val assignText: String = if (setter == null || isFinal) {
                    String.format("%1\$s = builder.%1\$s;", fieldName)
                } else {
                    String.format("%s(builder.%s);", setter.name, fieldName)
                }
                val assignStatement = psiElementFactory.createStatementFromText(assignText, null)
                constructorBody.add(assignStatement)
            }
        }
        return constructor
    }

    private fun generateBuildMethod(targetClass: PsiClass, options: Set<InnerBuilderOption>): PsiMethod {
        val targetClassType: PsiType = psiElementFactory.createType(targetClass)
        val buildMethod = psiElementFactory.createMethod("build", targetClassType)
        val useJsr305 = options.contains(InnerBuilderOption.JSR305_ANNOTATIONS)
        if (useJsr305) buildMethod.modifierList.addAnnotation(JSR305_NONNULL)
        buildMethod.modifierList.setModifierProperty(PsiModifier.PUBLIC, true)
        val buildMethodBody = buildMethod.body
        if (buildMethodBody != null) {
            val returnStatement = psiElementFactory.createStatementFromText(
                String.format(
                    "return new %s(this);", targetClass.name
                ), buildMethod
            )
            buildMethodBody.add(returnStatement)
        }
        setBuildMethodComment(buildMethod, targetClass)
        return buildMethod
    }

    private fun findOrCreateBuilderClass(targetClass: PsiClass): PsiClass {
        return targetClass.findInnerClassByName(BUILDER_CLASS_NAME, false)
            ?: return createBuilderClass(targetClass)
    }

    private fun createBuilderClass(targetClass: PsiClass): PsiClass {
        val builderClass = targetClass.add(psiElementFactory.createClass(BUILDER_CLASS_NAME)) as PsiClass
        PsiUtil.setModifierProperty(builderClass, PsiModifier.STATIC, true)
        PsiUtil.setModifierProperty(builderClass, PsiModifier.FINAL, true)
        setBuilderComment(builderClass, targetClass)
        setBuilderAnnotation(builderClass)
        return builderClass
    }

    private fun findOrCreateField(
        builderClass: PsiClass, member: PsiFieldMember?,
        last: PsiElement?
    ): PsiElement {
        val field = member!!.element
        val fieldName = field.name
        val fieldType = field.type
        val existingField = builderClass.findFieldByName(fieldName, false)
        if (existingField == null || !InnerBuilderUtils.areTypesPresentableEqual(existingField.type, fieldType)) {
            existingField?.delete()
            val newField = psiElementFactory.createField(fieldName, fieldType)
            return if (last != null) {
                builderClass.addAfter(newField, last)
            } else {
                builderClass.add(newField)
            }
        }
        return existingField
    }

    private fun addMethod(
        target: PsiClass, after: PsiElement?,
        newMethod: PsiMethod, replace: Boolean
    ): PsiElement {
        var existingMethod = target.findMethodBySignature(newMethod, false)
        if (existingMethod == null && newMethod.isConstructor) {
            for (constructor in target.constructors) {
                if (InnerBuilderUtils.areParameterListsEqual(
                        constructor.parameterList,
                        newMethod.parameterList
                    )
                ) {
                    existingMethod = constructor
                    break
                }
            }
        }
        if (existingMethod == null) {
            return if (after != null) {
                target.addAfter(newMethod, after)
            } else {
                target.add(newMethod)
            }
        } else if (replace) {
            existingMethod.replace(newMethod)
        }
        return existingMethod
    }

    private fun setBuilderComment(clazz: PsiClass, targetClass: PsiClass) {
        if (currentOptions().contains(InnerBuilderOption.WITH_JAVADOC)) {
            val str = StringBuilder("/**\n").append("* {@code ")
            str.append(targetClass.name).append("} builder static inner class.\n")
            str.append("*/")
            setStringComment(clazz, str.toString())
        }
    }

    private fun setBuilderAnnotation(clazz: PsiClass) {
        if (currentOptions().contains(InnerBuilderOption.PMD_AVOID_FIELD_NAME_MATCHING_METHOD_NAME_ANNOTATION)) {
            clazz.modifierList!!.addAnnotation("SuppressWarnings(\"PMD.AvoidFieldNameMatchingMethodName\")")
        }
    }

    private fun setSetterComment(method: PsiMethod, fieldName: String, parameterName: String) {
        if (currentOptions().contains(InnerBuilderOption.WITH_JAVADOC)) {
            val str = StringBuilder("/**\n").append("* Sets the {@code ").append(fieldName)
            str.append("} and returns a reference to this Builder enabling method chaining.\n")
            str.append("* @param ").append(parameterName).append(" the {@code ")
            str.append(fieldName).append("} to set\n")
            str.append("* @return a reference to this Builder\n*/")
            setStringComment(method, str.toString())
        }
    }

    private fun setBuildMethodComment(method: PsiMethod, targetClass: PsiClass) {
        if (currentOptions().contains(InnerBuilderOption.WITH_JAVADOC)) {
            val str = StringBuilder("/**\n")
            str.append("* Returns a {@code ").append(targetClass.name).append("} built ")
            str.append("from the parameters previously set.\n*\n")
            str.append("* @return a {@code ").append(targetClass.name).append("} ")
            str.append("built with parameters of this {@code ").append(targetClass.name).append(".Builder}\n*/")
            setStringComment(method, str.toString())
        }
    }

    private fun setStringComment(method: PsiMethod, strComment: String) {
        val comment = psiElementFactory.createCommentFromText(strComment, null)
        val doc = method.docComment
        if (doc != null) {
            doc.replace(comment)
        } else {
            method.addBefore(comment, method.firstChild)
        }
    }

    private fun setStringComment(clazz: PsiClass, strComment: String) {
        val comment = psiElementFactory.createCommentFromText(strComment, null)
        val doc = clazz.docComment
        if (doc != null) {
            doc.replace(comment)
        } else {
            clazz.addBefore(comment, clazz.firstChild)
        }
    }

    companion object {
        private const val BUILDER_CLASS_NAME: @NonNls String = "Builder"
        private const val BUILDER_SETTER_DEFAULT_PARAMETER_NAME: @NonNls String = "val"
        private const val BUILDER_SETTER_ALTERNATIVE_PARAMETER_NAME: @NonNls String = "value"
        private const val JSR305_NONNULL: @NonNls String = "javax.annotation.Nonnull"
        private const val DEFAULT_BUILDER_METHOD_NAME: @NonNls String = "newBuilder"
        private const val BUILDER_METHOD_NAME: @NonNls String = "builder"
        fun generate(
            project: Project, editor: Editor, file: PsiFile,
            selectedFields: List<PsiFieldMember?>
        ) {
            val builderGenerator: Runnable = InnerBuilderGenerator(project, file, editor, selectedFields)
            ApplicationManager.getApplication().runWriteAction(builderGenerator)
        }

        private fun currentOptions(): EnumSet<InnerBuilderOption> {
            val options = EnumSet.noneOf(
                InnerBuilderOption::class.java
            )
            val propertiesComponent = PropertiesComponent.getInstance()
            for (option in InnerBuilderOption.values()) {
                if (option.isBooleanProperty) {
                    val currentSetting = propertiesComponent.getBoolean(option.property, false)
                    if (currentSetting) {
                        options.add(option)
                    }
                } else {
                    val currentValue = propertiesComponent.getValue(option.property).toString()
                    InnerBuilderOption.findValue(currentValue)
                        .ifPresent { e: InnerBuilderOption -> options.add(e) }
                }
            }
            return options
        }
    }
}
