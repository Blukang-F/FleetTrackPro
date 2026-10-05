package com.industri.fleettrack.data.remote

import com.industri.fleettrack.BuildConfig
import com.industri.fleettrack.data.remote.api.CourierApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Pembuatan Retrofit/OkHttp singleton. Logging interceptor hanya dipasang
 * pada BuildConfig.DEBUG sehingga data sensitif tidak bocor di log release.
 */
object NetworkModule {

    val baseUrl: String
        get() = BuildConfig.API_BASE_URL

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.ENABLE_HTTP_LOGGING) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (BuildConfig.ENABLE_HTTP_LOGGING) {
                    addInterceptor(loggingInterceptor)
                }
            }
            .build()
    }

    val apiService: CourierApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CourierApiService::class.java)
    }
}