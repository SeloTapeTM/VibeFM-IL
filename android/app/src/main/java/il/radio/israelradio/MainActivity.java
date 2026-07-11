package il.radio.israelradio;

import android.media.AudioManager;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(RadioMediaPlugin.class);
        super.onCreate(savedInstanceState);
        // Route hardware volume keys to media volume, not ringer volume.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }
}
