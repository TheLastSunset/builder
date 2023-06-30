package org.jetbrains.plugins.innerbuilder

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class InnerBuilderCollectorTest : BasePlatformTestCase() {

    fun testCollectFieldsIsNull() {
        val file = myFixture.configureByText(
            JavaFileType.INSTANCE,
            """
            package org.jetbrains.plugins.innerbuilder;
            public class MyPlugin {

            }
        """.trimIndent()
        )
        val editor = myFixture.editor

        val fields = InnerBuilderCollector.collectFields(file, editor)
        assertNull("fields should be null", fields)
    }

}
