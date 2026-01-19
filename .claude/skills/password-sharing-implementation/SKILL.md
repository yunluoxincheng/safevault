---
name: password-sharing-implementation
description: Use when implementing secure password sharing features in Android apps with multiple transmission methods. Apply for QR code generation, Bluetooth transfer, NFC sharing, cloud-based sharing, real-time notifications, or permission management in security-focused applications.
---

# Password Sharing Implementation

## Overview

**Multi-method password sharing system for Android apps with offline and cloud capabilities.** Based on SafeVault's comprehensive sharing features including secure encryption, permission management, and real-time notifications.

## When to Use

**Use when:**
- Building password managers with sharing capabilities
- Implementing offline data transfer (QR, Bluetooth, NFC)
- Setting up cloud-based sharing with permissions
- Creating real-time notification systems
- Handling encrypted data transmission
- Managing share permissions and expiration
- Discovering nearby users for sharing

**Symptoms that indicate you need this:**
- Need to share sensitive data between devices
- No secure offline transfer mechanism
- Cloud sharing without permission controls
- No real-time notifications for shares
- Difficulty handling multiple sharing methods
- No encryption for shared data

**NOT for:**
- Simple file sharing without encryption
- Public data sharing
- Non-sensitive data transfer

## Core Pattern

### Before (Insecure Single Method)
```java
// Plaintext sharing
public void sharePassword(String password) {
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.putExtra(Intent.EXTRA_TEXT, password); // INSECURE!
    startActivity(intent);
}
```

### After (Secure Multi-method)
```java
// Encrypted sharing with multiple options
public void sharePasswordSecurely(PasswordItem password) {
    // Encrypt for sharing
    String encrypted = encryptForSharing(password);
    
    // Show sharing options
    showShareOptions(encrypted, password.getPermissions());
}
```

## Quick Reference

| Method | Technology | Use Case |
|--------|------------|----------|
| **QR Code** | ZXing library | Offline visual sharing |
| **Bluetooth** | RFCOMM sockets | Nearby device transfer |
| **NFC** | NDEF records | Tap-to-share |
| **Cloud Link** | REST API + deep links | Remote sharing |
| **User-to-User** | WebSocket + API | Direct user sharing |
| **Nearby Users** | Location services | Physical proximity sharing |

## Implementation Guidelines

### 1. Share Data Structure
```java
public class PasswordShare {
    private String shareId;
    private String passwordId;
    private String senderId;
    private String receiverId; // null for public
    private SharePermission permissions;
    private Date expiresAt;
    private String encryptedData;
    
    public enum ShareStatus { PENDING, ACTIVE, ACCEPTED, REVOKED, EXPIRED }
}

public class SharePermission {
    private boolean canView;
    private boolean canSave;
    private boolean revocable;
    
    public static SharePermission viewOnly() {
        return new SharePermission(true, false, true);
    }
}
```

### 2. QR Code Sharing
```java
public class QRCodeUtils {
    public static Bitmap generateShareQR(String data) {
        try {
            String uri = "safevault://share/" + URLEncoder.encode(data, "UTF-8");
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(uri, BarcodeFormat.QR_CODE, 512, 512);
            return toBitmap(matrix);
        } catch (Exception e) {
            return null;
        }
    }
}
```

### 3. Bluetooth Sharing
```java
public class BluetoothTransferManager {
    private static final UUID SAFEVAULT_UUID = UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB");
    
    public void sendShare(String deviceAddress, String data) {
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SAFEVAULT_UUID);
        socket.connect();
        
        OutputStream stream = socket.getOutputStream();
        stream.write(data.getBytes(StandardCharsets.UTF_8));
        socket.close();
    }
}
```

### 4. NFC Sharing
```java
public class NFCTransferManager {
    public NdefMessage createShareMessage(String data) {
        String uri = "safevault://share/" + URLEncoder.encode(data, "UTF-8");
        NdefRecord uriRecord = NdefRecord.createUri(uri);
        return new NdefMessage(new NdefRecord[]{uriRecord});
    }
}
```

### 5. Cloud Sharing (REST API)
```java
public interface ShareServiceApi {
    @POST("/v1/shares")
    Single<ShareResponse> createShare(@Body CreateShareRequest request);
    
    @GET("/v1/shares/{shareId}")
    Single<ShareResponse> getShare(@Path("shareId") String shareId);
}
```

### 6. Real-time Notifications
```java
public class ShareNotificationService extends Service {
    private WebSocketManager webSocketManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        connectWebSocket();
    }
    
    private void connectWebSocket() {
        webSocketManager.connect(token, new WebSocketListener() {
            @Override
            public void onMessage(String message) {
                handleNotification(message);
            }
        });
    }
}
```

## Security Considerations

### 1. Share Encryption
```java
public class ShareEncryption {
    public static String encryptForSharing(PasswordItem password, String receiverKey) {
        // Generate one-time symmetric key
        SecretKey shareKey = generateKey();
        
        // Encrypt password with symmetric key
        byte[] encrypted = encrypt(password.getPassword(), shareKey);
        
        // Encrypt symmetric key with receiver's public key
        byte[] encryptedKey = encrypt(shareKey.getEncoded(), parsePublicKey(receiverKey));
        
        return packageData(encrypted, encryptedKey);
    }
}
```

### 2. Permission Validation
```java
public class SharePermissionValidator {
    public static boolean canView(PasswordShare share, String userId) {
        if (share.getStatus() != ShareStatus.ACTIVE) return false;
        if (share.getExpiresAt().before(new Date())) return false;
        if (share.getReceiverId() != null && !share.getReceiverId().equals(userId)) return false;
        return share.getPermissions().isCanView();
    }
}
```

## Integration Patterns

### 1. Unified Share Activity
```java
public class ShareActivity extends AppCompatActivity {
    private void showSharingOptions(PasswordItem password) {
        String[] methods = {"QR Code", "Bluetooth", "NFC", "Cloud Link"};
        
        new AlertDialog.Builder(this)
            .setTitle("Share Password")
            .setItems(methods, (dialog, which) -> {
                switch (which) {
                    case 0: shareViaQRCode(password); break;
                    case 1: shareViaBluetooth(password); break;
                    case 2: shareViaNFC(password); break;
                    case 3: shareViaCloudLink(password); break;
                }
            })
            .show();
    }
}
```

### 2. Receive Share Handling
```java
public class ReceiveShareActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Handle different incoming share types
        if (getIntent().getAction() != null) {
            handleIntentAction(getIntent());
        } else if (getIntent().hasExtra("share_data")) {
            handleOfflineShare(getIntent().getStringExtra("share_data"));
        }
    }
}
```

## Common Mistakes

| Mistake | Solution |
|---------|----------|
| No encryption for offline shares | Always encrypt with one-time keys |
| QR code too complex | Keep data < 2KB, use error correction |
| Bluetooth not pairing | Use RFCOMM with service record |
| No share expiration | Set default 24h expiration |
| No permission controls | Implement granular permissions |

## Red Flags

**STOP and fix if:**
- Offline shares not encrypted
- QR codes contain plaintext passwords
- No permission validation
- WebSocket connections not authenticated
- Location sharing without user consent

## References

- ZXing QR Code Library
- Android Bluetooth API
- Android NFC Guide
- SafeVault Implementation: `QRCodeUtils.java`, `BluetoothTransferManager.java`, `ShareServiceApi.java`