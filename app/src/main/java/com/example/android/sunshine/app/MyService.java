package com.example.android.sunshine.app;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import com.example.android.sunshine.app.sync.SunshineSyncAdapter;

public class MyService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        if (messageEvent.getPath().equals("/update_path")) {
            SunshineSyncAdapter.syncImmediately(this);
        } else {
            super.onMessageReceived(messageEvent);
        }
    }
}
