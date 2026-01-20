package com.ttt.safevault.service.offline;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

/**
 * 网络工具类
 */
public class NetworkUtil {

    /**
     * 检查网络是否可用
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                   (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    /**
     * 检查是否使用WiFi
     */
    public static boolean isWiFi(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null &&
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected();
        }
    }
}
