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

/**
 * Contains methods to create special results for the suggestion list such as "search the web",
 * "more results" and the corpus results.
 */
public interface SuggestionFactory extends SourceSuggestionBacker.MoreExpanderFactory,
        SourceSuggestionBacker.CorpusResultFactory {


    /**
     * Creates a one-off suggestion for searching the web with the current query.
     * The description can be a format string with one string value, which will be
     * filled in by the provided query argument.
     *
     * @param query The query
     */
    public SuggestionData createSearchTheWebSuggestion(String query);

    /**
     * Creates a one-off suggestion for visiting the url specified by the current query,
     * or null if the current query does not look like a url.
     *
     * @param query The query
     */
    public SuggestionData createGoToWebsiteSuggestion(String query);

}
