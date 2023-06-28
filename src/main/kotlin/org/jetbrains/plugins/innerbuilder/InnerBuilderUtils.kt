package org.jetbrains.plugins.innerbuilder

import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.NonNls

object InnerBuilderUtils {
    private const val JAVA_DOT_LANG: @NonNls String = "java.lang."

    /**
     * Does the string have a lowercase character?
     *
     * @param str the string to test.
     * @return true if the string has a lowercase character, false if not.
     */
    fun hasLowerCaseChar(str: String): Boolean {
        for (element in str) {
            if (Character.isLowerCase(element)) {
                return true
            }
        }
        return false
    }

    fun capitalize(str: String): String {
        return if (hasOneLetterPrefix(str)) str[1].uppercaseChar()
            .toString() + str.substring(2) else str[0].uppercaseChar().toString() + str.substring(1)
    }

    fun hasOneLetterPrefix(str: String): Boolean {
        return str.length == 1 || Character.isLowerCase(str[0]) && Character.isUpperCase(str[1])
    }

    private fun stripJavaLang(typeString: String): String {
        return if (typeString.startsWith(JAVA_DOT_LANG)) typeString.substring(JAVA_DOT_LANG.length) else typeString
    }

    fun areParameterListsEqual(paramList1: PsiParameterList, paramList2: PsiParameterList): Boolean {
        if (paramList1.parametersCount != paramList2.parametersCount) {
            return false
        }
        val param1Params = paramList1.parameters
        val param2Params = paramList2.parameters
        for (i in param1Params.indices) {
            val param1Param = param1Params[i]
            val param2Param = param2Params[i]
            if (!areTypesPresentableEqual(param1Param.type, param2Param.type)) {
                return false
            }
        }
        return true
    }

    fun areTypesPresentableEqual(type1: PsiType?, type2: PsiType?): Boolean {
        if (type1 != null && type2 != null) {
            val type1Canonical = stripJavaLang(type1.presentableText)
            val type2Canonical = stripJavaLang(type2.presentableText)
            return type1Canonical == type2Canonical
        }
        return false
    }

    /**
     * @param file   psi file
     * @param editor editor
     * @return psiClass if class is static or top level. Otherwise returns `null`
     */
    fun getStaticOrTopLevelClass(file: PsiFile, editor: Editor): PsiClass? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return null
        val topLevelClass = PsiUtil.getTopLevelClass(element)
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
        return if (psiClass != null && (psiClass.hasModifierProperty(PsiModifier.STATIC) ||
                    psiClass.manager.areElementsEquivalent(psiClass, topLevelClass))
        ) psiClass else null
    }

    fun isPrimitive(psiField: PsiField): Boolean {
        return psiField.type is PsiPrimitiveType
    }

    fun createReturnThis(psiElementFactory: PsiElementFactory, context: PsiElement?): PsiStatement {
        return psiElementFactory.createStatementFromText("return this;", context)
    }
}
