package org.jetbrains.plugins.innerbuilder

import com.intellij.codeInsight.generation.PsiFieldMember
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil

object InnerBuilderCollector {
    private val logClassList = listOf(
        "org.apache.log4j.Logger",
        "org.apache.logging.log4j.Logger",
        "java.util.logging.Logger",
        "org.slf4j.Logger",
        "ch.qos.logback.classic.Logger",
        "net.sf.microlog.core.Logger",
        "org.apache.commons.logging.Log",
        "org.pmw.tinylog.Logger",
        "org.jboss.logging.Logger",
        "jodd.log.Logger"
    )

    fun collectFields(file: PsiFile, editor: Editor): List<PsiFieldMember>? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        val clazz = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        if (clazz == null || clazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return null
        }
        val allFields: MutableList<PsiFieldMember> = ArrayList()
        var classToExtractFieldsFrom = clazz
        while (classToExtractFieldsFrom != null) {
            val classFieldMembers = collectFieldsInClass(
                element, clazz,
                classToExtractFieldsFrom
            )
            allFields.addAll(0, classFieldMembers)
            classToExtractFieldsFrom = classToExtractFieldsFrom.superClass
        }
        return allFields
    }

    private fun collectFieldsInClass(
        element: PsiElement,
        accessObjectClass: PsiClass,
        clazz: PsiClass
    ): List<PsiFieldMember> {
        val classFieldMembers: MutableList<PsiFieldMember> = ArrayList()
        val helper = JavaPsiFacade.getInstance(clazz.project).resolveHelper
        for (field in clazz.fields) {

            // check access to the field from the builder container class (e.g. private superclass fields)
            if ((helper.isAccessible(field, clazz, accessObjectClass) || hasSetter(clazz, field.name))
                && !PsiTreeUtil.isAncestor(field, element, false)
            ) {

                // skip static fields
                if (field.hasModifierProperty(PsiModifier.STATIC)) {
                    continue
                }

                // skip any uppercase fields
                if (!InnerBuilderUtils.hasLowerCaseChar(field.name)) {
                    continue
                }

                // skip eventual logging fields
                if (logClassList.contains(field.type.canonicalText)) {
                    continue
                }
                if (field.hasModifierProperty(PsiModifier.FINAL)) {
                    if (field.initializer != null) {
                        continue  // skip final fields that are assigned in the declaration
                    }
                    if (!accessObjectClass.isEquivalentTo(clazz)) {
                        continue  // skip final superclass fields
                    }
                }
                val containingClass = field.containingClass
                if (containingClass != null) {
                    classFieldMembers.add(buildFieldMember(field, containingClass, clazz))
                }
            }
        }
        return classFieldMembers
    }

    private fun hasSetter(clazz: PsiClass, name: String): Boolean {
        for (i in clazz.allMethods.indices) {
            if (clazz.allMethods[i].name == String.format("set%s", InnerBuilderUtils.capitalize(name))) {
                return true
            }
        }
        return false
    }

    private fun buildFieldMember(
        field: PsiField, containingClass: PsiClass,
        clazz: PsiClass
    ): PsiFieldMember {
        return PsiFieldMember(
            field,
            TypeConversionUtil.getSuperClassSubstitutor(containingClass, clazz, PsiSubstitutor.EMPTY)
        )
    }
}
