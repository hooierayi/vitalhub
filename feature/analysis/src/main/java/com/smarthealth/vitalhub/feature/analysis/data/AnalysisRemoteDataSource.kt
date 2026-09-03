package com.smarthealth.vitalhub.feature.analysis.data

import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

internal interface AnalysisRemoteDataSource {
    suspend fun upload(
        file: File,
        appVersion: String,
        protocolVersion: String,
        onProgress: (Int) -> Unit,
    ): RestResult<CreateAnalysisData>

    suspend fun getResult(analysisId: String): RestResult<AnalysisResultData>
}

internal class RetrofitAnalysisRemoteDataSource(
    private val api: AnalysisApi,
    private val executor: RestCallExecutor = RestCallExecutor(),
) : AnalysisRemoteDataSource {
    override suspend fun upload(
        file: File,
        appVersion: String,
        protocolVersion: String,
        onProgress: (Int) -> Unit,
    ): RestResult<CreateAnalysisData> {
        val requestBody = ProgressFileRequestBody(
            file = file,
            mediaType = DICOM_MEDIA_TYPE,
            onProgress = onProgress,
        )
        return executor.execute {
            api.createAnalysis(
                data = MultipartBody.Part.createFormData("data", file.name, requestBody),
                appVersion = appVersion.toRequestBody(TEXT_MEDIA_TYPE),
                protocolVersion = protocolVersion.toRequestBody(TEXT_MEDIA_TYPE),
            )
        }
    }

    override suspend fun getResult(analysisId: String): RestResult<AnalysisResultData> =
        executor.execute { api.getResult(analysisId) }

    private companion object {
        val DICOM_MEDIA_TYPE = "application/dicom".toMediaType()
        val TEXT_MEDIA_TYPE = "text/plain".toMediaType()
    }
}
