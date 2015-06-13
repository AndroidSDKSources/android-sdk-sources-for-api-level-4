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
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Collection;

/**
 * Source suggestion backer shows (that is, snapshots) the results in the following order:
 * - go to website (if applicable)
 * - shortcuts
 * - results from promoted sources that reported in time
 * - a "search the web for 'query'" entry
 * - a "more" item that, when expanded is followed by
 * - an entry for each promoted source that has more results than was displayed above
 * - an entry for each promoted source that reported too late
 * - an entry for each non-promoted source
 *
 * The "search the web" and "more" entries appear only after the promoted sources are given
 * a chance to return their results (either they all return their results, or the timeout elapses).
 *
 * Some set of sources are deemed 'promoted' at the begining via {@link #mPromotedSources}.  These
 * are the sources that will get their results shown at the top.  However, if a promoted source
 * fails to report within {@link #mPromotedSourceDeadline}, they will be removed from the promoted
 * list, and only shown in the "more results" section.
 */
public class SourceSuggestionBacker extends SuggestionBacker {

    private static final boolean DBG = false;
    private static final String TAG = "GlobalSearch";
    private int mIndexOfMore;
    private boolean mShowingMore;

    private final String mQuery;

    interface MoreExpanderFactory {

        /**
         * @param expanded Whether the entry should appear expanded.
         * @param sourceStats The entries that will appear under "more results".
         * @return An entry that will hold the "more results" toggle / expander.
         */
        SuggestionData getMoreEntry(boolean expanded, List<SourceStat> sourceStats);
    }

    interface CorpusResultFactory {

        /**
         * Creates a result to be shown representing the results available for a corpus.
         *
         * @param query The query
         * @param sourceStat Information about the source.
         * @return A result displaying this information.
         */
        SuggestionData getCorpusEntry(String query, SourceStat sourceStat);
    }

    private final List<SuggestionData> mShortcuts;
    private final List<SuggestionSource> mSources;
    private final HashSet<ComponentName> mPromotedSources;
    private final SuggestionSource mSelectedWebSearchSource;
    private final SuggestionData mGoToWebsiteSuggestion;
    private final SuggestionData mSearchTheWebSuggestion;
    private final MoreExpanderFactory mMoreFactory;
    private final CorpusResultFactory mCorpusFactory;
    private final int mMaxPromotedSlots;
    private final long mPromotedSourceDeadline;
    private long mPromotedQueryStartTime;

    // The suggestion to pin to the bottom of the list, if any, coming from the web search source.
    // This is used by the Google search provider to pin a "Manage search history" item to the
    // bottom whenever we show search history related suggestions.
    private SuggestionData mPinToBottomSuggestion;

    private final LinkedHashMap<ComponentName, SuggestionResult> mReportedResults =
            new LinkedHashMap<ComponentName, SuggestionResult>();
    private final HashSet<ComponentName> mReportedBeforeDeadline
            = new HashSet<ComponentName>();

    private final HashSet<String> mSuggestionKeys = new HashSet<String>();

    private final HashSet<ComponentName> mPendingSources
            = new HashSet<ComponentName>();

    // non-promoted sources that have been viewed (snapshotted while "more" was expanded).
    // we keep track of this because we never want to remove a source that we have shown
    // before, even if they end up having zero results
    private final HashSet<ComponentName> mViewedNonPromoted
            = new HashSet<ComponentName>();

