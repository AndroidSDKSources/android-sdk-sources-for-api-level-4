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

import java.util.ArrayList;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * A write only content provider made availalbe to SearchDialog to report click when a user searches
 * in global search, pivots into an app, and clicks on a result.
 */
public class StatsProvider extends ContentProvider {
    private ShortcutRepository mShortcutRepo;

    @Override
    public boolean onCreate() {
        mShortcutRepo = ShortcutRepositoryImplLog.create(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        ComponentName name = ComponentName.unflattenFromString(values.getAsString(
                SearchManager.SEARCH_CLICK_REPORT_COLUMN_COMPONENT));
        String query = values.getAsString(SearchManager.SEARCH_CLICK_REPORT_COLUMN_QUERY);
        
        // Don't shortcut if this is not a promoted source.
        boolean promotedSource = false;
        ArrayList<ComponentName> sourceRanking = mShortcutRepo.getSourceRanking();
        for (int i = 0; i < SuggestionSession.NUM_PROMOTED_SOURCES && i < sourceRanking.size();
                i++) {
            if (name.equals(sourceRanking.get(i))) {
                promotedSource = true;
            }
        }
        if (!promotedSource) return null;

        final SuggestionData suggestionData = new SuggestionData.Builder(name)
                .format(values.getAsString(SearchManager.SUGGEST_COLUMN_FORMAT))
                .title(values.getAsString(SearchManager.SUGGEST_COLUMN_TEXT_1))
                .description(values.getAsString(SearchManager.SUGGEST_COLUMN_TEXT_2))
                .icon1(values.getAsString(SearchManager.SUGGEST_COLUMN_ICON_1))
                .icon2(values.getAsString(SearchManager.SUGGEST_COLUMN_ICON_2))
                .intentQuery(values.getAsString(SearchManager.SUGGEST_COLUMN_QUERY))
                .intentAction(values.getAsString(SearchManager.SUGGEST_COLUMN_INTENT_ACTION))
                .intentData(values.getAsString(SearchManager.SUGGEST_COLUMN_INTENT_DATA))
                .intentExtraData(values.getAsString(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA))
                .intentComponentName(
                        values.getAsString(SearchManager.SUGGEST_COLUMN_INTENT_COMPONENT_NAME))
                .shortcutId(values.getAsString(SearchManager.SUGGEST_COLUMN_SHORTCUT_ID))
                // note: deliberately omitting background color since it is only for global search
                // "more results" entries
                .build();

        mShortcutRepo.reportStats(new SessionStats(query, suggestionData));
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
