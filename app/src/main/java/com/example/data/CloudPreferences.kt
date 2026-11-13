package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.cloud.CloudSettings
import com.example.cloud.CloudSigningMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cloudDataStore by preferencesDataStore(name = "dex2c_cloud_prefs")

class CloudPreferences(private val context: Context) {

  private object Keys {
    val CLIENT_ID = stringPreferencesKey("github_client_id")
    val ACCESS_TOKEN = stringPreferencesKey("github_access_token")
    val USERNAME = stringPreferencesKey("github_username")
    val REPO_OWNER = stringPreferencesKey("repo_owner")
    val REPO_NAME = stringPreferencesKey("repo_name")
    val DEFAULT_BRANCH = stringPreferencesKey("default_branch")
    val PRIVATE_REPO = booleanPreferencesKey("private_repo")
    val WORKFLOW_INITIALIZED = booleanPreferencesKey("workflow_initialized")
    val NDK_VERSION = stringPreferencesKey("ndk_version")
    val JDK_VERSION = stringPreferencesKey("jdk_version")
    val SIGNING_MODE = stringPreferencesKey("signing_mode")
    val POLL_INTERVAL = intPreferencesKey("poll_interval_seconds")
    val MAX_UPLOAD_MB = intPreferencesKey("max_direct_upload_mb")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val LAST_NOTIFIED_RUN_ID = longPreferencesKey("last_notified_run_id")
  }

  val settingsFlow: Flow<CloudSettings> =
    context.cloudDataStore.data.map { prefs ->
      val defaults = CloudSettings()
      CloudSettings(
        githubClientId = prefs[Keys.CLIENT_ID] ?: defaults.githubClientId,
        accessToken = prefs[Keys.ACCESS_TOKEN] ?: defaults.accessToken,
        githubUsername = prefs[Keys.USERNAME] ?: defaults.githubUsername,
        repoOwner = prefs[Keys.REPO_OWNER] ?: defaults.repoOwner,
        repoName = prefs[Keys.REPO_NAME] ?: defaults.repoName,
        defaultBranch = prefs[Keys.DEFAULT_BRANCH] ?: defaults.defaultBranch,
        privateRepo = prefs[Keys.PRIVATE_REPO] ?: defaults.privateRepo,
        workflowInitialized = prefs[Keys.WORKFLOW_INITIALIZED] ?: defaults.workflowInitialized,
        ndkVersion = prefs[Keys.NDK_VERSION] ?: defaults.ndkVersion,
        jdkVersion = prefs[Keys.JDK_VERSION] ?: defaults.jdkVersion,
        signingMode =
          prefs[Keys.SIGNING_MODE]?.let { runCatching { CloudSigningMode.valueOf(it) }.getOrNull() }
            ?: defaults.signingMode,
        pollIntervalSeconds = prefs[Keys.POLL_INTERVAL] ?: defaults.pollIntervalSeconds,
        maxDirectUploadMb = prefs[Keys.MAX_UPLOAD_MB] ?: defaults.maxDirectUploadMb,
        notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: defaults.notificationsEnabled,
        lastNotifiedRunId = prefs[Keys.LAST_NOTIFIED_RUN_ID] ?: defaults.lastNotifiedRunId,
      )
    }

  suspend fun saveSettings(settings: CloudSettings) {
    context.cloudDataStore.edit { prefs ->
      prefs[Keys.CLIENT_ID] = settings.githubClientId
      prefs[Keys.ACCESS_TOKEN] = settings.accessToken
      prefs[Keys.USERNAME] = settings.githubUsername
      prefs[Keys.REPO_OWNER] = settings.repoOwner
      prefs[Keys.REPO_NAME] = settings.repoName
      prefs[Keys.DEFAULT_BRANCH] = settings.defaultBranch
      prefs[Keys.PRIVATE_REPO] = settings.privateRepo
      prefs[Keys.WORKFLOW_INITIALIZED] = settings.workflowInitialized
      prefs[Keys.NDK_VERSION] = settings.ndkVersion
      prefs[Keys.JDK_VERSION] = settings.jdkVersion
      prefs[Keys.SIGNING_MODE] = settings.signingMode.name
      prefs[Keys.POLL_INTERVAL] = settings.pollIntervalSeconds
      prefs[Keys.MAX_UPLOAD_MB] = settings.maxDirectUploadMb
      prefs[Keys.NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
      prefs[Keys.LAST_NOTIFIED_RUN_ID] = settings.lastNotifiedRunId
    }
  }

  suspend fun markRunNotified(runId: Long) {
    context.cloudDataStore.edit { prefs ->
      prefs[Keys.LAST_NOTIFIED_RUN_ID] = runId
    }
  }

  suspend fun clearAuthSession() {
    context.cloudDataStore.edit { prefs ->
      prefs[Keys.ACCESS_TOKEN] = ""
      prefs[Keys.USERNAME] = ""
      prefs[Keys.WORKFLOW_INITIALIZED] = false
    }
  }
}
