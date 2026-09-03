package com.smarthealth.vitalhub.feature.analysis.data

import com.google.gson.Gson
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

internal data class RemoteResponse<T>(
    val httpCode: Int,
    val body: ApiEnvelope<T>?,
    val errorCode: Int?,
    val errorMessage: String?,
) {
    val isHttpSuccessful: Boolean get() = httpCode in 200..299
}

internal interface AnalysisRemoteDataSource {
    suspend fun upload(
        file: File,
        appVersion: String,
        protocolVersion: String,
        onProgress: (Int) -> Unit,
    ): RemoteResponse<CreateAnalysisData>

    suspend fun getResult(analysisId: String): RemoteResponse<AnalysisResultData>
}

internal class RetrofitAnalysisRemoteDataSource(
    private val api: AnalysisApi,
    private val gson: Gson = Gson(),
) : AnalysisRemoteDataSource {
    override suspend fun upload(
        file: File,
        appVersion: String,
        protocolVersion: String,
        onProgress: (Int) -> Unit,
    ): RemoteResponse<CreateAnalysisData> {
        val requestBody = ProgressFileRequestBody(
            file = file,
            mediaType = DICOM_MEDIA_TYPE,
            onProgress = onProgress,
        )
        return api.createAnalysis(
            data = MultipartBody.Part.createFormData("data", file.name, requestBody),
            appVersion = appVersion.toRequestBody(TEXT_MEDIA_TYPE),
            protocolVersion = protocolVersion.toRequestBody(TEXT_MEDIA_TYPE),
        ).toRemoteResponse()
    }

    override suspend fun getResult(analysisId: String): RemoteResponse<AnalysisResultData> =
        api.getResult(analysisId).toRemoteResponse()

    private fun <T> Response<ApiEnvelope<T>>.toRemoteResponse(): RemoteResponse<T> {
        if (isSuccessful) {
            return RemoteResponse(code(), body(), null, null)
        }
        val error = runCatching {
            errorBody()?.string()?.let { gson.fromJson(it, ApiErrorEnvelope::class.java) }
        }.getOrNull()
        return RemoteResponse(
            httpCode = code(),
            body = null,
            errorCode = error?.code,
            errorMessage = error?.message,
        )
    }

    private data class ApiErrorEnvelope(
        val code: Int?,
        val message: String?,
    )

    private companion object {
        val DICOM_MEDIA_TYPE = "application/dicom".toMediaType()
        val TEXT_MEDIA_TYPE = "text/plain".toMediaType()
    }
}
