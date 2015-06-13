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

import android.content.ComponentName;

import java.util.Collection;
import java.util.ArrayList;

import com.google.android.collect.Lists;

/**
 * Holds the stats recorded during a single {@link SuggestionSession}.  For now, just the
 * suggestion data that was clicked on, if any.
 */
public class SessionStats {

    private final String mQuery;
    private final SuggestionData mClicked;
    private final Collection<ComponentName> mSourceImpressions;

    SessionStats(String query, SuggestionData clicked) {
        mQuery = query;
        mClicked = clicked;
        if (clicked == null) {
            mSourceImpressions = new ArrayList<ComponentName>();
        } else {
            mSourceImpressions = Lists.newArrayList(clicked.getSource());
        }
    }

    public SessionStats(
            String query, SuggestionData clicked, Collection<ComponentName> sourceImpressions) {
        mQuery = query;
        mClicked = clicked;
        mSourceImpressions = sourceImpressions;
    }

    public String getQuery() {
        return mQuery;
    }

    public SuggestionData getClicked() {
        return mClicked;
    }

    public Collection<ComponentName> getSourceImpressions() {
        return mSourceImpressions;
    }
}
