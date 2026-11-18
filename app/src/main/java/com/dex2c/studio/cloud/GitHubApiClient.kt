package com.dex2c.studio.cloud

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.ByteString.Companion.toByteString

sealed interface TokenPollResult {
  data class Success(val accessToken: String) : TokenPollResult
  data object Pending : TokenPollResult
  data class SlowDown(val nextIntervalSec: Int) : TokenPollResult
  data object Expired : TokenPollResult
  data object Denied : TokenPollResult
  data class Failure(val message: String) : TokenPollResult
}

data class GitHubRepoInfo(
  val owner: String,
  val name: String,
  val fullName: String,
  val htmlUrl: String,
  val isPrivate: Boolean,
  val defaultBranch: String,
)

class GitHubApiClient(
  private val oauthBaseUrl: String = "https://github.com",
  private val apiBaseUrl: String = "https://api.github.com",
  private val isDohEnabled: () -> Boolean = { false },
  private val httpClient: OkHttpClient = defaultClient(isDohEnabled),
) {

  private val noRedirectClient: OkHttpClient by lazy {
    httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
  }

  fun requestDeviceCode(
    clientId: String,
    scope: String = "repo workflow",
  ): DeviceCodeResponse {
    val cleanId = clientId.trim()
    require(cleanId.isNotEmpty()) { "GitHub OAuth Client ID is required in Settings." }

    val body =
      FormBody.Builder()
        .add("client_id", cleanId)
        .add("scope", scope)
        .build()

    val req =
      Request.Builder()
        .url("$oauthBaseUrl/login/device/code")
        .header("Accept", "application/json")
        .post(body)
        .build()

    httpClient.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        throw IOException("GitHub Device Code error (${resp.code}): ${extractErrorMessage(text)}")
      }
      val map = JsonMini.parseObject(text)
      val err = map["error"] as? String
      if (!err.isNullOrBlank()) {
        throw IOException((map["error_description"] as? String) ?: err)
      }
      return DeviceCodeResponse(
        deviceCode = (map["device_code"] as? String).orEmpty(),
        userCode = (map["user_code"] as? String).orEmpty(),
        verificationUri =
          (map["verification_uri"] as? String) ?: "https://github.com/login/device",
        expiresInSec = ((map["expires_in"] as? Number)?.toInt() ?: 900),
        intervalSec = ((map["interval"] as? Number)?.toInt() ?: 5).coerceAtLeast(5),
      )
    }
  }

  fun pollAccessTokenOnce(
    clientId: String,
    deviceCode: String,
    currentIntervalSec: Int,
  ): TokenPollResult {
    val body =
      FormBody.Builder()
        .add("client_id", clientId.trim())
        .add("device_code", deviceCode)
        .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
        .build()

    val req =
      Request.Builder()
        .url("$oauthBaseUrl/login/oauth/access_token")
        .header("Accept", "application/json")
        .post(body)
        .build()

    httpClient.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        return TokenPollResult.Failure("OAuth token request failed (${resp.code}).")
      }
      val map = JsonMini.parseObject(text)
      val accessToken = map["access_token"] as? String
      if (!accessToken.isNullOrBlank()) {
        return TokenPollResult.Success(accessToken)
      }
      return when (val err = map["error"] as? String) {
        "authorization_pending" -> TokenPollResult.Pending
        "slow_down" -> {
          val interval = (map["interval"] as? Number)?.toInt() ?: (currentIntervalSec + 5)
          TokenPollResult.SlowDown(interval)
        }
        "expired_token" -> TokenPollResult.Expired
        "access_denied" -> TokenPollResult.Denied
        else ->
          TokenPollResult.Failure(
            (map["error_description"] as? String) ?: err ?: "Unknown OAuth error"
          )
      }
    }
  }

  fun getAuthenticatedUsername(token: String): String {
    val req = apiRequest(token, "$apiBaseUrl/user").get().build()
    httpClient.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        throw IOException("GitHub /user failed (${resp.code}): ${extractErrorMessage(text)}")
      }
      val map = JsonMini.parseObject(text)
      return (map["login"] as? String)
        ?: throw IOException("GitHub /user response missing login.")
    }
  }

  fun getRepository(token: String, owner: String, repo: String): GitHubRepoInfo? {
    val req = apiRequest(token, "$apiBaseUrl/repos/$owner/$repo").get().build()
    httpClient.newCall(req).execute().use { resp ->
      if (resp.code == 404) return null
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        throw IOException("GitHub repo lookup failed (${resp.code}): ${extractErrorMessage(text)}")
      }
      return parseRepoInfo(JsonMini.parseObject(text))
    }
  }

  fun createRepository(
    token: String,
    name: String,
    isPrivate: Boolean,
    description: String = "Dex2C Studio automated cloud APK build workspace",
  ): GitHubRepoInfo {
    val payload =
      JsonMini.objectJson(
        listOf(
          "name" to name.trim(),
          "description" to description,
          "private" to isPrivate,
          "auto_init" to true,
        )
      )
    val req =
      apiRequest(token, "$apiBaseUrl/user/repos")
        .post(payload.toRequestBody(JSON_MEDIA))
        .build()

    httpClient.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        throw IOException("Failed to create repository (${resp.code}): ${extractErrorMessage(text)}")
      }
      return parseRepoInfo(JsonMini.parseObject(text))
    }
  }

  fun getContentSha(
    token: String,
    owner: String,
    repo: String,
    path: String,
    branch: String,
  ): String? {
    val cleanPath = path.trimStart('/')
    val req =
      apiRequest(token, "$apiBaseUrl/repos/$owner/$repo/contents/$cleanPath?ref=$branch")
        .get()
        .build()
    httpClient.newCall(req).execute().use { resp ->
      if (resp.code == 404) return null
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) return null
      val map = runCatching { JsonMini.parseObject(text) }.getOrNull() ?: return null
      return map["sha"] as? String
    }
  }

  fun upsertRepoFile(
    token: String,
    owner: String,
    repo: String,
    path: String,
    branch: String,
    commitMessage: String,
    contentBytes: ByteArray,
    onUploadProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
  ) {
    val cleanPath = path.trimStart('/')
    val existingSha = getContentSha(token, owner, repo, cleanPath, branch)
    val base64Content = contentBytes.toByteString().base64()
    val fields =
      buildList {
        add("message" to commitMessage)
        add("content" to base64Content)
        add("branch" to branch)
        if (!existingSha.isNullOrBlank()) {
          add("sha" to existingSha)
        }
      }
    val payloadBytes = JsonMini.objectJson(fields).toByteArray(Charsets.UTF_8)
    val requestBody =
      if (onUploadProgress == null) {
        payloadBytes.toRequestBody(JSON_MEDIA)
      } else {
        ProgressByteRequestBody(payloadBytes, JSON_MEDIA, onUploadProgress)
      }
    val req =
      apiRequest(token, "$apiBaseUrl/repos/$owner/$repo/contents/$cleanPath")
        .put(requestBody)
        .build()

    httpClient.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        throw IOException(
          "Failed to commit '$cleanPath' (${resp.code}): ${extractErrorMessage(text)}"
        )
      }
    }
  }

  fun dispatchWorkflow(
    token: String,
    owner: String,
    repo: String,
    workflowFile: String = "dex2c.yml",
    ref: String,
    inputs: Map<String, String>,
  ) {
    val payload =
      JsonMini.objectJson(
        listOf(
          "ref" to ref,
          "inputs" to inputs,
        )
      )
    val req =
      apiRequest(token, "$apiBaseUrl/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches")
        .post(payload.toRequestBody(JSON_MEDIA))
        .build()

    httpClient.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        throw IOException("Workflow dispatch failed (${resp.code}): ${extractErrorMessage(text)}")
      }
    }
  }

  fun listWorkflowRuns(
    token: String,
    owner: String,
    repo: String,
    workflowFile: String = "dex2c.yml",
  ): List<GitHubWorkflowRunSummary> {
    val req =
      apiRequest(token, "$apiBaseUrl/repos/$owner/$repo/actions/workflows/$workflowFile/runs?per_page=8")
        .get()
        .build()
    httpClient.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        throw IOException("Failed to fetch workflow runs (${resp.code}): ${extractErrorMessage(text)}")
      }
      val map = JsonMini.parseObject(text)
      val runs = map["workflow_runs"] as? List<*> ?: return emptyList()
      return runs.mapNotNull { item ->
        val obj = item as? Map<*, *> ?: return@mapNotNull null
        GitHubWorkflowRunSummary(
          id = (obj["id"] as? Number)?.toLong() ?: return@mapNotNull null,
          runNumber = (obj["run_number"] as? Number)?.toInt() ?: 0,
          status = (obj["status"] as? String) ?: "queued",
          conclusion = obj["conclusion"] as? String,
          htmlUrl = (obj["html_url"] as? String).orEmpty(),
          createdAt = (obj["created_at"] as? String).orEmpty(),
          runStartedAtMillis = CloudTimeUtil.parseIsoUtcMillis(obj["run_started_at"] as? String),
          updatedAtMillis = CloudTimeUtil.parseIsoUtcMillis(obj["updated_at"] as? String),
        )
      }
    }
  }

  fun getRunJobTelemetry(
    token: String,
    owner: String,
    repo: String,
    runId: Long,
  ): GitHubJobTelemetry {
    val req =
      apiRequest(token, "$apiBaseUrl/repos/$owner/$repo/actions/runs/$runId/jobs")
        .get()
        .build()
    httpClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) return GitHubJobTelemetry()
      val text = resp.body?.string().orEmpty()
      val map = runCatching { JsonMini.parseObject(text) }.getOrNull() ?: return GitHubJobTelemetry()
      val jobs = map["jobs"] as? List<*> ?: return GitHubJobTelemetry()
      val firstJob = jobs.firstOrNull() as? Map<*, *> ?: return GitHubJobTelemetry()
      val stepsRaw = firstJob["steps"] as? List<*> ?: emptyList<Any?>()
      val steps =
        stepsRaw.mapNotNull { s ->
          val obj = s as? Map<*, *> ?: return@mapNotNull null
          GitHubWorkflowStep(
            name = (obj["name"] as? String) ?: "Step",
            status = (obj["status"] as? String) ?: "queued",
            conclusion = obj["conclusion"] as? String,
          )
        }
      return GitHubJobTelemetry(
        jobName = (firstJob["name"] as? String) ?: "dex2c-apk",
        jobStatus = (firstJob["status"] as? String) ?: "queued",
        startedAtMillis = CloudTimeUtil.parseIsoUtcMillis(firstJob["started_at"] as? String),
        completedAtMillis = CloudTimeUtil.parseIsoUtcMillis(firstJob["completed_at"] as? String),
        steps = steps,
      )
    }
  }

  fun getRunSteps(
    token: String,
    owner: String,
    repo: String,
    runId: Long,
  ): List<GitHubWorkflowStep> = getRunJobTelemetry(token, owner, repo, runId).steps

  fun listRunArtifacts(
    token: String,
    owner: String,
    repo: String,
    runId: Long,
  ): List<GitHubArtifactItem> {
    val req =
      apiRequest(token, "$apiBaseUrl/repos/$owner/$repo/actions/runs/$runId/artifacts")
        .get()
        .build()
    httpClient.newCall(req).execute().use { resp ->
      val text = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        throw IOException("Failed to fetch artifacts (${resp.code}): ${extractErrorMessage(text)}")
      }
      val map = JsonMini.parseObject(text)
      val artifacts = map["artifacts"] as? List<*> ?: return emptyList()
      return artifacts.mapNotNull { a ->
        val obj = a as? Map<*, *> ?: return@mapNotNull null
        GitHubArtifactItem(
          id = (obj["id"] as? Number)?.toLong() ?: return@mapNotNull null,
          name = (obj["name"] as? String) ?: "artifact",
          sizeInBytes = (obj["size_in_bytes"] as? Number)?.toLong() ?: 0L,
          archiveDownloadUrl = (obj["archive_download_url"] as? String).orEmpty(),
          createdAt = (obj["created_at"] as? String).orEmpty(),
          expired = (obj["expired"] as? Boolean) ?: false,
        )
      }
    }
  }

  fun downloadArtifactZip(
    token: String,
    archiveDownloadUrl: String,
    expectedTotalBytes: Long = -1L,
    onDownloadProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
  ): ByteArray {
    val req = apiRequest(token, archiveDownloadUrl).get().build()
    noRedirectClient.newCall(req).execute().use { resp ->
      if (resp.code in 300..399) {
        val location =
          resp.header("Location")
            ?: throw IOException("Artifact redirect missing Location header.")
        val s3Req = Request.Builder().url(location).get().build()
        httpClient.newCall(s3Req).execute().use { s3Resp ->
          if (!s3Resp.isSuccessful) {
            throw IOException("Failed downloading artifact payload (${s3Resp.code}).")
          }
          val body = s3Resp.body ?: throw IOException("Empty artifact payload.")
          return readBodyWithProgress(body, expectedTotalBytes, onDownloadProgress)
        }
      }
      if (!resp.isSuccessful) {
        throw IOException("Artifact download failed (${resp.code}).")
      }
      val body = resp.body ?: throw IOException("Empty artifact response.")
      return readBodyWithProgress(body, expectedTotalBytes, onDownloadProgress)
    }
  }

  private fun readBodyWithProgress(
    body: ResponseBody,
    expectedTotalBytes: Long,
    onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)?,
  ): ByteArray {
    if (onProgress == null) {
      return body.bytes()
    }
    val headerLen = body.contentLength()
    val total =
      when {
        headerLen > 0L -> headerLen
        expectedTotalBytes > 0L -> expectedTotalBytes
        else -> -1L
      }
    val out = ByteArrayOutputStream(if (total > 0L) total.toInt().coerceAtMost(8 * 1024 * 1024) else 32_768)
    val buffer = ByteArray(16_384)
    var totalRead = 0L
    var lastEmitBytes = 0L
    var lastEmitTime = 0L
    onProgress(0L, total)
    body.byteStream().use { input ->
      while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        out.write(buffer, 0, read)
        totalRead += read
        val now = System.currentTimeMillis()
        if (totalRead - lastEmitBytes >= 32_768L || now - lastEmitTime >= 120L) {
          lastEmitBytes = totalRead
          lastEmitTime = now
          onProgress(totalRead, if (total > 0L) total.coerceAtLeast(totalRead) else -1L)
        }
      }
    }
    val finalTotal = if (total > 0L) total.coerceAtLeast(totalRead) else totalRead
    onProgress(totalRead, finalTotal)
    return out.toByteArray()
  }

  private class ProgressByteRequestBody(
    private val bytes: ByteArray,
    private val mediaType: MediaType,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit,
  ) : RequestBody() {
    override fun contentType(): MediaType = mediaType
    override fun contentLength(): Long = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
      val total = bytes.size.toLong()
      var offset = 0
      var lastEmitBytes = 0L
      var lastEmitTime = 0L
      onProgress(0L, total)
      while (offset < bytes.size) {
        val chunk = (bytes.size - offset).coerceAtMost(16_384)
        sink.write(bytes, offset, chunk)
        offset += chunk
        val written = offset.toLong()
        val now = System.currentTimeMillis()
        if (written == total || written - lastEmitBytes >= 32_768L || now - lastEmitTime >= 120L) {
          lastEmitBytes = written
          lastEmitTime = now
          onProgress(written, total)
        }
      }
    }
  }

  fun extractArtifactPayload(
    zipBytes: ByteArray,
    artifactName: String = "",
    defaultBaseName: String = "dex2c",
  ): CloudDownloadedApk {
    val cleanBase = CloudNamingConvention.sanitizeBaseName(
      raw = defaultBaseName.ifBlank {
        artifactName.removeSuffix("-signed-apk").removeSuffix("-build-log")
      },
      fallback = "dex2c",
    )
    val isLogTarget = artifactName.contains("log", ignoreCase = true)

    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
      var firstLogBytes: ByteArray? = null
      var firstLogName: String? = null
      var firstApkBytes: ByteArray? = null
      var firstApkName: String? = null

      while (true) {
        val entry = zis.nextEntry ?: break
        if (!entry.isDirectory) {
          val name = entry.name.substringAfterLast('/')
          if (name.endsWith(".apk", ignoreCase = true) && firstApkBytes == null) {
            firstApkBytes = zis.readBytes()
            firstApkName = name
          } else if ((name.endsWith(".log", ignoreCase = true) || name.endsWith(".txt", ignoreCase = true)) && firstLogBytes == null) {
            firstLogBytes = zis.readBytes()
            firstLogName = name
          }
        }
        zis.closeEntry()
      }

      if (isLogTarget && firstLogBytes != null) {
        val resolvedName = if (firstLogName != null && firstLogName.contains(cleanBase)) {
          firstLogName
        } else {
          CloudNamingConvention.buildLogFileName(cleanBase)
        }
        return CloudDownloadedApk(
          fileName = resolvedName,
          bytes = firstLogBytes,
          isZipFallback = false,
        )
      }

      if (firstApkBytes != null) {
        val resolvedName = if (firstApkName != null && firstApkName.contains(cleanBase) && !firstApkName.startsWith("dex2c-signed-output")) {
          firstApkName
        } else {
          CloudNamingConvention.signedApkFileName(cleanBase)
        }
        return CloudDownloadedApk(
          fileName = resolvedName,
          bytes = firstApkBytes,
          isZipFallback = false,
        )
      }

      if (firstLogBytes != null) {
        val resolvedName = if (firstLogName != null && firstLogName.contains(cleanBase)) {
          firstLogName
        } else {
          CloudNamingConvention.buildLogFileName(cleanBase)
        }
        return CloudDownloadedApk(
          fileName = resolvedName,
          bytes = firstLogBytes,
          isZipFallback = false,
        )
      }
    }

    val fallbackExt = if (isLogTarget) "log.zip" else "artifact.zip"
    return CloudDownloadedApk(
      fileName = "${cleanBase}_$fallbackExt",
      bytes = zipBytes,
      isZipFallback = true,
    )
  }

  fun extractApkFromArtifactZip(zipBytes: ByteArray, defaultBaseName: String = "dex2c-signed-output"): CloudDownloadedApk {
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
      while (true) {
        val entry = zis.nextEntry ?: break
        if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
          val fileName = entry.name.substringAfterLast('/')
          return CloudDownloadedApk(
            fileName = fileName,
            bytes = zis.readBytes(),
            isZipFallback = false,
          )
        }
        zis.closeEntry()
      }
    }
    return CloudDownloadedApk(
      fileName = "${defaultBaseName}_artifact.zip",
      bytes = zipBytes,
      isZipFallback = true,
    )
  }

  private fun parseRepoInfo(map: Map<String, Any?>): GitHubRepoInfo {
    val ownerObj = map["owner"] as? Map<*, *>
    val owner = (ownerObj?.get("login") as? String).orEmpty()
    val name = (map["name"] as? String).orEmpty()
    return GitHubRepoInfo(
      owner = owner,
      name = name,
      fullName = (map["full_name"] as? String) ?: "$owner/$name",
      htmlUrl = (map["html_url"] as? String).orEmpty(),
      isPrivate = (map["private"] as? Boolean) ?: true,
      defaultBranch = (map["default_branch"] as? String) ?: "main",
    )
  }

  private fun apiRequest(token: String, url: String): Request.Builder =
    Request.Builder()
      .url(url)
      .header("Authorization", "Bearer $token")
      .header("Accept", "application/vnd.github+json")
      .header("X-GitHub-Api-Version", "2022-11-28")

  private fun extractErrorMessage(body: String): String =
    runCatching {
      val map = JsonMini.parseObject(body)
      (map["message"] as? String) ?: (map["error_description"] as? String) ?: body.take(180)
    }.getOrDefault(body.take(180))

  companion object {
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val sharedConnectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)

    fun defaultClient(isDohEnabled: () -> Boolean = { false }): OkHttpClient =
      OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .dns(ResilientGitHubDns(isDohEnabled = isDohEnabled))
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
  }
}
