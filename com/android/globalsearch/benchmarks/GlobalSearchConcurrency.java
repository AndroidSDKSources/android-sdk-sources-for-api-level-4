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

import android.content.ComponentName;

/**
 * Latency tests for suggestion sources.
 */

/*

To build and run:

mmm packages/apps/GlobalSearch/benchmarks \
&& adb -e install -r $OUT/system/app/GlobalSearchBenchmarks.apk \
&& sleep 10 \
&& adb -e shell am start -a android.intent.action.MAIN \
        -n com.android.globalsearch.benchmarks/.GlobalSearchConcurrency \
&& adb -e logcat

 */

public class GlobalSearchConcurrency extends SourceLatency {

    private static final String QUERY = "hillary clinton";

    // Delay between queries (in milliseconds).
    private static final long DELAY_MS = 150;

    private static ComponentName GLOBAL_SEARCH_COMPONENT =
            new ComponentName("com.android.globalsearch",
                "com.android.globalsearch.GlobalSearch");

    @Override
    protected void onResume() {
        super.onResume();
        testGlobalSearchConcurrency();
    }

    private void testGlobalSearchConcurrency() {
        checkSourceConcurrent("GLOBAL", GLOBAL_SEARCH_COMPONENT, QUERY, DELAY_MS);
    }
}
