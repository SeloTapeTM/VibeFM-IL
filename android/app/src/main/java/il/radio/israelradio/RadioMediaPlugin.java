package il.radio.israelradio;

import android.content.Intent;
import android.view.WindowManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Bridges the WebView player (index.html) with the native foreground
 * service that owns the MediaSession, lock-screen/notification controls
 * and hardware media button handling.
 */
@CapacitorPlugin(name = "RadioMedia")
public class RadioMediaPlugin extends Plugin {

    private static RadioMediaPlugin instance;

    @Override
    public void load() {
        instance = this;
    }

    static RadioMediaPlugin getInstance() {
        return instance;
    }

    void notifyMediaButton(String action) {
        JSObject data = new JSObject();
        data.put("action", action);
        notifyListeners("mediaButton", data);
    }

    @PluginMethod
    public void updateState(PluginCall call) {
        String title = call.getString("title", "Israel Radio");
        String subtitle = call.getString("subtitle", "");
        boolean playing = Boolean.TRUE.equals(call.getBoolean("playing", false));

        Intent intent = new Intent(getContext(), RadioPlaybackService.class);
        intent.setAction(RadioPlaybackService.ACTION_UPDATE_STATE);
        intent.putExtra(RadioPlaybackService.EXTRA_TITLE, title);
        intent.putExtra(RadioPlaybackService.EXTRA_SUBTITLE, subtitle);
        intent.putExtra(RadioPlaybackService.EXTRA_PLAYING, playing);
        getContext().startForegroundService(intent);

        call.resolve();
    }

    @PluginMethod
    public void setKeepScreenOn(PluginCall call) {
        boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
        getActivity().runOnUiThread(() -> {
            if (enabled) {
                getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        call.resolve();
    }
}
