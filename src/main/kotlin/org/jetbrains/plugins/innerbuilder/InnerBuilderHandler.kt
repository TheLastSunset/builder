package org.jetbrains.plugins.innerbuilder

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile

class InnerBuilderHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor, file: PsiFile): Boolean {
        if (file !is PsiJavaFile) {
            return false
        }
        editor.project ?: return false
        return InnerBuilderUtils.getStaticOrTopLevelClass(file, editor) != null && isApplicable(file, editor)
    }

    override fun startInWriteAction(): Boolean {
        return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val currentDocument = psiDocumentManager.getDocument(file) ?: return
        psiDocumentManager.commitDocument(currentDocument)
        if (!EditorModificationUtil.checkModificationAllowed(editor)) {
            return
        }
        if (!FileDocumentManager.getInstance().requestWriting(editor.document, project)) {
            return
        }
        val existingFields = InnerBuilderCollector.collectFields(file, editor)
        if (existingFields != null) {
            val selectedFields = InnerBuilderOptionSelector.selectFieldsAndOptions(existingFields, project)
            if (selectedFields.isNullOrEmpty()) {
                return
            }
            InnerBuilderGenerator.generate(project, editor, file, selectedFields)
        }
    }

    companion object {
        private fun isApplicable(file: PsiFile, editor: Editor): Boolean {
            val targetElements = InnerBuilderCollector.collectFields(file, editor)
            return !targetElements.isNullOrEmpty()
        }
    }
}