    /**
     * @param query
     * @param shortcuts To be shown at top.
     * @param sources The sources that are either part of the cached results, or that are expected
     *        to report.
     * @param promotedSources The promoted sources expecting to report
     * @param selectedWebSearchSource the currently selected web search source
     * @param cachedResults Any results we are already privy to
     * @param goToWebsiteSuggestion The "go to website" entry to show if appropriate
     * @param searchTheWebSuggestion The "search the web" entry to show if appropriate
     * @param maxPromotedSlots The maximum numer of results to show for the promoted sources
     * @param promotedSourceDeadline How long to wait for the promoted sources before mixing in the
     *        results and displaying the "search the web" and "more results" entries.
     * @param moreFactory How to create the expander entry
     * @param corpusFactory How to create results for each corpus
     */
    public SourceSuggestionBacker(
            String query,
            List<SuggestionData> shortcuts,
            List<SuggestionSource> sources,
            HashSet<ComponentName> promotedSources,
            SuggestionSource selectedWebSearchSource,
            Collection<SuggestionResult> cachedResults,
            SuggestionData goToWebsiteSuggestion,
            SuggestionData searchTheWebSuggestion,
            int maxPromotedSlots,
            long promotedSourceDeadline,
            MoreExpanderFactory moreFactory,
            CorpusResultFactory corpusFactory) {

        if (promotedSources.size() > maxPromotedSlots) {
            throw new IllegalArgumentException("more promoted sources than there are slots " +
                    "provided");
        }

        mQuery = query;
        mShortcuts = shortcuts;
        mGoToWebsiteSuggestion = goToWebsiteSuggestion;
        mSearchTheWebSuggestion = searchTheWebSuggestion;
        mMoreFactory = moreFactory;
        mPromotedSourceDeadline = promotedSourceDeadline;
        mCorpusFactory = corpusFactory;
        mSources = sources;
        mPromotedSources = promotedSources;
        mMaxPromotedSlots = maxPromotedSlots;
        mSelectedWebSearchSource = selectedWebSearchSource;

        mPromotedQueryStartTime = getNow();

        final int numShortcuts = shortcuts.size();
        for (int i = 0; i < numShortcuts; i++) {
            final SuggestionData shortcut = shortcuts.get(i);
            mSuggestionKeys.add(makeSuggestionKey(shortcut));
        }

        for (SuggestionResult cachedResult : cachedResults) {
            addSourceResults(cachedResult);
        }
    }

    /**
     * Sets the time that the promoted sources were queried, if different from the creation
     * time.  This is necessary when the backer is created, but the sources are queried after
     * a delay.
     */
    public synchronized void reportPromotedQueryStartTime() {
        mPromotedQueryStartTime = getNow();
    }

    /**
     * @return Whether the deadline has passed for promoted sources to report before mixing in
     *   the rest of the results and displaying the "search the web" and "more results" entries.
     */
    private boolean isPastDeadline() {
        return getNow() - mPromotedQueryStartTime >= mPromotedSourceDeadline;
    }

    @Override
    public synchronized void snapshotSuggestions(
            ArrayList<SuggestionData> dest, boolean expandAdditional) {
        if (DBG) Log.d(TAG, "snapShotSuggestions");
        mIndexOfMore = snapshotSuggestionsInternal(dest, expandAdditional);
    }

