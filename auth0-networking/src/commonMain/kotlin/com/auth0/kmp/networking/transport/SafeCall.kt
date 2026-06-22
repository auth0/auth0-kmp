package com.auth0.kmp.networking.transport

import com.auth0.kmp.core.error.TransportError
import com.auth0.kmp.core.result.Result
import com.auth0.kmp.networking.request.NetworkRequest
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Executes a single HTTP attempt and maps the outcome to a [Result].
 *
 * Never throws for a network or protocol failure: every failure is returned as a
 * typed [TransportError]. A [CancellationException] is re-thrown so coroutine
 * cancellation is preserved.
 *
 * @param client the HTTP client to execute the request with.
 * @param url the fully resolved, absolute request URL.
 * @param request the transport-level description of the request to send.
 * @param deserialize converts a successful response body into [T]. Invoked only
 *   for a 2xx response; a failure here is reported as [TransportError.Serialization].
 * @return [Result.Success] with the deserialized body on a 2xx response, otherwise
 *   [Result.Failure] with the mapped [TransportError].
 */
internal suspend fun <T> safeCall(
    client: HttpClient,
    url: String,
    request: NetworkRequest,
    deserialize: (String) -> T
): Result<T, TransportError> {
    return try {
        val response: HttpResponse = client.request(url) {
            method = HttpMethod.parse(request.method.name)
            url {
                request.query.forEach { (key, value) -> parameters.append(key, value) }
            }
            request.headers.forEach { (key, value) -> headers.append(key, value) }
            request.body?.let {
                if (request.headers.keys.none {
                        it.equals(
                            HttpHeaders.ContentType,
                            ignoreCase = true
                        )
                    }) {
                    contentType(ContentType.Application.Json)
                }
                setBody(it)
            }
        }

        when (val status = response.status.value) {
            in 200..299 -> Result.Success(deserialize(response.bodyAsText()))
            else -> Result.Failure(TransportError.Server(status, response.bodyAsText()))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        Result.Failure(TransportError.Timeout)
    } catch (e: ConnectTimeoutException) {
        Result.Failure(TransportError.Timeout)
    } catch (e: SocketTimeoutException) {
        Result.Failure(TransportError.Timeout)
    } catch (e: IOException) {
        Result.Failure(TransportError.NoInternet)
    } catch (e: SerializationException) {
        Result.Failure(TransportError.Serialization(e.message ?: "Failed to deserialize response"))
    } catch (e: Throwable) {
        Result.Failure(TransportError.Unknown(e.message))
    }
}