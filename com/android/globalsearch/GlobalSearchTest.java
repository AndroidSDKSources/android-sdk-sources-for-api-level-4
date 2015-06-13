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

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.server.search.SearchableInfo;
import android.test.AndroidTestCase;

/**
 * Tests for {@link GlobalSearch}
 */
public class GlobalSearchTest extends AndroidTestCase {

    private static final ComponentName GLOBAL_SEARCH_COMPONENT
            = new ComponentName("com.android.globalsearch", 
                    "com.android.globalsearch.GlobalSearch");
    
    public void testDefaultSearchable() {
        SearchManager searchManager = (SearchManager)
                getContext().getSystemService(Context.SEARCH_SERVICE);
        SearchableInfo si = searchManager.getSearchableInfo(null, true);
        assertNotNull("No default searchable.", si);
        assertEquals("GlobalSearch is not the default searchable.", 
                GLOBAL_SEARCH_COMPONENT, si.getSearchActivity());
    }

}
