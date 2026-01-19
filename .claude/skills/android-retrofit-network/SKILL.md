---
name: android-retrofit-network
description: Use when implementing network layers in Android apps with Retrofit, RxJava, and OkHttp. Apply for authentication, token management, API error handling, SSL configuration, or real-time WebSocket communication in security-focused applications.
---

# Android Retrofit Network Layer

## Overview

**Secure network layer implementation for Android apps using Retrofit, RxJava, and OkHttp.** Based on SafeVault password manager's network architecture with authentication, token management, and security considerations.

## When to Use

**Use when:**
- Building Android apps with REST API integration
- Implementing authentication and token management
- Handling network errors and retry logic
- Configuring SSL/TLS for secure communication
- Setting up real-time communication with WebSockets
- Managing API rate limiting and timeouts
- Debugging network issues in production apps

**Symptoms that indicate you need this:**
- Network code scattered across multiple classes
- No centralized error handling
- Token expiration not handled
- SSL certificate issues in debug mode
- WebSocket connections dropping unexpectedly
- Difficulty mocking network calls for tests

**NOT for:**
- Simple HTTP requests without authentication
- Non-Android projects
- Projects using other networking libraries (Volley, HttpURLConnection)

## Core Pattern

### Before (Scattered Network Code)
```java
// Multiple places making raw HTTP calls
public class LoginActivity extends AppCompatActivity {
    private void login() {
        new Thread(() -> {
            // Direct HTTPURLConnection usage
            // No error handling, no token management
        }).start();
    }
}
```

### After (Centralized Network Layer)
```java
// RetrofitClient singleton
public class RetrofitClient {
    private static RetrofitClient instance;
    private final Retrofit retrofit;
    private final TokenManager tokenManager;
    
    public static RetrofitClient getInstance(Context context) {
        if (instance == null) {
            instance = new RetrofitClient(context.getApplicationContext());
        }
        return instance;
    }
    
    // API services
    public AuthServiceApi getAuthServiceApi() {
        return retrofit.create(AuthServiceApi.class);
    }
}
```

## Quick Reference

| Component | Responsibility | SafeVault Example |
|-----------|----------------|-------------------|
| **RetrofitClient** | Singleton HTTP client | `RetrofitClient.java` |
| **Api Interfaces** | REST endpoint definitions | `AuthServiceApi.java` |
| **TokenManager** | JWT token storage/refresh | `TokenManager.java` |
| **AuthInterceptor** | Add auth headers to requests | `AuthInterceptor.java` |
| **WebSocketManager** | Real-time communication | `WebSocketManager.java` |
| **ApiConstants** | URLs and timeouts | `ApiConstants.java` |

## Implementation Guidelines

### 1. RetrofitClient Singleton
```java
public class RetrofitClient {
    private static RetrofitClient instance;
    private final Retrofit retrofit;
    private final TokenManager tokenManager;
    
    private AuthServiceApi authServiceApi;
    private ShareServiceApi shareServiceApi;
    
    private RetrofitClient(Context context) {
        tokenManager = TokenManager.getInstance(context);
        
        // HTTP logging (BASIC level to avoid logging sensitive data)
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        
        // Authentication interceptor
        AuthInterceptor authInterceptor = new AuthInterceptor(tokenManager);
        
        // SSL configuration (development: trust all, production: proper certs)
        X509TrustManager trustAllCerts = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{}; }
        };
        
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{trustAllCerts}, null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        
        // OkHttpClient with interceptors and timeouts
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .sslSocketFactory(sslSocketFactory, trustAllCerts)
            .hostnameVerifier((hostname, session) -> true) // Development only!
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        
        // Retrofit configuration
        retrofit = new Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build();
        
        // Initialize API services
        authServiceApi = retrofit.create(AuthServiceApi.class);
        shareServiceApi = retrofit.create(ShareServiceApi.class);
        
        // Configure TokenManager with auth API for token refresh
        tokenManager.setAuthApi(authServiceApi);
    }
    
    public static synchronized RetrofitClient getInstance(Context context) {
        if (instance == null) {
            instance = new RetrofitClient(context.getApplicationContext());
        }
        return instance;
    }
    
    public AuthServiceApi getAuthServiceApi() { return authServiceApi; }
    public TokenManager getTokenManager() { return tokenManager; }
}
```

