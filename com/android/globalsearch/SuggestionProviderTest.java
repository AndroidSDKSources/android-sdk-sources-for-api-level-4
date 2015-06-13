/*
 * Copyright (C) The Android Open Source Project
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

import android.database.Cursor;
import android.net.Uri;
import android.test.AndroidTestCase;

/**
 * Tests for {@link SuggestionProvider}
 */
public class SuggestionProviderTest extends AndroidTestCase {

    private static final String URI_PREFIX = 
            "content://com.android.globalsearch.SuggestionProvider/search_suggest_query/";

    public void testZeroQuery() {
        Uri uri = Uri.parse(URI_PREFIX);
        Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
        assertNotNull(cursor);
    }

    public void testShortQuery() {
        Uri uri = Uri.parse(URI_PREFIX + "a");
        Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
        assertNotNull(cursor);
    }

    public void testLongQuery() {
        Uri uri = Uri.parse(URI_PREFIX + "android");
        Cursor cursor = getContext().getContentResolver().query(uri, null, null, null, null);
        assertNotNull(cursor);
    }

}
