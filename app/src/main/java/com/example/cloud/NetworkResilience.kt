package com.example.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OkHttp DNS resolver resilient to transient Android system DNS failures (e.g. "Unable to resolve
 * host api.github.com" during cellular/Wi-Fi handoffs or broken IPv6 DNS servers).
 *
 * Order of resolution:
 * 1. System DNS (with IPv4 prioritized first to prevent stalled IPv6 sockets)
 * 2. Quick second System DNS retry after brief pause
 * 3. Direct IP-literal DNS-over-HTTPS fallback (1.1.1.1 & 8.8.8.8)
 */
class ResilientGitHubDns(
  private val dohClient: OkHttpClient = defaultDohClient(),
) : Dns {

  override fun lookup(hostname: String): List<InetAddress> {
    val trimmed = hostname.trim()
    if (trimmed.isEmpty()) throw UnknownHostException("Empty hostname")

    // Never hit remote DoH for localhost or numeric IP literals
    if (trimmed.equals("localhost", ignoreCase = true) || isNumericIpLiteral(trimmed)) {
      return Dns.SYSTEM.lookup(trimmed)
    }

    val firstError: UnknownHostException? =
      try {
        val systemAddrs = Dns.SYSTEM.lookup(trimmed)
        if (systemAddrs.isNotEmpty()) return prioritizeIpv4(systemAddrs)
        null
      } catch (e: UnknownHostException) {
        e
      }

    // Brief pause and second system DNS attempt for transient Android netd blips
    try {
      Thread.sleep(150L)
      val retryAddrs = Dns.SYSTEM.lookup(trimmed)
      if (retryAddrs.isNotEmpty()) return prioritizeIpv4(retryAddrs)
    } catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
    } catch (_: UnknownHostException) {
      // Fall through to DoH
    }

    val dohAddrs = resolveViaDoh(trimmed)
    if (dohAddrs.isNotEmpty()) {
      return dohAddrs
    }

    throw firstError ?: UnknownHostException("Unable to resolve host \"$trimmed\"")
  }

  private fun resolveViaDoh(hostname: String): List<InetAddress> {
    val encoded = runCatching { URLEncoder.encode(hostname, "UTF-8") }.getOrDefault(hostname)
    val endpoints =
      listOf(
        "https://1.1.1.1/dns-query?name=$encoded&type=A",
        "https://8.8.8.8/resolve?name=$encoded&type=A",
      )

    for (url in endpoints) {
      val resolved =
        runCatching {
          val req =
            Request.Builder()
              .url(url)
              .header("Accept", "application/dns-json")
              .get()
              .build()
          dohClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val body = resp.body?.string().orEmpty()
            parseDohARecords(body)
          }
        }.getOrDefault(emptyList())
      if (resolved.isNotEmpty()) {
        return resolved
      }
    }
    return emptyList()
  }

  companion object {
    internal fun prioritizeIpv4(addresses: List<InetAddress>): List<InetAddress> {
      val v4 = addresses.filterIsInstance<Inet4Address>()
      val other = addresses.filterNot { it is Inet4Address }
      return v4 + other
    }

    internal fun parseDohARecords(jsonBody: String): List<InetAddress> {
      if (jsonBody.isBlank()) return emptyList()
      val root = runCatching { JsonMini.parseObject(jsonBody) }.getOrNull() ?: return emptyList()
      val answers = root["Answer"] as? List<*> ?: return emptyList()
      return answers.mapNotNull { entry ->
        val obj = entry as? Map<*, *> ?: return@mapNotNull null
        val typeNum = (obj["type"] as? Number)?.toInt()
        if (typeNum != 1) return@mapNotNull null
        val ipStr = (obj["data"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        if (!isIpv4Literal(ipStr)) return@mapNotNull null
        runCatching { InetAddress.getByName(ipStr) }.getOrNull()
      }
    }

    private fun isIpv4Literal(host: String): Boolean {
      val parts = host.split('.')
      if (parts.size != 4) return false
      return parts.all { part ->
        val n = part.toIntOrNull() ?: return@all false
        n in 0..255
      }
    }

    private fun isNumericIpLiteral(host: String): Boolean =
      isIpv4Literal(host) || (host.contains(':') && host.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' })

    private fun defaultDohClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()
  }
}

object NetworkResilience {

  fun isNetworkAvailable(context: Context): Boolean {
    return runCatching {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
      val network = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(network) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
  }

  fun isTransientNetworkError(t: Throwable): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
      when (cur) {
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException,
        is NoRouteToHostException,
        is SocketException,
        is SSLException -> return true
        is IOException -> {
          val msg = cur.message?.lowercase().orEmpty()
          if (
            msg.contains("unable to resolve host") ||
            msg.contains("no address associated with hostname") ||
            msg.contains("failed to connect") ||
            msg.contains("timeout") ||
            msg.contains("timed out") ||
            msg.contains("connection reset") ||
            msg.contains("software caused connection abort") ||
            msg.contains("network is unreachable") ||
            msg.contains("broken pipe") ||
            msg.contains("unexpected end of stream")
          ) {
            return true
          }
        }
      }
      cur = cur.cause
    }
    return false
  }

  fun formatUserFacingError(t: Throwable, isOnline: Boolean): String {
    if (!isOnline) {
      return "No internet connection. Reconnect Wi-Fi or mobile data and tap Retry."
    }
    val msg = t.message.orEmpty()
    val isDnsFailure =
      generateSequence(t) { it.cause }.any { it is UnknownHostException } ||
        msg.contains("Unable to resolve host", ignoreCase = true) ||
        msg.contains("No address associated with hostname", ignoreCase = true)
    if (isDnsFailure) {
      return "Unable to reach GitHub (DNS lookup failed). Check internet or VPN and tap Retry."
    }
    val isTimeout = generateSequence(t) { it.cause }.any { it is SocketTimeoutException }
    if (isTimeout) {
      return "Connection to GitHub timed out. Tap Retry to try again."
    }
    return msg.takeIf { it.isNotBlank() } ?: "Network error while contacting GitHub."
  }

  suspend fun <T> retryTransient(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1_000L,
    maxDelayMs: Long = 6_000L,
    onRetry: ((attempt: Int, error: Throwable, nextDelayMs: Long) -> Unit)? = null,
    block: suspend () -> T,
  ): T {
    val safeAttempts = maxAttempts.coerceAtLeast(1)
    var currentDelay = initialDelayMs.coerceAtLeast(100L)
    var lastError: Throwable? = null

    for (attempt in 1..safeAttempts) {
      try {
        return block()
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        lastError = t
        if (attempt >= safeAttempts || !isTransientNetworkError(t)) {
          throw t
        }
        onRetry?.invoke(attempt, t, currentDelay)
        delay(currentDelay)
        currentDelay = (currentDelay * 2L).coerceAtMost(maxDelayMs)
      }
    }
    throw lastError ?: IllegalStateException("Retry loop exited unexpectedly")
  }
}