### 2. Authentication Interceptor
```java
public class AuthInterceptor implements Interceptor {
    private final TokenManager tokenManager;
    
    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        
        // Skip auth for login/register endpoints
        if (shouldSkipAuth(originalRequest)) {
            return chain.proceed(originalRequest);
        }
        
        // Get access token
        String accessToken = tokenManager.getAccessToken();
        if (accessToken == null) {
            // Token missing - could trigger re-authentication
            return chain.proceed(originalRequest);
        }
        
        // Add Authorization header
        Request.Builder requestBuilder = originalRequest.newBuilder()
            .header("Authorization", "Bearer " + accessToken);
        
        Response response = chain.proceed(requestBuilder.build());
        
        // Handle 401 Unauthorized - token expired
        if (response.code() == 401) {
            // Attempt token refresh
            boolean refreshed = tokenManager.refreshTokenSync();
            if (refreshed) {
                // Retry with new token
                accessToken = tokenManager.getAccessToken();
                requestBuilder = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + accessToken);
                response.close();
                return chain.proceed(requestBuilder.build());
            } else {
                // Refresh failed - clear tokens and return original response
                tokenManager.clearTokens();
            }
        }
        
        return response;
    }
    
    private boolean shouldSkipAuth(Request request) {
        String path = request.url().encodedPath();
        return path.contains("/auth/login") || 
               path.contains("/auth/register") ||
               path.contains("/auth/refresh");
    }
}
```

### 3. Token Management
```java
public class TokenManager {
    private static final String PREFS_NAME = "token_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    
    private static TokenManager instance;
    private final SharedPreferences prefs;
    private AuthServiceApi authApi;
    
    private TokenManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static TokenManager getInstance(Context context) {
        if (instance == null) {
            instance = new TokenManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // Save tokens from AuthResponse
    public void saveTokens(AuthResponse response) {
        saveTokens(response.getUserId(), response.getAccessToken(), response.getRefreshToken());
    }
    
    public void saveTokens(String userId, String accessToken, String refreshToken) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_ACCESS_TOKEN, accessToken);
        editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        editor.apply();
    }
    
    // Refresh token with RxJava
    public Single<AuthResponse> refreshToken() {
        String refreshToken = getRefreshToken();
        if (refreshToken == null) {
            return Single.error(new IllegalStateException("No refresh token available"));
        }
        
        return authApi.refreshToken("Bearer " + refreshToken)
            .doOnSuccess(this::saveTokens)
            .doOnError(error -> {
                // Clear tokens on refresh failure
                if (error instanceof HttpException && 
                    ((HttpException) error).code() == 401) {
                    clearTokens();
                }
            });
    }
    
    // Synchronous refresh (for interceptors)
    public boolean refreshTokenSync() {
        try {
            AuthResponse response = authApi.refreshToken("Bearer " + getRefreshToken())
                .execute()
                .body();
            
            if (response != null) {
                saveTokens(response);
                return true;
            }
        } catch (IOException e) {
            // Refresh failed
        }
        return false;
    }
    
    // Clear all tokens (logout)
    public void clearTokens() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_ACCESS_TOKEN);
        editor.remove(KEY_REFRESH_TOKEN);
        editor.apply();
    }
    
    public boolean isLoggedIn() {
        return getAccessToken() != null;
    }
    
    // Getters
    public String getAccessToken() { return prefs.getString(KEY_ACCESS_TOKEN, null); }
    public String getRefreshToken() { return prefs.getString(KEY_REFRESH_TOKEN, null); }
    public String getUserId() { return prefs.getString(KEY_USER_ID, null); }
    
    // Setter for auth API (called by RetrofitClient)
    public void setAuthApi(AuthServiceApi authApi) { this.authApi = authApi; }
}
```

