package com.example.foresight.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("/predict")
    suspend fun predictRisk(@Body request: PredictionRequest): Response<PredictionResponse>
}
