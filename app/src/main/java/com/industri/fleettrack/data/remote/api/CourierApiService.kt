package com.industri.fleettrack.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class ManifestOrderDto(
    @SerializedName("order_id")
    val orderId: String? = null,

    @SerializedName("tracking_number")
    val trackingNumber: String? = null,

    @SerializedName("recipient_name")
    val recipientName: String? = null,

    @SerializedName("recipient_phone")
    val recipientPhone: String? = null,

    @SerializedName("destination_address")
    val destinationAddress: String? = null,

    @SerializedName("cod_amount")
    val codAmount: Double? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("failure_reason")
    val failureReason: String? = null
)

data class StatusUpdateResponseDto(
    @SerializedName("success")
    val success: Boolean? = null,

    @SerializedName("message")
    val message: String? = null
)

interface CourierApiService {

    @GET("api/v1/courier/manifest/today")
    suspend fun getTodayManifest(): Response<List<ManifestOrderDto>>

    @POST("api/v1/courier/deliveries/{orderId}/status")
    suspend fun updateDeliveryStatus(
        @Path("orderId") orderId: String,
        @Query("status") status: String
    ): Response<StatusUpdateResponseDto>

    companion object {
        const val BASE_URL = "https://mock-api.fleettrack.id/"
    }
}