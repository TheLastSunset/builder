package org.jetbrains.plugins.innerbuilder

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.BaseCodeInsightAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * The IntelliJ IDEA action for this plugin, generates an inner builder class as described in Effective Java.
 *
 * @author  Mathias Bogaert
 */
class InnerBuilderAction : BaseCodeInsightAction() {
    private val handler = InnerBuilderHandler()
    override fun getHandler(): CodeInsightActionHandler {
        return handler
    }

    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile): Boolean {
        return handler.isValidFor(editor, file)
    }
}
