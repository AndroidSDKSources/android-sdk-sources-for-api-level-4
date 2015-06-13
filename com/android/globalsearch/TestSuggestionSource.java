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

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;

/**
 * For testing, delivers a canned set of suggestions after fixed delay.
 */
class TestSuggestionSource extends AbstractSuggestionSource {

    private final ComponentName mComponent;
    private final long mDelay;
    private final Map<String, List<SuggestionData>> mCannedResponses;
    private final HashSet<String> mErrorQueries;
    private final boolean mQueryAfterZeroResults;
    private final String mLabel;

    private TestSuggestionSource(
            ComponentName component,
            long delay,
            Map<String, List<SuggestionData>> cannedResponses,
            HashSet<String> errorQueries, boolean queryAfterZeroResults, String label) {
        mComponent = component;
        mDelay = delay;
        mCannedResponses = cannedResponses;
        mErrorQueries = errorQueries;
        mQueryAfterZeroResults = queryAfterZeroResults;
        mLabel = label;
    }


    public ComponentName getComponentName() {
        return mComponent;
    }

    public String getIcon() {
        return null;
    }

    public String getLabel() {
        return mLabel;
    }
    
    public String getSettingsDescription() {
        return "settings description";
    }

    @Override
    protected SuggestionResult getSuggestions(String query, int maxResults, int queryLimit) {
        if (mDelay > 0) {
            try {
                Thread.sleep(mDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return mEmptyResult;
            }
        }

        if (mErrorQueries.contains(query)) {
            return SuggestionResult.createErrorResult(this);
        }

        final List<SuggestionData> result = mCannedResponses.get(query);
        return result != null ?
                new SuggestionResult(this, result) :
                mEmptyResult;
    }

    @Override
    protected SuggestionData validateShortcut(SuggestionData shortcut) {
        String shortcutId = shortcut.getShortcutId();
        for (List<SuggestionData> suggestionDatas : mCannedResponses.values()) {
            for (SuggestionData suggestionData : suggestionDatas) {
                if (shortcutId.equals(suggestionData.getShortcutId())) {
                    return suggestionData;
                }
            }
        }
        return null;
    }

    public boolean queryAfterZeroResults() {
        return mQueryAfterZeroResults;
    }

    @Override
    public String toString() {
        return mComponent.toShortString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TestSuggestionSource that = (TestSuggestionSource) o;

        if (mDelay != that.mDelay) return false;
        if (mQueryAfterZeroResults != that.mQueryAfterZeroResults) return false;
        if (mCannedResponses != null ?
                !mCannedResponses.equals(that.mCannedResponses)
                : that.mCannedResponses != null)
            return false;
        if (!mComponent.equals(that.mComponent)) return false;
        if (mLabel != null ? !mLabel.equals(that.mLabel) : that.mLabel != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = mComponent.hashCode();
        result = 31 * result + (int) (mDelay ^ (mDelay >>> 32));
        result = 31 * result + (mCannedResponses != null ? mCannedResponses.hashCode() : 0);
        result = 31 * result + (mQueryAfterZeroResults ? 1 : 0);
        result = 31 * result + (mLabel != null ? mLabel.hashCode() : 0);
        return result;
    }

    /**
     * Makes a test source that will returned 1 canned result matching the given query.
     */
    public static SuggestionSource makeCanned(String query, String s, long delay) {
        final ComponentName name = new ComponentName("com.test." + s, "Class$" + s);

        final SuggestionData suggestion = new SuggestionData.Builder(name)
                .title(s)
                .intentAction("view")
                .intentData(s)
                .build();

        return new TestSuggestionSource.Builder()
                .setComponent(name)
                .setLabel(s)
                .setDelay(delay)
                .addCannedResponse(query, suggestion)
                .create();
    }

    static class Builder {
        private ComponentName mComponent =
                new ComponentName(
                        "com.android.globalsearch", "com.android.globalsearch.GlobalSearch");
        private long mDelay = 0;
        private Map<String, List<SuggestionData>> mCannedResponses =
                new HashMap<String, List<SuggestionData>>();
        private HashSet<String> mErrorQueries = new HashSet<String>();
        private boolean mQueryAfterZeroResults = false;
        private String mLabel = "TestSuggestionSource";

        public Builder setComponent(ComponentName component) {
            mComponent = component;
            return this;
        }

        public Builder setDelay(long delay) {
            mDelay = delay;
            return this;
        }

        public Builder setQueryAfterZeroResults(boolean queryAfterNoResults) {
            mQueryAfterZeroResults = queryAfterNoResults;
            return this;
        }

        public Builder setLabel(String label) {
            mLabel = label;
            return this;
        }

        /**
         * Adds a list of canned suggestions as a result for the query and all prefixes of it.
         *
         * @param query The query
         * @param suggestions The suggetions to return for the query and its prefixes.
         */
        public Builder addCannedResponse(String query, SuggestionData... suggestions) {
            final List<SuggestionData> suggestionList = Arrays.asList(suggestions);
            mCannedResponses.put(query, suggestionList);

            final int queryLength = query.length();
            for (int i = 1; i < queryLength; i++) {
                final String subQuery = query.substring(0, queryLength - i);
                mCannedResponses.put(subQuery, suggestionList);
            }
            return this;
        }

        /**
         * Makes it so the source will return a <code>null</code> result for this query.
         */
        public Builder addErrorResponse(String query) {
            mErrorQueries.add(query);
            return this;
        }

        public TestSuggestionSource create() {
            return new TestSuggestionSource(
                    mComponent, mDelay, mCannedResponses, mErrorQueries,
                    mQueryAfterZeroResults, mLabel);
        }
    }
}
