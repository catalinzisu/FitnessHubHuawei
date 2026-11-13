package ro.fitnesshub.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ro.fitnesshub.api.ErrorResponse
import ro.fitnesshub.api.HealthResponse
import ro.fitnesshub.api.ProfileDto
import ro.fitnesshub.api.TemplateRequest
import ro.fitnesshub.api.TemplateResponse
import ro.fitnesshub.api.WeeklyStatsResponse
import ro.fitnesshub.api.WorkoutRequest
import ro.fitnesshub.api.WorkoutResponse
import ro.fitnesshub.data.auth.TokenProvider
import ro.fitnesshub.util.AppLog
import java.io.IOException

/**
 * REST client for our Ktor server (Ktor Client + kotlinx.serialization). One function per route.
 *  - Every /api request carries the Firebase ID token: `Authorization: Bearer <token>`.
 *  - Network work runs on Dispatchers.IO, never on the main thread.
 *  - Nothing throws: every outcome is an [ApiResult] (see [ApiError]).
 * The JSON classes are the server's own, from the :shared module.
 *
 * @param baseUrl from BuildConfig.SERVER_URL (local.properties), e.g. http://10.0.2.2:8080; blank = not configured
 * @param engine OkHttp in the app; tests pass a MockEngine with made-up answers
 * @param log one line per request (method, path, status; never tokens or bodies); tests pass a no-op
 */
class FitnessApi(
    private val baseUrl: String,
    private val tokens: TokenProvider,
    engine: HttpClientEngine = OkHttp.create(),
    private val log: (String) -> Unit = { AppLog.i("Api", it) },
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    private val client = HttpClient(engine) {
        expectSuccess = false // statuses are turned into ApiError below, not into exceptions
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true }) // a newer server may add fields
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
        defaultRequest {
            url(baseUrl.trimEnd('/') + "/")
            contentType(ContentType.Application.Json)
        }
    }

    // ---- Routes ----

    /** Is the server up? No sign-in needed. */
    suspend fun health(): ApiResult<HealthResponse> = call(HttpMethod.Get, "health", signedIn = false)

    suspend fun profile(): ApiResult<ProfileDto> = call(HttpMethod.Get, "api/profile")

    suspend fun saveProfile(profile: ProfileDto): ApiResult<ProfileDto> =
        call(HttpMethod.Put, "api/profile") { setBody(profile) }

    suspend fun templates(): ApiResult<List<TemplateResponse>> = call(HttpMethod.Get, "api/templates")

    suspend fun createTemplate(template: TemplateRequest): ApiResult<TemplateResponse> =
        call(HttpMethod.Post, "api/templates") { setBody(template) }

    suspend fun updateTemplate(id: Long, template: TemplateRequest): ApiResult<TemplateResponse> =
        call(HttpMethod.Put, "api/templates/$id") { setBody(template) }

    suspend fun deleteTemplate(id: Long): ApiResult<Unit> = call(HttpMethod.Delete, "api/templates/$id")

    /** Workouts started at or after [since] (epoch ms), or all of them. */
    suspend fun workouts(since: Long? = null): ApiResult<List<WorkoutResponse>> =
        call(HttpMethod.Get, "api/workouts") { since?.let { parameter("since", it) } }

    /** Safe to repeat: the server keeps one copy per [WorkoutRequest.clientId]. */
    suspend fun uploadWorkout(workout: WorkoutRequest): ApiResult<WorkoutResponse> =
        call(HttpMethod.Post, "api/workouts") { setBody(workout) }

    suspend fun deleteWorkout(id: Long): ApiResult<Unit> = call(HttpMethod.Delete, "api/workouts/$id")

    suspend fun weeklyStats(weeks: Int, timeZone: String): ApiResult<WeeklyStatsResponse> =
        call(HttpMethod.Get, "api/stats/weekly") {
            parameter("weeks", weeks)
            parameter("tz", timeZone)
        }

    // ---- One place for tokens, threads and errors ----

    /**
     * Sends [method] [path] (relative to the server address) and turns whatever happens into an
     * [ApiResult]. [body] adds a JSON body or query parameters. With [signedIn], the Firebase ID
     * token is attached; a 401 can mean it expired a moment ago, so it is retried once with a fresh one.
     */
    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: String,
        signedIn: Boolean = true,
        crossinline body: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext ApiResult.Failure(ApiError.NotConfigured)
        try {
            var token = if (signedIn) tokens.idToken(forceRefresh = false) ?: return@withContext ApiResult.Failure(ApiError.NotSignedIn) else null
            var response = client.request(path) {
                this.method = method
                if (token != null) bearerAuth(token)
                body()
            }
            if (response.status == HttpStatusCode.Unauthorized && signedIn) {
                token = tokens.idToken(forceRefresh = true) ?: return@withContext ApiResult.Failure(ApiError.NotSignedIn)
                response = client.request(path) {
                    this.method = method
                    bearerAuth(token)
                    body()
                }
            }
            log("${method.value} /$path -> ${response.status.value}")
            toResult<T>(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            fail(ApiError.Timeout, e)
        } catch (e: IOException) {
            // No network, server not running, unknown host, connection refused or reset.
            fail(ApiError.Offline, e)
        } catch (e: Exception) {
            // E.g. a body that doesn't match the expected JSON.
            fail(ApiError.Unexpected, e)
        }
    }

    private suspend inline fun <reified T> toResult(response: HttpResponse): ApiResult<T> {
        val status = response.status
        return when {
            status.isSuccess() ->
                @Suppress("UNCHECKED_CAST")
                ApiResult.Success(if (T::class == Unit::class) Unit as T else response.body<T>())
            status == HttpStatusCode.BadRequest -> {
                val error = runCatching { response.body<ErrorResponse>() }.getOrNull()
                ApiResult.Failure(ApiError.Invalid(error?.details ?: listOfNotNull(error?.message)))
            }
            status == HttpStatusCode.Unauthorized -> ApiResult.Failure(ApiError.Unauthorized)
            status == HttpStatusCode.NotFound -> ApiResult.Failure(ApiError.NotFound)
            else -> ApiResult.Failure(ApiError.Server(status.value))
        }
    }

    private fun fail(error: ApiError, e: Exception): ApiResult.Failure {
        log("Request failed: $error (${e::class.simpleName})")
        return ApiResult.Failure(error)
    }
}