    /**
     * @return the index of the "more results" entry, or if there is no "more results" entry,
     *   something large enough so that the index will never be requested (e.g the size).
     */
    private int snapshotSuggestionsInternal(
            ArrayList<SuggestionData> dest, boolean expandAdditional) {
        dest.clear();

        // Add 'go to website' right at top if applicable.
        if (mGoToWebsiteSuggestion != null) {
            if (DBG) Log.d(TAG, "snapshot: adding 'go to website'");
            dest.add(mGoToWebsiteSuggestion);
        }

        // start with all shortcuts
        dest.addAll(mShortcuts);

        final int promotedSlotsAvailable = mMaxPromotedSlots - mShortcuts.size();
        final int chunkSize = mPromotedSources.isEmpty() ?
                0 :
                Math.max(1, promotedSlotsAvailable / mPromotedSources.size());

        // grab reported results from promoted sources that reported before the deadline
        ArrayList<Iterator<SuggestionData>> reportedResults =
                new ArrayList<Iterator<SuggestionData>>(mReportedResults.size());
        for (SuggestionResult suggestionResult : mReportedResults.values()) {
            final ComponentName name = suggestionResult.getSource().getComponentName();
            if (mPromotedSources.contains(name)
                    && mReportedBeforeDeadline.contains(name)
                    && !suggestionResult.getSuggestions().isEmpty()) {
                reportedResults.add(suggestionResult.getSuggestions().iterator());
            }
        }

        HashMap<ComponentName, Integer> sourceToNumDisplayed =
                new HashMap<ComponentName, Integer>();

        // fill in chunk size
        int numSlotsUsed = 0;
        for (Iterator<SuggestionData> reportedResult : reportedResults) {
            for (int i = 0; i < chunkSize && reportedResult.hasNext(); i++) {
                final SuggestionData suggestionData = reportedResult.next();
                dest.add(suggestionData);
                final Integer displayed = sourceToNumDisplayed.get(suggestionData.getSource());
                sourceToNumDisplayed.put(
                        suggestionData.getSource(), displayed == null ? 1 : displayed + 1);
                numSlotsUsed++;
            }
        }

        // if all of the promoted sources have responded (or the deadline for promoted sources
        // has passed), we use up any remaining promoted slots, and display the "more" UI
        // - one exception: shortcuts only (no sources)
        final boolean pastDeadline = isPastDeadline();
        final boolean allPromotedResponded = mReportedResults.size() >= mPromotedSources.size();
        mShowingMore = (pastDeadline || allPromotedResponded) && !mSources.isEmpty();
        if (mShowingMore) {

            if (DBG) Log.d(TAG, "snapshot: mixing in rest of results.");

            // prune out results that have nothing left
            final Iterator<Iterator<SuggestionData>> pruner = reportedResults.iterator();
            while (pruner.hasNext()) {
                Iterator<SuggestionData> suggestionDataIterator = pruner.next();
                if (!suggestionDataIterator.hasNext()) {
                    pruner.remove();
                }
            }

            // fill in remaining promoted slots, keep track of how many results from each
            // source have been displayed
            int slotsRemaining = promotedSlotsAvailable - numSlotsUsed;
            final int newChunk = reportedResults.isEmpty() ?
                    0 : Math.max(1, slotsRemaining / reportedResults.size());
            for (Iterator<SuggestionData> reportedResult : reportedResults) {
                if (slotsRemaining <= 0) break;
                for (int i = 0; i < newChunk && slotsRemaining > 0; i++) {
                    if (reportedResult.hasNext()) {
                        final SuggestionData suggestionData = reportedResult.next();
                        dest.add(suggestionData);
                        final Integer displayed =
                                sourceToNumDisplayed.get(suggestionData.getSource());
                        sourceToNumDisplayed.put(
                        suggestionData.getSource(), displayed == null ? 1 : displayed + 1);
                        slotsRemaining--;
                    } else {
                        break;
                    }
                }
            }

            // gather stats about sources so we can properly construct "more" ui
            ArrayList<SourceStat> moreSources = new ArrayList<SourceStat>();
            final boolean showingPinToBottom = (mPinToBottomSuggestion != null)
                    && mReportedBeforeDeadline.contains(mPinToBottomSuggestion.getSource());
            for (SuggestionSource source : mSources) {
                final boolean promoted = mPromotedSources.contains(source.getComponentName());
                final boolean reported = mReportedResults.containsKey(source.getComponentName());
                final boolean beforeDeadline =
                        mReportedBeforeDeadline.contains(source.getComponentName());

                if (!reported) {
                    // sources that haven't reported yet
                    final int responseStatus =
                            mPendingSources.contains(source.getComponentName()) ?
                                    SourceStat.RESPONSE_IN_PROGRESS :
                                    SourceStat.RESPONSE_NOT_STARTED;
                    moreSources.add(new SourceStat(
                            source.getComponentName(), promoted, source.getLabel(),
                            source.getIcon(), responseStatus, 0, 0));
                } else if (beforeDeadline && promoted) {
                    // promoted sources that have reported before the deadline are only in "more"
                    // if they have undisplayed results
                    final SuggestionResult sourceResult =
                            mReportedResults.get(source.getComponentName());
                    int numDisplayed = sourceToNumDisplayed.containsKey(source.getComponentName())
                            ? sourceToNumDisplayed.get(source.getComponentName()) : 0;

                    if (numDisplayed < sourceResult.getSuggestions().size()) {
                        // Decrement the number of results remaining by one if one of them
                        // is a pin-to-bottom suggestion from the web search source (since the
                        // pin to bottom will always be from the web source)
                        int numResultsRemaining = sourceResult.getCount() - numDisplayed;
                        int queryLimit = sourceResult.getQueryLimit() - numDisplayed;
                        if (showingPinToBottom && isWebSuggestionSource(source)) {
                            numResultsRemaining--;
                            queryLimit--;
                        }

                        moreSources.add(
                                new SourceStat(
                                        source.getComponentName(),
                                        promoted,
                                        source.getLabel(),
                                        source.getIcon(),
                                        SourceStat.RESPONSE_FINISHED,
                                        numResultsRemaining,
                                        queryLimit));
                    }
                } else {
                    // unpromoted sources that have reported
                    final SuggestionResult sourceResult =
                            mReportedResults.get(source.getComponentName());
                    moreSources.add(
                            new SourceStat(
                                    source.getComponentName(),
                                    false,
                                    source.getLabel(),
                                    source.getIcon(),
                                    SourceStat.RESPONSE_FINISHED,
                                    sourceResult.getCount(),
                                    sourceResult.getQueryLimit()));
                }
            }

            // add "search the web"
            if (mSearchTheWebSuggestion != null) {
                if (DBG) Log.d(TAG, "snapshot: adding 'search the web'");
                dest.add(mSearchTheWebSuggestion);
            }

            // add a pin-to-bottom suggestion if one has been found to use and its source reported
            // before the deadline
            if (showingPinToBottom) {
                if (DBG) Log.d(TAG, "snapshot: adding a pin-to-bottom suggestion");
                dest.add(mPinToBottomSuggestion);
            }

            // add "more results" if applicable
            int indexOfMore = dest.size();

            if (anyCorpusSourceVisible(moreSources)) {
                if (DBG) Log.d(TAG, "snapshot: adding 'more results' expander");

                dest.add(mMoreFactory.getMoreEntry(expandAdditional, moreSources));
                if (expandAdditional) {
                    for (int i = 0; i < moreSources.size(); i++) {
                        final SourceStat moreSource = moreSources.get(i);
                        if (shouldCorpusEntryBeVisible(moreSource)) {
                            if (DBG) Log.d(TAG, "snapshot: adding 'more' " + moreSource.getLabel());
                            dest.add(mCorpusFactory.getCorpusEntry(mQuery, moreSource));
                            mViewedNonPromoted.add(moreSource.getName());
                        }
                    }
                }
            }
            return indexOfMore;
        }
        return dest.size();
    }

