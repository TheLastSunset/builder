package org.jetbrains.plugins.innerbuilder

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.util.PsiErrorElementUtil

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class InnerBuilderActionTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(
            JavaFileType.INSTANCE,
            """
            package org.jetbrains.plugins.innerbuilder;
            public class MyPlugin {

            }
        """.trimIndent()
        )
        val selectedFields = InnerBuilderCollector.collectFields(psiFile, myFixture.editor)

        if (selectedFields.isNullOrEmpty()) {
            return
        }
        InnerBuilderGenerator(project, psiFile, myFixture.editor, selectedFields)
    }

}
