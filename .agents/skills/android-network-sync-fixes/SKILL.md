---
name: android-network-sync-fixes
description: Use when Retrofit API calls fail, WebSocket connections drop, HTTP errors occur (401, 403, 500, timeouts), token refresh fails, data synchronization doesn't work, or network request issues arise in Android apps
---

# Android Network & Sync Fixes

## Overview

Systematic debugging and fixing for network operations including Retrofit REST API calls, WebSocket real-time connections, data synchronization, and token authentication management.

**Core principle:** Network issues are rarely random. Trace the full request-response path from app → client → server → response.

**REQUIRED PREREQUISITE:** Use `android-debugging-fixes` first for general debugging foundation.

## When to Use

Use this skill when:
- API calls fail with HTTP errors (401, 403, 404, 500, etc.)
- WebSocket connections fail to establish or drop unexpectedly
- Data synchronization doesn't complete
- Token refresh fails or loops endlessly
- Requests timeout consistently
- Network responses are malformed
- Offline/online state transitions fail

## Network Layer Architecture

```
Application (ViewModel/Activity)
    ↓
BackendService Interface
    ↓
Retrofit Client (OkHttp)
    ├── Interceptors (Logging, Auth, Retry)
    ├── TokenManager (JWT refresh)
    └── WebSocketManager (Real-time)
    ↓
Network (Internet/Server)
```

## Diagnostic Flowchart

```
┌─────────────────────────────────────────────────────────────┐
│  1. Verify Network Connectivity                              │
│  - Check active network: ConnectivityManager                 │
│  - Check internet access (not just WiFi connected)          │
│  - Test with: adb shell ping -c 3 google.com                │
│  └─> No internet ──> Show offline UI                        │
│         │                                                    │
│         Has internet                                         │
│         │                                                    │
│         v                                                    │
│  2. Check API Endpoint Configuration                         │
│  - BASE_URL correct? (no trailing slash usually)            │
│  - Endpoint path correct?                                    │
│  - API version in path? (/v1/)                               │
│  └─> Wrong ──> Fix BuildConfig or constants                │
│         │                                                    │
│         Correct                                              │
│         │                                                    │
│         v                                                    │
│  3. Add Network Logging Interceptor                          │
│  - Enable OkHttp HttpLoggingInterceptor                      │
│  - Set level to BODY                                         │
│  - Check logcat for actual request/response                 │
│  └─> Review logs for issues                                 │
│         │                                                    │
│         v                                                    │
│  4. Analyze HTTP Status Code                                 │
│  See "HTTP Error Codes" table below                          │
│  └─> Apply specific fix                                      │
│         │                                                    │
│         v                                                    │
│  5. Check Request Format                                     │
│  - Request body JSON valid?                                  │
│  - Required headers present?                                 │
│  - Content-Type correct?                                     │
│  - Query parameters properly encoded?                        │
│  └─> Fix request building                                   │
│         │                                                    │
│         v                                                    │
│  6. Check Authentication                                     │
│  - Token present?                                            │
│  - Token format valid (Bearer)?                              │
│  - Token expired?                                            │
│  └─> Token issue ──> Check TokenManager                     │
│         │                                                    │
│         v                                                    │
│  7. Check Response Parsing                                   │
│  - Response JSON matches DTO?                                │
│  - Nullable fields handled?                                  │
│  - Gson/Moshi converters configured?                         │
│  └─> Fix DTO or parsing                                     │
│         │                                                    │
│         v                                                    │
│  8. Test with Postman/curl                                   │
│  (Verify server is responding correctly)                     │
└─────────────────────────────────────────────────────────────┘
```

## HTTP Error Codes Reference

| Status | Meaning | Common Causes | Fix Strategy |
|--------|---------|---------------|--------------|
| **400** | Bad Request | Malformed JSON, invalid params | Log request body, validate before sending |
| **401** | Unauthorized | Missing/invalid token | Check token presence, trigger refresh |
| **403** | Forbidden | Valid token but insufficient permissions | Check user permissions, verify role |
| **404** | Not Found | Wrong endpoint, resource doesn't exist | Verify endpoint URL, check ID |
| **409** | Conflict | Resource already exists, state conflict | Check for duplicates before creating |
| **429** | Too Many Requests | Rate limiting exceeded | Implement retry with exponential backoff |
| **500** | Internal Server Error | Server bug | Check server logs, report to backend team |
| **502** | Bad Gateway | Upstream server issue | Check server status, retry |
| **503** | Service Unavailable | Server maintenance/overloaded | Retry after delay, show maintenance message |
| **504** | Gateway Timeout | Request took too long | Increase timeout, optimize query |

## Common Issues and Solutions

### Issue 1: 401 Unauthorized Loop

**Symptoms:**
- API returns 401
- Token refresh triggered
- After refresh, still get 401
- Infinite refresh loop

