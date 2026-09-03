package com.smarthealth.vitalhub.feature.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRenderingTest {
    @Test
    fun `single dollar inline latex is normalized for Markwon`() {
        val markdown = "行内：\$HR_{avg} = \\frac{1}{n}\$，转义 \\$5，代码 `\$raw\$`"

        assertEquals(
            "行内：\$\$HR_{avg} = \\frac{1}{n}\$\$，转义 \\$5，代码 `\$raw\$`",
            normalizeInlineLatex(markdown),
        )
    }

    @Test
    fun `block latex and fenced code remain unchanged`() {
        val markdown = """
            ${'$'}${'$'}
            QTc = \\frac{QT}{\\sqrt{RR}}
            ${'$'}${'$'}
            ```text
            ${'$'}not_math${'$'}
            ```
        """.trimIndent()

        assertEquals(markdown, normalizeInlineLatex(markdown))
    }

    @Test
    fun `top level table becomes an independently scrollable render block`() {
        val blocks = splitMarkdownTables(
            """
                前文

                | 指标 | 结果 | 范围 | 状态 |
                |---|---:|:---:|:---:|
                | 心率 | 72 | 60-100 | 正常 |

                后文
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        assertFalse(blocks[0].isTable)
        assertTrue(blocks[1].isTable)
        assertEquals(4, blocks[1].tableColumnCount)
        assertFalse(blocks[2].isTable)
    }

    @Test
    fun `email matcher excludes adjacent Chinese label`() {
        assertEquals(
            listOf("doctor@example.com"),
            MarkdownEmailLinkPlugin.emailMatches("自动邮箱：doctor@example.com"),
        )
    }

    @Test
    fun `phone matcher accepts explicit phone but rejects metric ranges`() {
        assertTrue(MarkdownPhoneLinkPlugin.phoneMatches("自动电话：+86 138 0013 8000").isNotEmpty())
        assertTrue(MarkdownPhoneLinkPlugin.phoneMatches("参考范围 60–100 bpm").isEmpty())
        assertTrue(MarkdownPhoneLinkPlugin.phoneMatches("参考范围 12-20 次/分").isEmpty())
        assertTrue(MarkdownPhoneLinkPlugin.phoneMatches("QTc < 450 ms").isEmpty())
    }

    @Test
    fun `footnotes become linked references and definitions while code is unchanged`() {
        val markdown = """
            正文[^1]，重复[^note]，代码 `[^1]`

            [^1]: 第一条定义
            [^note]: 文本标签定义
        """.trimIndent()

        val normalized = normalizeFootnotes(markdown)

        assertTrue(normalized.contains("[［1］](vitalhub-footnote://definition/1)"))
        assertTrue(normalized.contains("[［note］](vitalhub-footnote://definition/note)"))
        assertTrue(normalized.contains("代码 `[^1]`"))
        assertTrue(normalized.contains("### 脚注"))
        assertTrue(normalized.contains("**〔1〕** 第一条定义"))
        assertTrue(normalized.contains("[返回正文](vitalhub-footnote://reference/1)"))
        assertFalse(normalized.contains("[^1]:"))
    }
}
