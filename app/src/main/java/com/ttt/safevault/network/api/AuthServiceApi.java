package com.ttt.safevault.network.api;

import com.ttt.safevault.dto.request.*;
import com.ttt.safevault.dto.response.AuthResponse;
import com.ttt.safevault.dto.response.CompleteRegistrationResponse;
import com.ttt.safevault.dto.response.DeleteAccountResponse;
import com.ttt.safevault.dto.response.DeviceListResponse;
import com.ttt.safevault.dto.response.EmailLoginResponse;
import com.ttt.safevault.dto.response.EmailRegistrationResponse;
import com.ttt.safevault.dto.response.LoginPrecheckResponse;
import com.ttt.safevault.dto.response.LogoutResponse;
import com.ttt.safevault.dto.response.RemoveDeviceResponse;
import com.ttt.safevault.dto.response.VerifyEmailResponse;
import com.ttt.safevault.dto.response.VerificationStatusResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 认证服务API接口
 */
public interface AuthServiceApi {

    @POST("v1/auth/register")
    Observable<AuthResponse> register(@Body RegisterRequest request);

    @POST("v1/auth/login")
    Observable<AuthResponse> login(@Body LoginRequest request);

    @POST("v1/auth/login/by-username")
    Observable<AuthResponse> loginByUsername(@Body LoginByUsernameRequest request);

    @POST("v1/auth/refresh")
    Observable<AuthResponse> refreshToken(@Header("Authorization") String refreshToken);

    // ========== 统一邮箱认证 API ==========

    @POST("v1/auth/register-email")
    Observable<EmailRegistrationResponse> registerWithEmail(@Body EmailRegistrationRequest request);

    @POST("v1/auth/verify-email")
    Observable<VerifyEmailResponse> verifyEmail(@Body VerifyEmailRequest request);

    @POST("v1/auth/resend-verification")
    Observable<EmailRegistrationResponse> resendVerification(@Body ResendVerificationRequest request);

    @POST("v1/auth/complete-registration")
    Observable<CompleteRegistrationResponse> completeRegistration(@Body CompleteRegistrationRequest request);

    @GET("v1/auth/verification-status")
    Observable<VerificationStatusResponse> checkVerificationStatus(@Query("email") String email);

    @POST("v1/auth/login-by-email")
    Observable<EmailLoginResponse> loginByEmail(@Body LoginByEmailRequest request);

    @POST("v1/auth/login-precheck")
    Observable<LoginPrecheckResponse> loginPrecheck(@Body LoginPrecheckRequest request);

    @POST("v1/auth/logout")
    Observable<LogoutResponse> logout(@Header("X-User-Id") String userId, @Body LogoutRequest request);

    // ========== 账户管理 API ==========

    @DELETE("v1/account")
    Observable<DeleteAccountResponse> deleteAccount(@Header("Authorization") String token);

    // ========== 设备管理 API ==========

    @GET("v1/auth/devices")
    Observable<DeviceListResponse> getDevices(@Header("X-User-Id") String userId);

    @DELETE("v1/auth/devices/{deviceId}")
    Observable<RemoveDeviceResponse> removeDevice(
            @Header("X-User-Id") String userId,
            @Path("deviceId") String deviceId
    );
}
