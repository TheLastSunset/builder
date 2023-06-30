package org.jetbrains.plugins.innerbuilder

import com.intellij.codeInsight.generation.PsiFieldMember
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase

class InnerBuilderCollectorTest : BasePlatformTestCase() {

    fun testCollectFieldsIsNullOrEmpty() {
        val file = myFixture.configureByText(
            JavaFileType.INSTANCE,
            """
            public class TestClass {

            }
        """.trimIndent()
        )
        val editor = myFixture.editor

        val fields = InnerBuilderCollector.collectFields(file, editor)
        assertNullOrEmpty(fields)
    }

    fun testCollectFieldsIsNotEmpty() {
        val file = myFixture.configureByText(
            JavaFileType.INSTANCE,
            """
            public class TestClass {
                private String name;
                public String getName() {
                    return name;
                }
                public void setName(String name) {
                    this.name = name;
                }
            }
        """.trimIndent()
        )
        val editor = myFixture.editor

        val fields = InnerBuilderCollector.collectFields(file, editor)
        assertNotNull("fields should be not null", fields)
        UsefulTestCase.assertSize(1, fields as Collection<PsiFieldMember>)
        TestCase.assertEquals("name", fields[0].element.name)
    }
}
