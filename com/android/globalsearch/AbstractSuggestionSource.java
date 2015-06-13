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

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extending this class can make it easier to implement {@link SuggestionSource} in creating
 * a {@link Callable} for you by calling {@link #getSuggestions}.
 */
public abstract class AbstractSuggestionSource implements SuggestionSource {

    // Regular expression which matches http:// or https://, followed by some stuff, followed by
    // optionally a trailing slash, all matched as separate groups.
    private static final Pattern STRIP_URL_PATTERN = Pattern.compile("^(https?://)(.*?)(/$)?");

    protected final SuggestionResult mEmptyResult;

    public AbstractSuggestionSource() {
        mEmptyResult = new SuggestionResult(this);
    }

    /**
     * Gets a list of suggestions for the given query.
     *
     * @param query The query.
     * @param maxResults The maximum number of suggestions that the source should return
     *        in {@link SuggestionResult#getSuggestions()}.
     *        If more suggestions are returned, the caller may discard all the returned
     *        suggestions.
     * @param queryLimit An advisory maximum number that the source should return
     *        in {@link SuggestionResult#getCount()}.
     * @return A list of suggestions, ordered by descending relevance if applicable.
     */
    protected abstract SuggestionResult getSuggestions(String query, int maxResults,
            int queryLimit);

    /**
     * This method returns <code>0</code>.
     */
    public int getQueryThreshold() {
        return 0;
    }

    /** {@inheritDoc} */
    public Callable<SuggestionResult> getSuggestionTask(final String query, final int maxResults,
            final int queryLimit) {
        return new Callable<SuggestionResult>() {

            public SuggestionResult call() throws Exception {
                if (Thread.interrupted()) {
                    // The suggestion request was canceled before it had time to start.
                    // Since the request has been canceled, nobody will look at the results,
                    // so we don't need to return any.
                    return SuggestionResult.createCancelled(AbstractSuggestionSource.this);
                }
                return getSuggestions(query, maxResults, queryLimit);
            }
        };
    }

    /** {@inheritDoc} */
    public Callable<SuggestionData> getShortcutValidationTask(final SuggestionData shortcut) {
        if (shortcut == null) {
            throw new IllegalArgumentException("shortcut must not be null");
        }
        final String shortcutId = shortcut.getShortcutId();
        if (shortcutId == null) {
            throw new IllegalArgumentException("shortcut id must not be null");
        }
        if (SearchManager.SUGGEST_NEVER_MAKE_SHORTCUT.equals(shortcutId)) {
            throw new IllegalArgumentException("makes no sense to validate a shortcut that is "
                    + "never valid");
        }
        return new Callable<SuggestionData>() {
            public SuggestionData call() throws Exception {
                return validateShortcut(shortcut);
            }
        };
    }

    /**
     * Validates a shortcut.  Returns a {@link SuggestionData} with the up to date information for
     * the shortcut if the shortcut is still valid, or <code>null</code> if the shortcut is not
     * valid.
     *
     * @param shortcut The old shortcut.
     * @return a {@link SuggestionData} with the up to date information for the shortcut if the
     *   shortcut is still valid, or <code>null</code> otherwise.
     */
    protected abstract SuggestionData validateShortcut(SuggestionData shortcut);

    /**
     * Strips the provided url of preceding "http://" or "https://" and any trailing "/".
     * If the provided string cannot be stripped, the original string is returned.
     *
     * @param url a url to strip, like "http://www.google.com/"
     * @return a stripped url like "www.google.com", or the original string if it could
     *         not be stripped
     */
    public static String stripUrl(String url) {
        if (url == null) return null;
        Matcher m = STRIP_URL_PATTERN.matcher(url);
        if (m.matches() && m.groupCount() == 3) {
            return m.group(2);
        } else {
            return url;
        }
    }
}
