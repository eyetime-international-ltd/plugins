// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.firebasemessaging;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.Result;

import java.util.Map;

public class FlutterFirebaseMessagingService extends FirebaseMessagingService {

  private static final String TAG = FlutterFirebaseMessagingService.class.getSimpleName();

  // Firebase specific meta keys
  private static final String NOTIFICATION_ICON_KEY = "com.google.firebase.messaging.default_notification_icon";
  private static final String FIREBASE_CHANNEL_ID_KEY = "com.google.firebase.messaging.default_notification_channel_id";
  private static final String FIREBASE_CHANNEL_DESCRIPTION_KEY = "com.google.firebase.messaging.default_notification_channel_description";

  private static final String DEFAULT_CHANNEL_ID = "default-channel";
  private static final String DEFAULT_CHANNEL_DESCRIPTION = "Description";

  public static final String ACTION_REMOTE_MESSAGE = "io.flutter.plugins.firebasemessaging.NOTIFICATION";
  public static final String ACTION_DISPATCH_APP = "io.flutter.plugins.firebasemessaging.DISPATCH_APP";
  public static final String EXTRA_REMOTE_MESSAGE = "notification";

  public static final String ACTION_TOKEN = "io.flutter.plugins.firebasemessaging.TOKEN";
  public static final String EXTRA_TOKEN = "token";

  public interface ShouldShowNotificationHandler {
    void invoke(Map<String, String> data, MethodChannel.Result callback);
  }

  private static ShouldShowNotificationHandler sShouldShowNotificationHandler = null;

  /**
   * Callback set by [FirebaseMessagingPlugin] to enable method call to dart code
   * Used to check if foreground notifications should be shown
   */
  public static void setShouldShowNotificationHandler(ShouldShowNotificationHandler handler) {
    sShouldShowNotificationHandler = handler;
  }

  /**
   * Called when message is received.
   *
   * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
   */
  @Override
  public void onMessageReceived(final RemoteMessage remoteMessage) {
    Log.d(TAG, "onMessageReceived");
    if (sShouldShowNotificationHandler == null) {
      return;
    }

    // Callback to check if foreground notification should be shown
    sShouldShowNotificationHandler.invoke(remoteMessage.getData(), new Result() {
      @Override
      public void success(Object obj) {
        if ((boolean) obj) {
          showNotification(remoteMessage);
        }
      }

      @Override
      public void error(String s, String s1, Object o) {
        throw new RuntimeException("Error during foreground notification check");
      }

      @Override
      public void notImplemented() {
        throw new RuntimeException("Not implemented exception during foreground check");
      }
    });
  }

  /**
   * Called when a new token for the default Firebase project is generated.
   *
   * @param token The token used for sending messages to this application instance. This token is
   *     the same as the one retrieved by getInstanceId().
   */
  @Override
  public void onNewToken(String token) {
    Intent intent = new Intent(ACTION_TOKEN);
    intent.putExtra(EXTRA_TOKEN, token);
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  /**
   * Creates and shows a system notification (In case of app in foreground)
   *
   * In case of an Android version >= 8 the firebase messaging notification channel is used.
   *
   * @param message
   */
  private void showNotification(final RemoteMessage message) {
    Intent intent = new Intent(ACTION_DISPATCH_APP);
    intent.putExtra(EXTRA_REMOTE_MESSAGE, message);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT );

    Bundle args;
    try {
      args = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData;
    } catch (Throwable th) {
      Log.w(TAG, "Unable to get package information", th);
      return;
    }

    int resIcon = 0;
    String channelId = DEFAULT_CHANNEL_ID;
    String channelDescription = DEFAULT_CHANNEL_DESCRIPTION;
    if (args != null) {
      channelId = args.getString(FIREBASE_CHANNEL_ID_KEY, DEFAULT_CHANNEL_ID);
      channelDescription = args.getString(FIREBASE_CHANNEL_DESCRIPTION_KEY, DEFAULT_CHANNEL_DESCRIPTION);
      resIcon = args.getInt(NOTIFICATION_ICON_KEY);
    }

    Notification notification;
    try {
      notification = new NotificationCompat.Builder(this, channelId)
              .setContentTitle(message.getNotification().getTitle())
              .setContentText(message.getNotification().getBody())
              .setAutoCancel(true)
              .setContentIntent(pendingIntent)
              .setSmallIcon(resIcon)
              .build();
    } catch (Throwable th) {
      Log.w(TAG, "Unable to build notification");
      return;
    }

    NotificationManager manager = (NotificationManager) getApplicationContext()
            .getSystemService(Context.NOTIFICATION_SERVICE);

    // Android >= 8 introduced channels for managing notifications
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      int importance = NotificationManager.IMPORTANCE_HIGH;
      NotificationChannel channel = new NotificationChannel(channelId, channelDescription, importance);
      manager.createNotificationChannel(channel);
    }

    // Notification is identified by the pair of Tag and Id.
    // https://firebase.google.com/docs/cloud-messaging/http-server-ref
    // In our case the Tag is set as the room_id and id defaults to 0 within firebase messaging
    manager.notify(message.getNotification().getTag(), 0, notification);
  }

}
