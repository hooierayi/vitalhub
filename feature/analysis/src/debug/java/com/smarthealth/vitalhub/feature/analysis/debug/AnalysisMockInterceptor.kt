package com.smarthealth.vitalhub.feature.analysis.debug

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Debug-only response fixture for visually checking every enabled Markdown renderer. */
internal class AnalysisMockInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!AnalysisMockConfig.enabled) {
            return chain.proceed(chain.request())
        }
        val request = chain.request()
        return when {
            request.method == "POST" && request.url.encodedPath == ANALYZE_PATH -> response(
                chain = chain,
                code = 202,
                body = """
                    {
                      "code": 100,
                      "message": "debug mock accepted",
                      "data": {
                        "session_id": "debug-markdown-session",
                        "analysis_id": "$MOCK_ANALYSIS_ID",
                        "status": "processing",
                        "poll_interval_secs": 0
                      }
                    }
                """.trimIndent(),
            )

            request.method == "GET" && request.url.encodedPath.startsWith(RESULT_PATH_PREFIX) -> {
                val analysisId = request.url.pathSegments.lastOrNull().orEmpty()
                response(
                    chain = chain,
                    code = 200,
                    body = """
                        {
                          "code": 0,
                          "message": "debug mock completed",
                          "data": {
                            "analysis_id": ${analysisId.toJsonString()},
                            "status": "completed",
                            "result": ${MARKDOWN_SHOWCASE.toJsonString()}
                          }
                        }
                    """.trimIndent(),
                )
            }

            else -> chain.proceed(request)
        }
    }

    private fun response(
        chain: Interceptor.Chain,
        code: Int,
        body: String,
    ): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(if (code == 202) "Accepted" else "OK")
        .header("Content-Type", JSON_MEDIA_TYPE.toString())
        .body(body.toResponseBody(JSON_MEDIA_TYPE))
        .build()

    private fun String.toJsonString(): String = buildString(length + 2) {
        append('"')
        this@toJsonString.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    internal companion object {
        const val MOCK_ANALYSIS_ID = "debug-markdown-showcase"

        val MARKDOWN_SHOWCASE = """
            # AI 健康分析报告

            > **演示说明：** 这是 Debug 拦截器返回的 Markdown 全格式样例，用于检查报告页渲染效果。
            >
            > 支持嵌套引用、**粗体**、*斜体*、***粗斜体***、~~删除线~~ 和 `行内代码`。

            ---

            ## 1. 标题层级

            ### 三级标题
            #### 四级标题
            ##### 五级标题
            ###### 六级标题

            普通段落包含中文、English、数字 123、符号 &amp;、转义字符 \*不是斜体\*，以及 Emoji：❤️ 🫀 ✅。
            第一行使用软换行
            第二行继续显示；这一行末尾使用反斜杠强制换行\
            这里是强制换行后的内容。

            ## 2. 列表与任务

            - 无序列表第一项
              - 二级项目 A
              - 二级项目 B
                1. 三级有序项目
                2. 第二个有序项目
            - 无序列表第二项

            1. 采集数据
            2. 上传 DICOM
            3. 生成分析报告

            - [x] CommonMark 基础格式
            - [x] 表格、删除线与任务列表
            - [x] 代码高亮和 LaTeX
            - [ ] 人工复核待完成

            ## 3. 指标表格

            | 指标 | 本次结果 | 参考范围 | 状态 |
            |:---|---:|:---:|:---:|
            | 平均心率 | **72 bpm** | 60–100 bpm | ✅ 正常 |
            | 呼吸频率 | 16 次/分 | 12–20 次/分 | ✅ 正常 |
            | QTc | 418 ms | &lt; 450 ms | ✅ 正常 |
            | 示例链接 | [查看 Markwon](https://noties.io/Markwon/) | — | 可点击 |

            ## 4. 代码高亮

            ```kotlin
            data class VitalSigns(
                val heartRate: Int,
                val respiratoryRate: Int,
            )

            fun riskLevel(value: Int) = when {
                value < 60 -> "LOW"
                value > 100 -> "HIGH"
                else -> "NORMAL"
            }
            ```

            ```json
            {
              "analysis_id": "debug-markdown-showcase",
              "status": "completed",
              "confidence": 0.98
            }
            ```

            ```python
            def average(values):
                return sum(values) / len(values)
            ```

            ## 5. 数学公式

            行内公式：${'$'}HR_{avg} = \frac{1}{n} \sum_{i=1}^{n} HR_i${'$'}

            ${'$'}${'$'}
            QTc = \frac{QT}{\sqrt{RR}}
            ${'$'}${'$'}

            ${'$'}${'$'}
            SpO_2 = \frac{HbO_2}{HbO_2 + Hb} \times 100\%
            ${'$'}${'$'}

            ## 6. HTML

            <p><b>HTML 粗体</b>、<i>HTML 斜体</i>、<u>HTML 下划线</u>、H<sub>2</sub>O、x<sup>2</sup></p>

            <blockquote>这是通过 HTML 标签生成的引用内容。</blockquote>

            ## 7. 链接与自动识别

            - Markdown 链接：[OpenAI](https://openai.com/)
            - 自动网址：https://developer.android.com
            - 自动邮箱：doctor@example.com
            - 自动电话：+86 138 0013 8000

            ## 8. 图片

            普通位图：

            ![普通 PNG](https://raw.githubusercontent.com/59naga/fixture-images/master/still.PNG)

            SVG（不同画布与宽高比）：

            ![Markdown SVG](https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/markdown.svg)

            ![Android SVG](https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/android.svg)

            ![GitHub SVG](https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/github.svg)

            GIF（不同尺寸与文件大小）：

            ![73×73 GIF](https://raw.githubusercontent.com/59naga/fixture-images/master/animated.GIF)

            ![480×480 GIF](https://raw.githubusercontent.com/steipete/gifgrep/main/gifdecode/testdata/knowledge-human-pink.gif)

            ![492×229 GIF](https://raw.githubusercontent.com/imgproxy/test-images/main/gif/gif.gif)

            ## 9. 其他元素

            `inline code`、`Ctrl + Enter`

                这是缩进代码块
                第二行缩进代码

            脚注可以从正文跳转到定义[^1]，也支持重复引用[^2]。

            [^1]: 第一条脚注用于解释分析报告里的补充信息。
            [^2]: 第二条脚注用于验证多条定义和返回正文。

            ## 10. PDF

            [打开 PDF 示例](https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf)

            ---

            **报告结束。** 以上内容仅用于 UI 调试，不代表真实健康结论。
        """.trimIndent()

        private const val ANALYZE_PATH = "/api/v1/analyze"
        private const val RESULT_PATH_PREFIX = "/api/v1/result/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal object AnalysisMockConfig {
    @Volatile
    var enabled: Boolean = false
        private set

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}
