/*
 * Copyright 2013-2015 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.gms.gcm;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import org.microg.gms.checkin.LastCheckinInfo;

import static org.microg.gms.gcm.McsConstants.ACTION_CONNECT;
import static org.microg.gms.gcm.McsConstants.ACTION_HEARTBEAT;
import static org.microg.gms.gcm.McsConstants.EXTRA_REASON;

public class TriggerReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = "GmsGcmTrigger";
    private static final String PREF_ENABLE_GCM = "gcm_enable_mcs_service";

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean force = "android.provider.Telephony.SECRET_CODE".equals(intent.getAction());
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_ENABLE_GCM, false) || force) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                McsService.resetCurrentDelay();
            }
            if (LastCheckinInfo.read(context).androidId == 0) {
                Log.d(TAG, "Ignoring " + intent + ": need to checkin first.");
                return;
            }

            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected() || force) {
                if (!McsService.isConnected() || force) {
                    Log.d(TAG, "Not connected to GCM but should be, asking the service to start up");
                    startWakefulService(context, new Intent(ACTION_CONNECT, null, context, McsService.class)
                            .putExtra(EXTRA_REASON, intent));
                } else {
                    if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                        Log.d(TAG, "Ignoring " + intent + ": service is running. schedule reconnect instead.");
                        McsService.scheduleReconnect(context);
                    } else {
                        Log.d(TAG, "Ignoring " + intent + ": service is running. heartbeat instead.");
                        startWakefulService(context, new Intent(ACTION_HEARTBEAT, null, context, McsService.class)
                                .putExtra(EXTRA_REASON, intent));
                    }
                }
            } else {
                Log.d(TAG, "Ignoring " + intent + ": network is offline, scheduling new attempt.");
                McsService.scheduleReconnect(context);
            }
        } else {
            Log.d(TAG, "Ignoring " + intent + ": gcm is disabled");
        }
    }

}
