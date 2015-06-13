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

package com.android.globalsearch;

import android.app.SearchManager;
import android.content.ContentResolver;
import android.net.Uri;
import android.test.AndroidTestCase;

/**
 * Tests the permissions protecting providers in global search.
 */
public class ProvidersPermissionTest extends AndroidTestCase {

    public void testSuggestionProviderRequires_GET_SUGGESTIONS() {

        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("com.android.globalsearch.SuggestionProvider")
                .appendPath(SearchManager.SUGGEST_URI_PATH_QUERY)
                .query("")
                .fragment("")
                .build();

        assertReadingContentUriRequiresPermission(uri, android.Manifest.permission.GLOBAL_SEARCH);
    }

    public void testStatsProviderRequires_WRITE_STATS() {
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(SearchManager.SEARCH_CLICK_REPORT_AUTHORITY)
                .appendPath(SearchManager.SEARCH_CLICK_REPORT_URI_PATH)
                .query("")
                .fragment("")
                .build();

        assertWritingContentUriRequiresPermission(uri, android.Manifest.permission.GLOBAL_SEARCH);
    }
}
