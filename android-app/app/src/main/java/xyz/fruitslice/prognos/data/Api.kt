package xyz.fruitslice.prognos.data

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import xyz.fruitslice.prognos.model.BookResponse
import xyz.fruitslice.prognos.model.ChartResponse
import xyz.fruitslice.prognos.model.LearningResponse
import xyz.fruitslice.prognos.model.MarketDto
import xyz.fruitslice.prognos.model.MarketsResponse
import xyz.fruitslice.prognos.model.NewsResponse
import xyz.fruitslice.prognos.model.TickerResponse
import xyz.fruitslice.prognos.model.WatchdogResponse
import java.util.concurrent.TimeUnit

interface PrognosApi {
    @GET("api.php?action=ticker")
    suspend fun ticker(): TickerResponse

    @GET("api.php?action=markets")
    suspend fun markets(@Query("limit") limit: Int = 100): MarketsResponse

    @GET("api.php?action=market")
    suspend fun market(@Query("id") id: String): MarketDto

    @GET("api.php?action=book")
    suspend fun book(
        @Query("token_id") tokenId: String,
        @Query("depth") depth: Int = 20
    ): BookResponse

    @GET("api.php?action=chart")
    suspend fun chart(@Query("market_id") marketId: String): ChartResponse

    @GET("api.php?action=news")
    suspend fun news(): NewsResponse

    @GET("api.php?action=watchdog")
    suspend fun watchdog(): WatchdogResponse

    @GET("api.php?action=learning")
    suspend fun learning(): LearningResponse

    @POST("api.php?action=record_forecasts")
    suspend fun recordForecasts(@Body body: Map<String, Any>): retrofit2.Response<okhttp3.ResponseBody>
}

fun createApi(): PrognosApi {
    val gson = GsonBuilder().setLenient().create()
    // v1.1.2: the old 20s connect / 30s read timeouts let one slow
    // call freeze a screen for half a minute. The server answers in
    // ~1s when healthy and ~7s for the markets list; 8s connect /
    // 10s read fails fast instead, and the cache covers the gap.
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(AppConfig.TRADING_BASE_URL.substringBeforeLast("/") + "/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(PrognosApi::class.java)
}
