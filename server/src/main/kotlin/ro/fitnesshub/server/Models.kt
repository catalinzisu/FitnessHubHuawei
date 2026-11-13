package ro.fitnesshub.server

import kotlinx.serialization.Serializable

/**
 * Body of every error response. [error] is a fixed code the app can check; [message] is for people;
 * [details] lists every broken rule when validation fails.
 */
@Serializable
data class ErrorResponse(val error: String, val message: String, val details: List<String>? = null)

/** Body of `GET /health`. */
@Serializable
data class HealthResponse(val status: String, val version: String, val database: String)
