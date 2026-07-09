package com.example.foresight.data.repository

import com.example.foresight.data.network.PredictionRequest
import com.example.foresight.data.network.PredictionResponse
import com.example.foresight.data.network.RetrofitClient
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class RiskRepository {
    suspend fun getRiskPrediction(request: PredictionRequest): Result<PredictionResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.predictRisk(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        validatePredictionResponse(body)
                    } else {
                        Result.failure(Exception("Empty response body"))
                    }
                } else {
                    Result.failure(Exception("Request failed with HTTP ${response.code()}"))
                }
            } catch (e: SocketTimeoutException) {
                Result.failure(Exception("Request timed out. Please try again."))
            } catch (e: UnknownHostException) {
                Result.failure(Exception("No internet connection."))
            } catch (e: JsonParseException) {
                Result.failure(Exception("Received invalid response from server."))
            } catch (e: IOException) {
                Result.failure(Exception("Unable to reach server. Please try again."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun validatePredictionResponse(response: PredictionResponse): Result<PredictionResponse> {
        if (!response.riskScore.isFinite() || response.riskScore !in 0f..100f) {
            return Result.failure(Exception("Invalid risk score in response."))
        }
        if (!response.confidence.isFinite() || response.confidence !in 0f..100f) {
            return Result.failure(Exception("Invalid confidence in response."))
        }
        if (!response.predictionTimeMs.isFinite() || response.predictionTimeMs < 0f) {
            return Result.failure(Exception("Invalid prediction time in response."))
        }
        if (response.reasons.any { it.isBlank() }) {
            return Result.failure(Exception("Invalid reasons in response."))
        }

        response.aiScore?.let {
            if (!it.isFinite() || it !in 0f..100f) {
                return Result.failure(Exception("Invalid AI score in response."))
            }
        }
        response.adjustedScore?.let {
            if (!it.isFinite() || it !in 0f..100f) {
                return Result.failure(Exception("Invalid adjusted score in response."))
            }
        }

        return Result.success(response)
    }
}
