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
import java.util.List;

/**
 * Base class for filtering suggestions.
 */
public abstract class SuggestionFilter {

    /**
     * Checks whether a give suggestion should pass the filter.
     *
     * @param suggestion The suggestion to check.
     * @return <code>true</code> if the suggestion should be kept.
     */
    public abstract boolean shouldKeepSuggestion(SuggestionData suggestion);

    /**
     * Filters a suggestion result.
     *
     * @param result Suggestions to filter.
     * @return A suggestion result with the same source as the given result,
     *         and only those suggestions for which {@link #shouldKeepSuggestion(SuggestionData)}
     *         returns <code>true</code>.
     */
    public SuggestionResult filter(SuggestionResult result) {
        final List<SuggestionData> old = result.getSuggestions();
        // Uses the original list size as capacity to avoid reallocation.
        // This may waste some space, but reduces allocation and GC.
        final ArrayList<SuggestionData> filtered = new ArrayList<SuggestionData>(old.size());
        for (SuggestionData suggestion : old) {
            if (shouldKeepSuggestion(suggestion)) {
                filtered.add(suggestion);
            }
        }
        return new SuggestionResult(result.getSource(), filtered, result.getCount(),
                result.getQueryLimit());
    }

}