### 4. API Service Interface (RxJava)
```java
public interface AuthServiceApi {
    @POST("/v1/auth/register")
    Single<AuthResponse> register(@Body RegisterRequest request);
    
    @POST("/v1/auth/login")
    Single<AuthResponse> login(@Body LoginRequest request);
    
    @POST("/v1/auth/refresh")
    Single<AuthResponse> refreshToken(@Header("Authorization") String refreshToken);
    
    @POST("/v1/auth/email/register")
    Single<EmailRegistrationResponse> registerWithEmail(@Body EmailRegistrationRequest request);
    
    @POST("/v1/auth/email/verify")
    Single<VerifyEmailResponse> verifyEmail(@Body VerifyEmailRequest request);
    
    @POST("/v1/auth/email/login")
    Single<EmailLoginResponse> loginByEmail(@Body LoginByEmailRequest request);
}
```

### 5. ViewModel Integration
```java
public class AuthViewModel extends AndroidViewModel {
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.retrofitClient = RetrofitClient.getInstance(application);
        this.tokenManager = retrofitClient.getTokenManager();
    }
    
    public void loginWithEmail(String email, String masterPassword) {
        // Get device info
        String deviceId = KeyManager.getInstance(getApplication()).getDeviceId();
        String deviceName = getDeviceName();
        long timestamp = System.currentTimeMillis();
        String signature = generateSignature(email, deviceId, masterPassword, timestamp);
        
        Disposable disposable = retrofitClient.getAuthServiceApi()
            .loginByEmail(new LoginByEmailRequest(
                email, deviceId, deviceName, signature, timestamp, "android", 
                "Android " + Build.VERSION.RELEASE))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    // Save tokens
                    tokenManager.saveTokens(
                        response.getUserId(),
                        response.getAccessToken(),
                        response.getRefreshToken()
                    );
                    authState.setValue(AuthState.AUTHENTICATED);
                },
                error -> {
                    if (error instanceof HttpException) {
                        int code = ((HttpException) error).code();
                        if (code == 401) {
                            errorMessage.setValue("Invalid credentials");
                        } else if (code == 429) {
                            errorMessage.setValue("Too many attempts, try later");
                        }
                    } else {
                        errorMessage.setValue("Network error: " + error.getMessage());
                    }
                }
            );
            
        disposables.add(disposable);
    }
}
```

### 6. WebSocket Integration (Real-time)
```java
public class WebSocketManager {
    private static WebSocketManager instance;
    private OkHttpClient okHttpClient;
    private WebSocket webSocket;
    private boolean isConnected = false;
    
    private WebSocketManager() {
        okHttpClient = new OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS) // Keep-alive
            .build();
    }
    
    public void connect(String token) {
        String wsUrl = ApiConstants.WS_BASE_URL + "/notifications?token=" + token;
        Request request = new Request.Builder()
            .url(wsUrl)
            .build();
            
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                isConnected = true;
                // Notify listeners
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // Parse JSON notification
                Gson gson = new Gson();
                ShareNotificationMessage message = gson.fromJson(text, ShareNotificationMessage.class);
                // Handle notification
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isConnected = false;
                // Schedule reconnection
            }
        });
    }
}
```

## Security Considerations

### 1. SSL/TLS Configuration
- **Development**: Trust all certificates (for testing)
- **Production**: Use proper certificate pinning
- **Never log sensitive data** in HTTP logs
- **Use HTTPS for all endpoints**

### 2. Token Security
- **Store tokens in SharedPreferences** (encrypted in production)
- **Auto-refresh on 401 responses**
- **Clear tokens on logout or refresh failure**
- **Never log tokens** in debug output

### 3. Request/Response Security
- **Use GSON with lenient parsing disabled** in production
- **Validate all API responses**
- **Sanitize user input** before sending
- **Handle timeouts gracefully**

## Error Handling Patterns