    /**
     * @param sourceStats A list of source stats.
     * @return True if any of them should be visible.
     */
    private boolean anyCorpusSourceVisible(ArrayList<SourceStat> sourceStats) {
        boolean needMore = false;
        final int num = sourceStats.size();
        for (int i = 0; i < num; i++) {
            final SourceStat moreSource = sourceStats.get(i);
            if (shouldCorpusEntryBeVisible(moreSource)) {
                needMore = true;
            }
        }
        return needMore;
    }

    /**
     * @param sourceStat A corpus result stat
     * @return True if it should be visible.
     */
    private boolean shouldCorpusEntryBeVisible(SourceStat sourceStat) {
        return sourceStat.getNumResults() > 0
                || sourceStat.getResponseStatus() != SourceStat.RESPONSE_FINISHED
                || mViewedNonPromoted.contains(sourceStat.getName());
    }

    private String makeSuggestionKey(SuggestionData suggestion) {
        // calculating accurate size of string builder avoids an allocation vs starting with
        // the default size and having to expand.
        final String action = suggestion.getIntentAction() == null ?
                "none" : suggestion.getIntentAction();
        final String intentData = suggestion.getIntentData() == null ?
                "none" : suggestion.getIntentData();
        final String intentQuery = suggestion.getIntentQuery() == null ?
                "" : suggestion.getIntentQuery();
        final int alloc = action.length() + 2 + intentData.length() + intentQuery.length();
        return new StringBuilder(alloc)
                .append(suggestion.getIntentAction())
                .append('#')
                .append(suggestion.getIntentData())
                .append('#')
                .append(suggestion.getIntentQuery())
                .toString();
    }

    @Override
    public synchronized boolean reportSourceStarted(ComponentName source) {
        mPendingSources.add(source);

        // only refresh if it is one of the non-promoted sources, since that is the only case
        // where it currently results in the UI changing (the progress icon starts for the
        // corresponding corpus result).
        return !mPromotedSources.contains(source);
    }

    @Override
    public boolean hasSourceStarted(ComponentName source) {
        return mReportedResults.containsKey(source);
    }

