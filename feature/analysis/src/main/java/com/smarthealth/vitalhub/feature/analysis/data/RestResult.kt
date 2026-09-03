package com.smarthealth.vitalhub.feature.analysis.data

import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

internal sealed interface RestResult<out T> {
    data class Success<T>(
        val httpCode: Int,
        val body: ApiEnvelope<T>,
    ) : RestResult<T>

    data class HttpFailure(
        val httpCode: Int,
        val businessCode: Int?,
        val message: String?,
    ) : RestResult<Nothing>

    data class NetworkFailure(val cause: IOException) : RestResult<Nothing>

    data class InvalidResponse(
        val httpCode: Int?,
        val message: String,
    ) : RestResult<Nothing>
}
internal class RestCallExecutor(
    private val gson: Gson = Gson(),
) {
    suspend fun <T> execute(
        request: suspend () -> Response<ApiEnvelope<T>>,
    ): RestResult<T> {
        val response = try {
            request()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            return RestResult.NetworkFailure(error)
        } catch (error: Throwable) {
            return RestResult.InvalidResponse(null, error.message ?: "服务器响应解析失败")
        }

        if (response.isSuccessful) {
            return response.body()?.let { RestResult.Success(response.code(), it) }
                ?: RestResult.InvalidResponse(response.code(), "服务器返回为空")
        }

        val error = runCatching {
            response.errorBody()?.string()?.let {
                gson.fromJson(it, ErrorEnvelope::class.java)
            }
        }.getOrNull()
        return RestResult.HttpFailure(
            httpCode = response.code(),
            businessCode = error?.code,
            message = error?.message,
        )
    }

    private data class ErrorEnvelope(
        val code: Int?,
        val message: String?,
    )
}