```java
// Global error handler in ViewModel
private void handleNetworkError(Throwable error) {
    if (error instanceof SocketTimeoutException) {
        errorMessage.setValue("Connection timeout, check network");
    } else if (error instanceof ConnectException) {
        errorMessage.setValue("Cannot connect to server");
    } else if (error instanceof HttpException) {
        HttpException httpError = (HttpException) error;
        switch (httpError.code()) {
            case 401:
                errorMessage.setValue("Session expired, please login again");
                tokenManager.clearTokens();
                break;
            case 403:
                errorMessage.setValue("Access denied");
                break;
            case 404:
                errorMessage.setValue("Resource not found");
                break;
            case 429:
                errorMessage.setValue("Too many requests, try later");
                break;
            case 500:
                errorMessage.setValue("Server error, please try again");
                break;
            default:
                errorMessage.setValue("HTTP error: " + httpError.code());
        }
    } else {
        errorMessage.setValue("Network error: " + error.getMessage());
    }
}
```

## Testing Strategy

```java
@RunWith(AndroidJUnit4.class)
public class NetworkLayerTest {
    private MockWebServer mockWebServer;
    private RetrofitClient retrofitClient;
    
    @Before
    public void setup() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        // Configure Retrofit with mock server URL
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(mockWebServer.url("/").toString())
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build();
            
        // Test with mocked Retrofit instance
    }
    
    @Test
    public void testLoginSuccess() {
        // Enqueue mock response
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"userId\":\"123\",\"accessToken\":\"abc\",\"refreshToken\":\"def\"}"));
        
        // Make API call and verify
    }
    
    @After
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }
}
```

## Common Mistakes

| Mistake | Problem | Solution |
|---------|---------|----------|
| **No token refresh logic** | Users get logged out on token expiry | Implement AuthInterceptor with refresh |
| **Hardcoded API URLs** | Difficult to change environments | Use ApiConstants with build variants |
| **No network error handling** | App crashes on network issues | Global error handler in ViewModel |
| **SSL bypass in production** | Security vulnerability | Use proper certificate validation |
| **Memory leaks with RxJava** | Disposables not cleaned | Use CompositeDisposable in ViewModel |
| **No request timeouts** | App hangs on slow networks | Set connect/read/write timeouts |
| **Logging sensitive data** | Security breach | Use BASIC logging level |

## SafeVault Specific Implementation

### 1. ApiConstants Configuration
```java
public class ApiConstants {
    // Development vs Production URLs
    public static final String BASE_URL = "https://api.safevault.dev/v1";
    public static final String WS_BASE_URL = "wss://api.safevault.dev/ws";
    
    // Timeouts (seconds)
    public static final int CONNECT_TIMEOUT = 30;
    public static final int READ_TIMEOUT = 30;
    public static final int WRITE_TIMEOUT = 30;
    
    // Endpoint paths
    public static final String PATH_LOGIN = "/auth/login";
    public static final String PATH_REGISTER = "/auth/register";
    public static final String PATH_SHARES = "/shares";
}
```

### 2. DTO (Data Transfer Objects)
```java
// Request DTO
public class LoginByEmailRequest {
    private String email;
    private String deviceId;
    private String deviceName;
    private String signature;
    private long timestamp;
    private String deviceType;
    private String osVersion;
    
    // Constructor, getters, setters
}

// Response DTO
public class EmailLoginResponse {
    private String userId;
    private String accessToken;
    private String refreshToken;
    private boolean isNewDevice;
    
    // Constructor, getters, setters
}
```

## Red Flags

**STOP and fix if:**
- No token refresh mechanism
- HTTP calls without timeouts
- SSL certificate validation disabled in production
- Sensitive data logged in HTTP interceptor
- No centralized error handling
- WebSocket without ping/pong keep-alive
- API URLs hardcoded in multiple places
- No network state checking (offline mode)

**CRITICAL**: Always test network layer with MockWebServer for reliable tests.

## References

- Retrofit Documentation: https://square.github.io/retrofit/
- OkHttp Interceptors: https://square.github.io/okhttp/interceptors/
- SafeVault Implementation: `RetrofitClient.java`, `TokenManager.java`, `AuthInterceptor.java`
- Android Network Security Configuration: https://developer.android.com/training/articles/security-config