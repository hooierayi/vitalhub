package com.smarthealth.vitalhub.feature.analysis.data

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

internal data class ApiEnvelope<T>(
    val code: Int,
    val message: String,
    val data: T?,
)

internal data class CreateAnalysisData(
    @SerializedName("session_id") val sessionId: String?,
    @SerializedName("analysis_id") val analysisId: String?,
    val status: String?,
)

internal data class AnalysisResultData(
    @SerializedName("analysis_id") val analysisId: String?,
    val status: String?,
    val result: String?,
)

internal interface AnalysisApi {
    @Multipart
    @POST("api/v1/analyze")
    suspend fun createAnalysis(
        @Part data: MultipartBody.Part,
        @Part("app_version") appVersion: RequestBody,
        @Part("protocol_version") protocolVersion: RequestBody,
    ): Response<ApiEnvelope<CreateAnalysisData>>

    @GET("api/v1/result/{analysisId}")
    suspend fun getResult(
        @Path("analysisId") analysisId: String,
    ): Response<ApiEnvelope<AnalysisResultData>>
}
