# Security Audit Report — Dex2c-Android

**Audit Target**: `com.dex2c.studio` (Dex2c-Android)  
**Date**: 2026-11-20  
**Methodology**: Cloudflare Security Audit Skill (Defensive Source-First Review)  
**Audit Status**: Complete  

---

## Executive Summary

A targeted security review of the Dex2c-Android codebase was conducted using the newly installed `security-audit` skill. The audit focused on:
1. **Cloud CI & Workflow Generation**: Interaction with GitHub Actions, secret handling, and dynamic workflow generation.
2. **Credential Management & Storage**: OAuth tokens, AndroidKeyStore encryption, and SharedPreferences / DataStore persistence.
3. **Android Application Attack Surface**: Exported components, intent filters, and backup policies.

### Summary of Findings

| ID | Title | Severity | Verdict | File |
|---|---|---|---|---|
| `DEX2C-SEC-01` | GitHub Actions workflow inline script injection via unquoted user inputs | **High** | Confirmed | `app/src/main/java/com/dex2c/studio/cloud/Dex2cWorkflowGenerator.kt` |
| `DEX2C-SEC-02` | Insecure fallback to static hardcoded AES key on AndroidKeyStore exception | **Medium** | Confirmed | `app/src/main/java/com/dex2c/studio/data/TokenEncryptor.kt` |

---

## Key Findings & Impact Analysis

### 1. `DEX2C-SEC-01`: GitHub Actions Workflow Script Injection
- **Location**: [Dex2cWorkflowGenerator.kt](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/cloud/Dex2cWorkflowGenerator.kt#L226-L326)
- **Impact**: When the app generates `.github/workflows/dex2c.yml` for cloud CI builds, it directly interpolates `${{ github.event.inputs.* }}` expressions into inline bash scripts (e.g. `curl -fsSL "${{ github.event.inputs.apk_url }}"`, `APP_NAME="${{ github.event.inputs.app_name }}"`). If dispatched with shell metacharacters, an attacker can break out of string literals and execute arbitrary shell commands inside the GitHub Actions runner.
- **Risk**: The runner has access to repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS`) used to sign production APKs. Command injection enables exfiltration of these signing keys.
- **Remediation**: Pass all workflow inputs as environment variables in the step's `env:` block rather than directly interpolating `${{ github.event.inputs.* }}` into `run:` scripts.

### 2. `DEX2C-SEC-02`: Insecure Fallback to Static AES Key
- **Location**: [TokenEncryptor.kt](file:///C:/Users/roni2/Downloads/Github-Projects/Dex2c-Android/app/src/main/java/com/dex2c/studio/data/TokenEncryptor.kt#L54-L84)
- **Impact**: `KeystoreTokenEncryptor` catches any `Exception` when initializing or reading from `AndroidKeyStore` and falls back to a deterministic, static byte array (`ByteArray(32) { (it * 31 + 17).toByte() }`).
- **Risk**: If the AndroidKeyStore service is unavailable, locked, or throws during key generation, OAuth personal access tokens are encrypted with a publicly known hardcoded key. Anyone inspecting the device or extracted DataStore can decrypt the token with zero difficulty.
- **Remediation**: Only permit the static fallback key during JVM unit test execution (e.g., controlled via build flavor or constructor flag). In production runtime, fail closed with a clear error prompt rather than silently encrypting with a known key.

---

## Positive Security Practices Noted
- **Scoped Permissions**: `AndroidManifest.xml` requests only necessary network and notification permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`).
- **Backup Exclusion**: `data_extraction_rules.xml` and `backup_rules.xml` properly exclude `datastore/dex2c_cloud_prefs.preferences_pb` and keystore XMLs from Google Cloud Auto-Backup.
- **Minimal Component Exposure**: Only `MainActivity` is exported as a launcher activity; no exported broadcast receivers, content providers, or background services are exposed without permissions.
