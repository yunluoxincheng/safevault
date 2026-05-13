package com.ttt.safevault.network.api;

import com.ttt.safevault.dto.request.AcceptShareRequest;
import com.ttt.safevault.dto.request.CreateShareRequest;
import com.ttt.safevault.dto.request.RejectShareRequest;
import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.dto.response.ShareResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * 分享服务API接口
 */
public interface ShareServiceApi {
    
    @POST("v1/shares")
    Observable<ShareResponse> createShare(@Body CreateShareRequest request);
    
    @GET("v1/shares/{shareId}")
    Observable<ReceivedShareResponse> receiveShare(@Path("shareId") String shareId);
    
    @POST("v1/shares/{shareId}/revoke")
    Observable<Void> revokeShare(@Path("shareId") String shareId);
    
    @POST("v1/shares/{shareId}/save")
    Observable<Void> saveSharedPassword(@Path("shareId") String shareId);
    
    @GET("v1/shares/created")
    Observable<List<ReceivedShareResponse>> getMyShares();
    
    @GET("v1/shares/received")
    Observable<List<ReceivedShareResponse>> getReceivedShares();

    /**
     * 接受分享
     * @param shareId 分享 ID
     * @param request 接受请求（可选备注）
     * @return 无返回数据
     */
    @POST("v1/shares/{shareId}/accept")
    Observable<Void> acceptShare(@Path("shareId") String shareId, @Body AcceptShareRequest request);

    /**
     * 拒绝分享
     * @param shareId 分享 ID
     * @param request 拒绝请求（可选原因）
     * @return 无返回数据
     */
    @POST("v1/shares/{shareId}/reject")
    Observable<Void> rejectShare(@Path("shareId") String shareId, @Body RejectShareRequest request);
}
