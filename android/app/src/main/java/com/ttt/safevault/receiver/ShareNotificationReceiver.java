package com.ttt.safevault.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.ttt.safevault.ui.share.ReceiveShareActivity;

/**
 * 分享通知接收器
 * 处理系统通知的点击事件
 */
public class ShareNotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "ShareNotificationReceiver";
    public static final String ACTION_OPEN_SHARE = "com.ttt.safevault.action.OPEN_SHARE";
    public static final String EXTRA_SHARE_ID = "share_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_OPEN_SHARE.equals(intent.getAction())) {
            String shareId = intent.getStringExtra(EXTRA_SHARE_ID);

            if (shareId != null && !shareId.isEmpty()) {
                Log.d(TAG, "Opening share: " + shareId);

                Intent shareIntent = new Intent(context, ReceiveShareActivity.class);
                shareIntent.putExtra("SHARE_ID", shareId);
                shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(shareIntent);
            }
        }
    }
}
