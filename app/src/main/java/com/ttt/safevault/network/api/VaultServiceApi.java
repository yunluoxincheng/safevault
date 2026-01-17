package com.ttt.safevault.network.api;

import com.ttt.safevault.dto.request.UploadPrivateKeyRequest;
import com.ttt.safevault.dto.request.VaultSyncRequest;
import com.ttt.safevault.dto.response.PrivateKeyResponse;
import com.ttt.safevault.dto.response.UploadPrivateKeyResponse;
import com.ttt.safevault.dto.response.VaultResponse;
import com.ttt.safevault.dto.response.VaultSyncResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * 密码库同步API接口
 */
public interface VaultServiceApi {

    // ========== 私钥管理端点 ==========

    @POST("v1/vault/private-key")
    Observable<UploadPrivateKeyResponse> uploadPrivateKey(
            @Header("X-User-Id") String userId,
            @Body UploadPrivateKeyRequest request
    );

    @GET("v1/vault/private-key")
    Observable<PrivateKeyResponse> getPrivateKey(
            @Header("X-User-Id") String userId
    );

    // ========== 密码库同步端点 ==========

    @POST("v1/vault/sync")
    Observable<VaultSyncResponse> syncVault(
            @Header("X-User-Id") String userId,
            @Body VaultSyncRequest request
    );

    @GET("v1/vault")
    Observable<VaultResponse> getVault(
            @Header("X-User-Id") String userId
    );
}
