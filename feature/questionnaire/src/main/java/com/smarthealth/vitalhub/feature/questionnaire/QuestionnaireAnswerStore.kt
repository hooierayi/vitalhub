package com.smarthealth.vitalhub.feature.questionnaire

import android.content.Context
import com.smarthealth.vitalhub.core.storage.KVStorage
import com.smarthealth.vitalhub.core.storage.Storage
import com.smarthealth.vitalhub.core.storage.StorageBackend
import com.smarthealth.vitalhub.core.storage.StorageOptions

internal data class QuestionnaireDraft(
    val answers: List<String>,
    val pageIndex: Int,
    val submitted: Boolean,
)

internal interface QuestionnaireAnswerStore {
    fun load(sessionId: String, phase: String, answerCount: Int): QuestionnaireDraft?
    fun save(sessionId: String, phase: String, answers: List<String>, pageIndex: Int, submitted: Boolean): Boolean
}

internal class LocalQuestionnaireAnswerStore(
    private val storage: KVStorage,
) : QuestionnaireAnswerStore {
    override fun load(sessionId: String, phase: String, answerCount: Int): QuestionnaireDraft? {
        if (sessionId.isBlank() || storage.getInt(key(sessionId, phase, FIELD_SCHEMA), 0) != SCHEMA_VERSION) return null
        if (storage.getInt(key(sessionId, phase, FIELD_ANSWER_COUNT), 0) != answerCount) return null
        return QuestionnaireDraft(
            answers = List(answerCount) { index ->
                storage.getString(key(sessionId, phase, "$FIELD_ANSWER_PREFIX$index")).orEmpty()
            },
            pageIndex = storage.getInt(key(sessionId, phase, FIELD_PAGE_INDEX), 0),
            submitted = storage.getBoolean(key(sessionId, phase, FIELD_SUBMITTED)),
        )
    }

    override fun save(
        sessionId: String,
        phase: String,
        answers: List<String>,
        pageIndex: Int,
        submitted: Boolean,
    ): Boolean {
        if (sessionId.isBlank()) return false
        return storage.edit().apply {
            putInt(key(sessionId, phase, FIELD_SCHEMA), SCHEMA_VERSION)
            putInt(key(sessionId, phase, FIELD_ANSWER_COUNT), answers.size)
            putInt(key(sessionId, phase, FIELD_PAGE_INDEX), pageIndex)
            putBoolean(key(sessionId, phase, FIELD_SUBMITTED), submitted)
            putLong(key(sessionId, phase, FIELD_UPDATED_AT), System.currentTimeMillis())
            answers.forEachIndexed { index, answer ->
                putString(key(sessionId, phase, "$FIELD_ANSWER_PREFIX$index"), answer)
            }
        }.commit()
    }

    private fun key(sessionId: String, phase: String, field: String): String = "$sessionId:$phase:$field"

    private companion object {
        const val SCHEMA_VERSION = 1
        const val FIELD_SCHEMA = "schema"
        const val FIELD_ANSWER_COUNT = "answer-count"
        const val FIELD_ANSWER_PREFIX = "answer-"
        const val FIELD_PAGE_INDEX = "page-index"
        const val FIELD_SUBMITTED = "submitted"
        const val FIELD_UPDATED_AT = "updated-at"
    }
}

internal object QuestionnaireAnswerStores {
    @Volatile
    private var store: QuestionnaireAnswerStore? = null

    fun initialize(context: Context) {
        if (store != null) return
        synchronized(this) {
            if (store == null) {
                store = LocalQuestionnaireAnswerStore(
                    Storage.create(
                        context.applicationContext,
                        STORAGE_ID,
                        StorageOptions(backend = StorageBackend.MMKV),
                    ),
                )
            }
        }
    }

    fun requireStore(): QuestionnaireAnswerStore = checkNotNull(store) {
        "QuestionnaireAnswerStores must be initialized before creating QuestionnaireViewModel."
    }

    private const val STORAGE_ID = "vitalhub-questionnaire-answers"
}