**Diagnosis:**
```kotlin
// Add logging interceptor to see actual tokens
val loggingInterceptor = HttpLoggingInterceptor { message ->
    Log.d("OkHttp", message)
}.apply {
    level = HttpLoggingInterceptor.Level.BODY
}

// Check token in authenticator
val authenticator = object : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d("Auth", "401 on ${response.request.url}")
        Log.d("Auth", "Current token: ${response.request.header("Authorization")}")

        // Refresh token logic...
        val newToken = tokenManager.refreshToken()

        if (newToken == null) {
            Log.e("Auth", "Token refresh failed, logout")
            return null // Don't retry, trigger logout
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }
}
```

**Common causes:**
1. Token refresh API also returns 401
2. New token not saved correctly
3. Refresh token also expired
4. Authenticator not applied to client

**Solution (Kotlin):**
```kotlin
// Prevent infinite loops
class TokenAuthenticator(
    private val tokenManager: TokenManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevent infinite loop: if we already tried refreshing
        if (response.responseCount >= 2) {
            return null // Give up, logout user
        }

        // Check if this is a token refresh request
        val refreshTokenPath = "/v1/auth/refresh"
        if (response.request.url.encodedPath.contains(refreshTokenPath)) {
            return null // Don't retry refresh failures
        }

        synchronized(this) {
            val newToken = tokenManager.getValidTokenSync()

            if (newToken == null) {
                // Refresh failed, logout user
                return null
            }

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }
    }

    private val Response.responseCount: Int
        get() = generateSequence(this) { it.priorResponse }
            .count() - 1
}
```

**Solution (Java):**
```java
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

// Prevent infinite loops
public class TokenAuthenticator implements Authenticator {

    private static final String TAG = "TokenAuthenticator";
    private final TokenManager tokenManager;

    public TokenAuthenticator(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        // Prevent infinite loop: if we already tried refreshing
        if (responseCount(response) >= 2) {
            Log.e(TAG, "Token refresh failed after retry, logging out");
            return null; // Give up, logout user
        }

        // Check if this is a token refresh request
        String refreshTokenPath = "/v1/auth/refresh";
        if (response.request().url().encodedPath().contains(refreshTokenPath)) {
            Log.e(TAG, "Token refresh request failed with 401");
            return null; // Don't retry refresh failures
        }

        synchronized (this) {
            String newToken = tokenManager.getValidTokenSync();

            if (newToken == null) {
                // Refresh failed, logout user
                Log.e(TAG, "Failed to refresh token");
                return null;
            }

            Log.d(TAG, "Token refreshed successfully");
            return response.request().newBuilder()
                .header("Authorization", "Bearer " + newToken)
                .build();
        }
    }

    private int responseCount(Response response) {
        int count = 1;
        Response priorResponse = response.priorResponse();
        while (priorResponse != null) {
            count++;
            priorResponse = priorResponse.priorResponse();
        }
        return count;
    }
}
```

---

### Issue 2: WebSocket Connection Drops

**Symptoms:**
- WebSocket connects initially
- Connection drops after some time
- No automatic reconnection

**Diagnosis:**
```kotlin
// Log WebSocket lifecycle
webSocketListener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d("WebSocket", "Connected")
        scheduleHeartbeat() // Keep connection alive
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("WebSocket", "Closing: $code - $reason")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d("WebSocket", "Closed: $code - $reason")
        scheduleReconnect()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e("WebSocket", "Error: ${t.message}", t)
        scheduleReconnect()
    }
}

private fun scheduleHeartbeat() {
    // Send ping every 30 seconds
    heartbeatHandler.postDelayed({
        webSocket?.send("{\"type\":\"ping\"}")
        scheduleHeartbeat()
    }, 30_000)
}
```

**Common causes:**
1. Server timeout (no activity)
2. Network change (WiFi → mobile)
3. App went to background
4. No heartbeat/ping mechanism

**Solution:**
```kotlin
class WebSocketManager(
    private val context: Context,
    private val tokenManager: TokenManager
) {
    private var webSocket: WebSocket? = null
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 5

    fun connect() {
        val request = Request.Builder()
            .url("${BASE_URL}/ws")
            .header("Authorization", "Bearer ${tokenManager.getToken()}")
            .build()

        webSocket = okHttpClient.newWebSocket(request, webSocketListener)
    }

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d("WS", "Connected")
            reconnectAttempts = 0
            startHeartbeat()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d("WS", "Closed: $code - $reason")
            stopHeartbeat()
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e("WS", "Error: ${t.message}")
            stopHeartbeat()
            scheduleReconnect()
        }
    }

    private fun startHeartbeat() {
        heartbeatHandler.postDelayed(object : Runnable {
            override fun run() {
                webSocket?.send("{\"type\":\"ping\"}")
                heartbeatHandler.postDelayed(this, 30_000)
            }
        }, 30_000)
    }

    private fun stopHeartbeat() {
        heartbeatHandler.removeCallbacksAndMessages(null)
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e("WS", "Max reconnect attempts reached")
            return
        }

        val delay = (2.0.pow(reconnectAttempts) * 1000).toLong() // Exponential backoff
        reconnectAttempts++

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("WS", "Reconnecting... (attempt $reconnectAttempts)")
            connect()
        }, delay)
    }

    // Monitor network changes
    fun startNetworkMonitoring() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("WS", "Network available, reconnecting...")
                    connect()
                }

                override fun onLost(network: Network) {
                    Log.d("WS", "Network lost")
                }
            }
        )
    }
}
```

