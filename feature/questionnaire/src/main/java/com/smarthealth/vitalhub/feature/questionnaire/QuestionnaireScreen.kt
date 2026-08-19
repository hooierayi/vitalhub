package com.smarthealth.vitalhub.feature.questionnaire

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.*

@Composable
fun QuestionnaireScreen(state: QuestionnaireUiState, onAnswer: (Int, String) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Surface(color = VitalColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
            ProgressHeader(state.title, state.pageIndex, state.pages.size)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.currentPage.forEach { question ->
                    QuestionBlock(question, state.answers[question.id]) { onAnswer(question.id, it) }
                }
                if (state.validationError) Text("请完成本页所有题目后继续", color = VitalColors.Danger, fontSize = 14.sp)
                state.flowError?.let { Text(it, color = VitalColors.Danger, fontSize = 14.sp) }
                Spacer(Modifier.height(9.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.pageIndex > 0) FlowButton("上一步", FlowButtonStyle.OUTLINE, Modifier.weight(1f), onPrevious)
                FlowButton(if (state.pageIndex == state.pages.lastIndex) "完成问卷" else "下一步", FlowButtonStyle.PRIMARY, Modifier.weight(1f), onNext)
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun ProgressHeader(title: String, pageIndex: Int, pageCount: Int) {
    val progress = (pageIndex + 1).toFloat() / pageCount
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$title  ${pageIndex + 1}/$pageCount", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
        Text("${(progress * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VitalColors.Teal)
    }
    Spacer(Modifier.height(7.dp)); ProgressTrack(progress); Spacer(Modifier.height(14.dp))
}

@Composable
private fun QuestionBlock(question: Question, answer: String, onAnswer: (String) -> Unit) {
    Text("${question.number}. ${question.title}", fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
    val selected = if (answer.isBlank()) emptySet() else answer.split(MULTI_SEPARATOR).toSet()
    val singleColumn = question.options.any { it.length > 10 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        question.options.chunked(if (singleColumn) 1 else 2).forEach { rowOptions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    val chosen = selected.any { it == option || it.startsWith("$option：") }
                    ChoiceTile(option, chosen, Modifier.weight(1f), question.multiple) {
                        val next = when {
                            !question.multiple -> setOf(option)
                            option in question.exclusiveOptions -> setOf(option)
                            chosen -> selected.filterNot { it == option || it.startsWith("$option：") }.toSet()
                            else -> (selected - question.exclusiveOptions) + option
                        }
                        onAnswer(next.joinToString(MULTI_SEPARATOR))
                    }
                }
                if (!singleColumn && rowOptions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        val other = question.otherOption
        if (other != null && selected.any { it == other || it.startsWith("$other：") }) {
            OutlinedTextField(
                value = selected.firstOrNull { it.startsWith("$other：") }?.substringAfter('：').orEmpty(),
                onValueChange = { text -> onAnswer(((selected.filterNot { it == other || it.startsWith("$other：") }).toSet() + if (text.isBlank()) other else "$other：$text").joinToString(MULTI_SEPARATOR)) },
                modifier = Modifier.fillMaxWidth(), label = { Text("请填写其他症状") }, singleLine = true,
            )
        }
    }
}