    @Override
    protected synchronized boolean addSourceResults(SuggestionResult suggestionResult) {
        final SuggestionSource source = suggestionResult.getSource();

        // If the source is the web search source and there is a pin-to-bottom suggestion at
        // the end of the list of suggestions, store it separately, remove it from the list,
        // and keep going. The stored suggestion will be added to the very bottom of the list
        // in snapshotSuggestions.
        final List<SuggestionData> suggestions = suggestionResult.getSuggestions();
        if (isWebSuggestionSource(source)) {
            if (!suggestions.isEmpty()) {
                int lastPosition = suggestions.size() - 1;
                SuggestionData lastSuggestion = suggestions.get(lastPosition);
                if (lastSuggestion.isPinToBottom()) {
                    mPinToBottomSuggestion = lastSuggestion;
                    suggestions.remove(lastPosition);
                }
            }
        }
        // no longer pending
        mPendingSources.remove(source.getComponentName());

        // prune down dupes if necessary
        final Iterator<SuggestionData> it = suggestions.iterator();
        while (it.hasNext()) {
            SuggestionData s = it.next();
            final String key = makeSuggestionKey(s);
            if (mSuggestionKeys.contains(key)) {
                it.remove();
            } else {
                mSuggestionKeys.add(key);
            }
        }

        mReportedResults.put(source.getComponentName(), suggestionResult);
        final boolean pastDeadline = isPastDeadline();
        if (!pastDeadline) {
            mReportedBeforeDeadline.add(source.getComponentName());
        }
        return pastDeadline || !suggestionResult.getSuggestions().isEmpty();
    }

    /**
     * Compares the provided source to the selected web search source.
     */
    private boolean isWebSuggestionSource(SuggestionSource source) {
        return mSelectedWebSearchSource != null &&
                source.getComponentName().equals(mSelectedWebSearchSource.getComponentName());
    }

    @Override
    protected synchronized boolean refreshShortcut(
            ComponentName source, String shortcutId, SuggestionData refreshed) {
        final int size = mShortcuts.size();
        for (int i = 0; i < size; i++) {
            final SuggestionData shortcut = mShortcuts.get(i);
            if (shortcutId.equals(shortcut.getShortcutId())) {
                if (refreshed == null) {
                    // If we're removing this shortcut, we still need to stop the spinner in
                    // the icon2 value of any shortcut which was set to spin while refreshing.
                    if (shortcut.isSpinnerWhileRefreshing()) {
                        mShortcuts.set(i, shortcut.buildUpon().icon2(null).build());
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    mShortcuts.set(i, refreshed);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean isResultsPending() {
        return mReportedResults.size() < mPromotedSources.size();
    }

    @Override
    public boolean isShowingMore() {
        return mShowingMore;
    }

    @Override
    public int getMoreResultPosition() {
        return mIndexOfMore;
    }

    /**
     * Stats about a particular source that includes enough information to properly display
     * "more results" entries.
     */
    static class SourceStat {
        static final int RESPONSE_NOT_STARTED = 77;
        static final int RESPONSE_IN_PROGRESS = 78;
        static final int RESPONSE_FINISHED = 79;

        private final ComponentName mName;
        private final boolean mShowingPromotedResults;
        private final String mLabel;
        private final String mIcon;
        private final int mResponseStatus;
        private final int mNumResults;
        private final int mQueryLimit;

        /**
         * @param name The component name of the source.
         * @param showingPromotedResults Whether this source has anything showing in the promoted
         *        slots.
         * @param label The label.
         * @param icon The icon.
         * @param responseStatus Whether it has responded.
         * @param numResults The number of results (if applicable).
         * @param queryLimit The number of results requested from the source.
         */
        SourceStat(ComponentName name, boolean showingPromotedResults, String label, String icon,
                   int responseStatus, int numResults, int queryLimit) {
            switch (responseStatus) {
                case RESPONSE_NOT_STARTED:
                case RESPONSE_IN_PROGRESS:
                case RESPONSE_FINISHED:
                    break;
                default:
                    throw new IllegalArgumentException("invalid response status");
            }

            this.mName = name;
            mShowingPromotedResults = showingPromotedResults;
            this.mLabel = label;
            this.mIcon = icon;
            this.mResponseStatus = responseStatus;
            this.mNumResults = numResults;
            mQueryLimit = queryLimit;
        }

        public ComponentName getName() {
            return mName;
        }

        public boolean isShowingPromotedResults() {
            return mShowingPromotedResults;
        }

        public String getLabel() {
            return mLabel;
        }

        public String getIcon() {
            return mIcon;
        }

        public int getResponseStatus() {
            return mResponseStatus;
        }

        public int getNumResults() {
            return mNumResults;
        }

        public int getQueryLimit() {
            return mQueryLimit;
        }
    }
}