---

### Issue 3: Data Synchronization Fails

**Symptoms:**
- Local data doesn't match server
- Changes not pushed to server
- Server changes not pulled
- Conflict resolution fails

**Diagnosis:**
```kotlin
// Log sync operations
class SyncManager {
    suspend fun sync(): SyncResult {
        try {
            Log.d("Sync", "Starting sync...")

            // 1. Push local changes
            val localChanges = localDatabase.getPendingChanges()
            Log.d("Sync", "Pushing ${localChanges.size} local changes")

            val pushResult = apiService.pushChanges(localChanges)
            Log.d("Sync", "Push result: $pushResult")

            // 2. Pull server changes
            val serverChanges = apiService.pullChanges(lastSyncTime)
            Log.d("Sync", "Pulled ${serverChanges.size} server changes")

            // 3. Merge changes
            val conflicts = resolveConflicts(localChanges, serverChanges)
            Log.d("Sync", "Found ${conflicts.size} conflicts")

            // 4. Update local database
            localDatabase.applyChanges(serverChanges)
            localDatabase.markChangesSynced(localChanges)

            Log.d("Sync", "Sync complete")
            return SyncResult.Success
        } catch (e: Exception) {
            Log.e("Sync", "Sync failed: ${e.message}", e)
            return SyncResult.Error(e.message)
        }
    }
}
```

**Common causes:**
1. No retry mechanism for failed sync
2. Network interruption during sync
3. Conflicts not handled
4. Last sync time not tracked

**Solution:**
```kotlin
class SyncManager(
    private val apiService: ApiService,
    private val localDatabase: LocalDatabase,
    private val context: Context
) {
    private val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            30,
            TimeUnit.SECONDS
        )
        .build()

    fun scheduleSync() {
        WorkManager.getInstance(context).enqueue(syncWork)
    }

    suspend fun syncNow(): SyncResult {
        // Implementation with proper error handling and conflict resolution
    }

    private suspend fun resolveConflicts(
        local: List<Item>,
        server: List<Item>
    ): List<Conflict> {
        // Conflict resolution strategy
    }
}
```

---

### Issue 4: Request Timeout

**Symptoms:**
- `java.net.SocketTimeoutException: timeout`
- Requests consistently fail after a delay
- Works on fast networks, fails on slow networks

**Solution:**
```kotlin
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)      // Time to establish connection
    .readTimeout(60, TimeUnit.SECONDS)         // Time to read response
    .writeTimeout(60, TimeUnit.SECONDS)        // Time to send request
    .callTimeout(120, TimeUnit.SECONDS)        // Total call timeout
    .retryOnConnectionFailure(true)
    .build()
```

## Network Logging Configuration

```kotlin
object NetworkClient {

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("API", message)
    }.apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}
```

## Testing Checklist

- [ ] Test with network connected
- [ ] Test with network disconnected
- [ ] Test with slow network (Network Link Conditioner)
- [ ] Test token expiration and refresh
- [ ] Test WebSocket reconnection
- [ ] Test sync conflict resolution
- [ ] Test with invalid server responses
- [ ] Test concurrent requests
- [ ] Test after app background/foreground
- [ ] Test on different network types (WiFi, mobile)

## Encountering Unknown Issues?

1. **Enable full logging** - Set HttpLoggingInterceptor.Level.BODY
2. **Capture network traffic** - Use Charles Proxy or Wireshark
3. **Test with Postman** - Verify server responds correctly
4. **Check server logs** - Correlate with app logs
5. **Review OkHttp documentation** - https://square.github.io/okhttp/

**After solving:** Update this skill with the new issue and solution

## Related Skills

- **android-debugging-fixes** - General Android debugging (use first)
- **android-security-practices** - Token storage and security

## Red Flags - STOP and Verify

If you catch yourself thinking:
- "Just increase the timeout to fix it"
- "Network requests always succeed"
- "Skip the logging, it's too verbose"
- "Testing on WiFi is sufficient"

**ALL of these mean: STOP. Network issues require systematic debugging.**
