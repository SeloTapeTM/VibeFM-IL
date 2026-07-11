package il.radio.israelradio;

import android.media.AudioManager;
import android.os.Bundle;
import android.webkit.WebSettings;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RadioMediaPlugin.class);
        super.onCreate(savedInstanceState);
        // Route hardware volume keys to media volume, not ringer volume.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // The app loads over https (see capacitor.config.json), but some
        // radio streams are plain http. cleartextTrafficPermitted in the
        // network security config only allows the traffic - mixed content
        // mode is what actually lets the WebView load it from an https page.
        this.bridge.getWebView().getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
}
