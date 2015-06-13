/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.globalsearch.benchmarks;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;


/*

To build and run:

mmm packages/apps/GlobalSearch/benchmarks \
&& adb -e install -r $OUT/system/app/GlobalSearchBenchmarks.apk \
&& sleep 10 \
&& adb -e shell am start -a android.intent.action.MAIN \
        -n com.android.globalsearch.benchmarks/.UserVisibleLatency \
&& adb -e logcat

 */
public class UserVisibleLatency extends Activity {

    private static final String TAG = "UserVisibleLatency";

    private SearchManager mSearchManager;

    private boolean mSearchStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_visible_latency);

        mSearchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged(" + hasFocus + ")");

        if (hasFocus && !mSearchStarted) {
            mSearchManager.startSearch("", false, getComponentName(), null, true);
            new InputThread("pub").start();
        }
    }

    private class InputThread extends Thread {

        private String mInput;

        public InputThread(String input) {
            mInput = input;
        }

        @Override
        public void run() {
            sendStringSync(mInput);
        }

        public void sendStringSync(String text) {
            if (text == null) {
                return;
            }
            KeyCharacterMap keyCharacterMap =
                    KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);

            KeyEvent[] events = keyCharacterMap.getEvents(text.toCharArray());

            if (events != null) {
                for (int i = 0; i < events.length; i++) {
                    sendKeySync(events[i]);
                }
            }
        }

        public void sendKeySync(KeyEvent event) {
            try {
                (IWindowManager.Stub.asInterface(ServiceManager.getService("window")))
                        .injectKeyEvent(event, true);
            } catch (RemoteException e) {
            }
        }

    }

}
