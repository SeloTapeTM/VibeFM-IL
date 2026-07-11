package il.radio.israelradio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

/**
 * Foreground service that keeps the process alive while a station is
 * playing (through Doze/App Standby) and exposes lock-screen / Bluetooth
 * (steering wheel, AVRCP) media controls via a MediaSession. Actual audio
 * decoding/playback stays in the WebView's <audio> element - this service
 * only mirrors its state and forwards transport commands back to it.
 */
public class RadioPlaybackService extends Service {

    static final String ACTION_UPDATE_STATE = "il.radio.israelradio.action.UPDATE_STATE";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_SUBTITLE = "subtitle";
    static final String EXTRA_PLAYING = "playing";

    private static final String ACTION_PLAY_PAUSE = "il.radio.israelradio.action.PLAY_PAUSE";
    private static final String ACTION_NEXT = "il.radio.israelradio.action.NEXT";
    private static final String ACTION_PREVIOUS = "il.radio.israelradio.action.PREVIOUS";
    private static final String ACTION_STOP = "il.radio.israelradio.action.STOP";

    private static final String CHANNEL_ID = "radio_playback";
    private static final int NOTIFICATION_ID = 1;

    private MediaSessionCompat mediaSession;
    private String currentTitle = "Israel Radio";
    private String currentSubtitle = "";
    private boolean isPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();

        PendingIntent mediaButtonPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            new Intent(Intent.ACTION_MEDIA_BUTTON, null, this, MediaButtonReceiver.class),
            PendingIntent.FLAG_IMMUTABLE
        );

        mediaSession = new MediaSessionCompat(this, "IsraelRadioSession");
        mediaSession.setMediaButtonReceiver(mediaButtonPendingIntent);
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSession.setCallback(
            new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    forward("play");
                }

                @Override
                public void onPause() {
                    forward("pause");
                }

                @Override
                public void onSkipToNext() {
                    forward("next");
                }

                @Override
                public void onSkipToPrevious() {
                    forward("previous");
                }

                @Override
                public void onStop() {
                    forward("stop");
                }
            }
        );
        mediaSession.setActive(true);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent);

            String action = intent.getAction();
            if (ACTION_UPDATE_STATE.equals(action)) {
                currentTitle = intent.getStringExtra(EXTRA_TITLE);
                currentSubtitle = intent.getStringExtra(EXTRA_SUBTITLE);
                isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false);
                updateSessionAndNotification();
            } else if (ACTION_PLAY_PAUSE.equals(action)) {
                forward(isPlaying ? "pause" : "play");
            } else if (ACTION_NEXT.equals(action)) {
                forward("next");
            } else if (ACTION_PREVIOUS.equals(action)) {
                forward("previous");
            } else if (ACTION_STOP.equals(action)) {
                forward("stop");
            }
        }
        return START_STICKY;
    }

    private void forward(String action) {
        RadioMediaPlugin plugin = RadioMediaPlugin.getInstance();
        if (plugin != null) {
            plugin.notifyMediaButton(action);
        }
    }

    private void updateSessionAndNotification() {
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1f
            );
        mediaSession.setPlaybackState(stateBuilder.build());

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSubtitle)
            .build();
        mediaSession.setMetadata(metadata);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        PendingIntent playPauseIntent = servicePendingIntent(ACTION_PLAY_PAUSE);
        PendingIntent nextIntent = servicePendingIntent(ACTION_NEXT);
        PendingIntent previousIntent = servicePendingIntent(ACTION_PREVIOUS);

        int playPauseIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;

        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtitle == null || currentSubtitle.isEmpty() ? "Israel Radio" : currentSubtitle)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_previous, "Previous", previousIntent)
            .addAction(playPauseIcon, isPlaying ? "Pause" : "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                new MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private PendingIntent servicePendingIntent(String action) {
        Intent intent = new Intent(this, RadioPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Radio playback",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Now-playing controls for Israel Radio");
            channel.setShowBadge(false);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mediaSession.setActive(false);
        mediaSession.release();
        super.onDestroy();
    }
}
