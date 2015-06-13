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

import java.util.concurrent.Callable;

/**
 * Defines what is expected of each source or corpus of suggestions in the global
 * suggestion provider.
 */
public interface SuggestionSource {

    /**
     * Gets the name of the activity that this source is for. When a suggestion is
     * clicked, the resulting intent will be sent to this activity. Also, any icon
     * resource IDs will be resolved relative to the package that this activity
     * belongs to.
     */
    ComponentName getComponentName();

    /**
     * Gets the localized, human-readable label for this source.
     */
    String getLabel();

    /**
     * Gets the icon for this suggestion source as an android.resource: URI.
     */
    String getIcon();
    
    /**
     * Gets the description to use for this source in system search settings.
     */
    String getSettingsDescription();

    /**
     *
     *  Note: this does not guarantee that this source will be queried for queries of
     *  this length or longer, only that it will not be queried for anything shorter.
     *
     * @return The minimum number of characters needed to trigger this source.
     */
    int getQueryThreshold();

    /**
     * Indicates whether a source should be invoked for supersets of queries it has returned zero
     * results for in the past.  For example, if a source returned zero results for "bo", it would
     * be ignored for "bob".
     *
     * If set to <code>false</code>, this source will only be ignored for a single session; the next
     * time the search dialog is brought up, all sources will be queried.
     *
     * @return <code>true</code> if this source should be queried after returning no results.
     */
    boolean queryAfterZeroResults();

    /**
     * Gets a {@link Callable} task that will produce a {@link SuggestionResult} for the given
     * query.
     *
     * @param query The user query.
     * @param maxResults The maximum number of suggestions that the source should return
     *        in {@link SuggestionResult#getSuggestions()}.
     *        If more suggestions are returned, the caller may discard all the returned
     *        suggestions.
     * @param queryLimit An advisory maximum number that the source should return
     *        in {@link SuggestionResult#getCount()}.
     * @return A callable that will produce a suggestion result.
     */
    Callable<SuggestionResult> getSuggestionTask(String query, int maxResults, int queryLimit);

    /**
     * Validates shortcut.  The {@link Callable} returns a {@link SuggestionData} with the up to
     * date information for the shortcut if the shortcut is still valid, or <code>null</code>
     * otherwise.
     *
     * @param shortcut The old shortcut.
     * @return A callable that will produce the result.
     */
    Callable<SuggestionData> getShortcutValidationTask(SuggestionData shortcut);

}
