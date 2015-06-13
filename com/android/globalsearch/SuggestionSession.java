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
import android.database.Cursor;
import android.util.Log;
import android.app.SearchManager;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A suggestion session lives from when the user starts typing into the search dialog until
 * he/she is done (either clicked on a result, or dismissed the dialog).  It caches results
 * for the duration of a session, and aggregates stats about the session once it the session is
 * closed.
 *
 * During the session, no {@link SuggestionSource} will be queried more than once for a given query.
 *
 * If a given source returns zero results for a query, that source will be ignored for supersets of
 * that query for the rest of the session.  Sources can opt out by setting their
 * <code>queryAfterZeroResults</code> property to <code>true</code> in searchable.xml
 *
 * If there are no shortcuts or cached entries for a given query, we prefill with the results from
 * the previous query for up to {@link #PREFILL_MILLIS} millis until the first result comes back.
 * This results in a smoother experience with less flickering of zero results.
 *
 * This class is thread safe, guarded by "this", to protect against the fact that {@link #query}
 * and the callbacks via {@link com.android.globalsearch.SuggestionCursor.CursorListener} may be
 * called by different threads (the filter thread of the ACTV, and the main thread respectively).
 * Because {@link #query} is always called from the same thread, this synchronization does not
 * impose any noticeable burden (and it is not necessary to attempt finer grained synchronization
 * within the method).
 */
public class SuggestionSession {
    private static final boolean DBG = false;
    private static final boolean SPEW = false;
    private static final String TAG = "GlobalSearch";

    private final SourceLookup mSourceLookup;
    private final ArrayList<SuggestionSource> mPromotableSources;
    private final ArrayList<SuggestionSource> mUnpromotableSources;
    private ShortcutRepository mShortcutRepo;
    private final PerTagExecutor mQueryExecutor;
    private final Executor mRefreshExecutor;
    private final DelayedExecutor mDelayedExecutor;
    private final SuggestionFactory mSuggestionFactory;
    private SessionCallback mListener;
    private int mNumPromotedSources = NUM_PROMOTED_SOURCES;

    // guarded by "this"

    private final SessionCache mSessionCache;

    // the cursor from the last character typed, if any
    private SuggestionCursor mPreviousCursor = null;

    // used to detect the closing of the session
    private final AtomicInteger mOutstandingQueryCount = new AtomicInteger(0);

    // we only allow shortcuts from sources in this set
    private HashSet<ComponentName> mAllowShortcutsFrom;
    /**
     * The number of sources that have a chance to show results above the "more results" entry
     * in one of {@link #MAX_RESULTS_TO_DISPLAY} slots.
     */
    static final int NUM_PROMOTED_SOURCES = 4;

    /**
     * Whether we cache the results for each query / source.  This avoids querying a source twice
     * for the same query, but uses more memory.
     */
    static final boolean CACHE_SUGGESTION_RESULTS = false;

    /**
     * Maximum number of results to display in the list, not including any
     * built-in suggestions or corpus selection suggestions.
     */
    private static final int MAX_RESULTS_TO_DISPLAY = 7;

    /**
     * Maximum number of results to get from each source.
     */
    private static final int MAX_RESULTS_PER_SOURCE = 51 + MAX_RESULTS_TO_DISPLAY;

    /**
     * How long the promoted source have to respond before the "search the web" and "more results"
     * entries are added to the end of the list, in millis.
     */
    private static final long PROMOTED_SOURCE_DEADLINE = 6000L;

    /**
     * How long an individual source has to respond before they will be cancelled.
     */
    static final long SOURCE_TIMEOUT_MILLIS = 10000L;

    static final long PREFILL_MILLIS = 400L;

    /**
     * Interface for receiving notifications from session.
     */
    interface SessionCallback {

        /**
         * Called when the session is over.
         */
        void closeSession();
    }

    /**
     * @param sourceLookup The sources to query for results
     * @param promotableSources The promotable sources, in the order that they should be queried.  If the
     *        web source is enabled, it will always be first.
     * @param unpromotableSources The unpromotable sources, in the order that they should be queried.
     * @param queryExecutor Used to execute the asynchronous queries
     * @param refreshExecutor Used to execute refresh tasks.
     * @param delayedExecutor Used to post messages.
     * @param suggestionFactory Used to create particular suggestions.
     * @param cacheSuggestionResults Whether to cache the results of sources in hopes we can avoid
     */
    public SuggestionSession(SourceLookup sourceLookup,
            ArrayList<SuggestionSource> promotableSources,
            ArrayList<SuggestionSource> unpromotableSources,
            PerTagExecutor queryExecutor,
            Executor refreshExecutor,
            DelayedExecutor delayedExecutor,
            SuggestionFactory suggestionFactory,
            boolean cacheSuggestionResults) {
        mSourceLookup = sourceLookup;
        mPromotableSources = promotableSources;
        mUnpromotableSources = unpromotableSources;
        mQueryExecutor = queryExecutor;
        mRefreshExecutor = refreshExecutor;
        mDelayedExecutor = delayedExecutor;
        mSuggestionFactory = suggestionFactory;
        mSessionCache = new SessionCache(cacheSuggestionResults);

        final int numPromotable = promotableSources.size();
        final int numUnpromotable = unpromotableSources.size();
        mAllowShortcutsFrom = new HashSet<ComponentName>(numPromotable + numUnpromotable);
        for (int i = 0; i < numPromotable; i++) {
            mAllowShortcutsFrom.add(promotableSources.get(i).getComponentName());
        }
        for (int i = 0; i < numUnpromotable; i++) {
            mAllowShortcutsFrom.add(unpromotableSources.get(i).getComponentName());
        }

        if (DBG) Log.d(TAG, "starting session");
    }

    /**
     * Sets a listener that will be notified of session events.
     */
    public synchronized void setListener(SessionCallback listener) {
        mListener = listener;
    }

    public synchronized void setShortcutRepo(ShortcutRepository shortcutRepo) {
        mShortcutRepo = shortcutRepo;
    }

    /**
     * @param numPromotedSources The number of sources to query first for the promoted list.
     */
    public synchronized void setNumPromotedSources(int numPromotedSources) {
        mNumPromotedSources = numPromotedSources;
    }

    /**
     * Queries the current session for a resulting cursor.  The cursor will be backed by shortcut
     * and cached data from this session and then be notified of change as other results come in.
     *
     * @param query The query.
     * @return A cursor.
     */
    public synchronized Cursor query(final String query) {
        mOutstandingQueryCount.incrementAndGet();

        final SuggestionCursor cursor = new SuggestionCursor(mDelayedExecutor, query);

        fireStuffOff(cursor, query);

        // if the cursor we are about to return is empty (no cache, no shortcuts),
        // prefill it with the previous results until we hear back from a source
        if (mPreviousCursor != null
                && query.length() > 1       // don't prefil when going from empty to first char
                && cursor.getCount() == 0
                && mPreviousCursor.getCount() > 0) {
            cursor.prefill(mPreviousCursor);

            // limit the amount of time we show prefilled results
            mDelayedExecutor.postDelayed(new Runnable() {
                public void run() {
                    cursor.onNewResults();
                }
            }, PREFILL_MILLIS);
        }
        mPreviousCursor = cursor;
        return cursor;
    }

    /**
     * Finishes the work necessary to report complete results back to the cursor.  This includes
     * getting the shortcuts, refreshing them, determining which source should be queried, sending
     * off the query to each of them, and setting up the callback from the cursor.
     *
     * @param cursor The cursor the results will be reported to.
     * @param query The query.
     */
    private void fireStuffOff(final SuggestionCursor cursor, final String query) {
        // get shortcuts
        final ArrayList<SuggestionData> shortcuts = getShortcuts(query);

        // filter out sources that aren't relevant to this query
        final ArrayList<SuggestionSource> promotableSourcesToQuery =
                filterSourcesForQuery(query, mPromotableSources);
        final ArrayList<SuggestionSource> unpromotableSourcesToQuery =
                filterSourcesForQuery(query, mUnpromotableSources);
        final ArrayList<SuggestionSource> sourcesToQuery
                = new ArrayList<SuggestionSource>(
                        promotableSourcesToQuery.size() + unpromotableSourcesToQuery.size());
        sourcesToQuery.addAll(promotableSourcesToQuery);
        sourcesToQuery.addAll(unpromotableSourcesToQuery);

        if (DBG) {
            Log.d(TAG, promotableSourcesToQuery.size() + " promotable sources and "
                    + promotableSourcesToQuery.size() + " unpromotable sources will be queried.");
        }

        // get the shortcuts to refresh
        final ArrayList<SuggestionData> shortcutsToRefresh = new ArrayList<SuggestionData>();
        final int numShortcuts = shortcuts.size();
        for (int i = 0; i < numShortcuts; i++) {
            SuggestionData shortcut = shortcuts.get(i);

            final String shortcutId = shortcut.getShortcutId();
            if (shortcutId == null) continue;

            if (mSessionCache.hasShortcutBeenRefreshed(shortcut.getSource(), shortcutId)) {
                // if we've already refreshed the shortcut, don't do it again.  if it shows a
                // spinner while refreshing, it will come out of the repo with a spinner for icon2.
                // we need to remove this or replace it with what was refreshed as applicable.
                if (shortcut.isSpinnerWhileRefreshing()) {
                    shortcuts.set(
                            i,
                            shortcut.buildUpon().icon2(
                                    mSessionCache.getRefreshedShortcutIcon2(
                                            shortcut.getSource(), shortcutId)).build());
                }
                continue;
            }
            shortcutsToRefresh.add(shortcut);
        }

        // make the suggestion backer
        final HashSet<ComponentName> promoted = pickPromotedSources(promotableSourcesToQuery);

        // cached source results
        final QueryCacheResults queryCacheResults = mSessionCache.getSourceResults(query);

        final SuggestionSource webSearchSource = mSourceLookup.getSelectedWebSearchSource();
        final SourceSuggestionBacker backer = new SourceSuggestionBacker(
                query,
                shortcuts,
                sourcesToQuery,
                promoted,
                webSearchSource,
                queryCacheResults.getResults(),
                mSuggestionFactory.createGoToWebsiteSuggestion(query),
                mSuggestionFactory.createSearchTheWebSuggestion(query),
                MAX_RESULTS_TO_DISPLAY,
                PROMOTED_SOURCE_DEADLINE,
                mSuggestionFactory,
                mSuggestionFactory);

        if (DBG) {
            Log.d(TAG, "starting off with " + queryCacheResults.getResults().size() + " cached "
                    + "sources");
            Log.d(TAG, "identified " + promoted.size() + " promoted sources to query");
            Log.d(TAG, "identified " + shortcutsToRefresh.size()
                + " shortcuts out of " + numShortcuts + " total shortcuts to refresh");
        }

        // fire off queries / refreshers
        final AsyncMux asyncMux = new AsyncMux(
                mQueryExecutor,
                mRefreshExecutor,
                mDelayedExecutor,
                mSessionCache,
                query,
                shortcutsToRefresh,
                removeCached(sourcesToQuery, queryCacheResults),
                promoted,
                backer,
                mShortcutRepo);

        cursor.attachBacker(asyncMux);
        asyncMux.setListener(cursor);

        cursor.setListener(new SessionCursorListener(asyncMux));

        asyncMux.sendOffShortcutRefreshers(mSourceLookup);
        asyncMux.sendOffPromotedSourceQueries();

        // refresh the backer after the deadline to force showing of "more results"
        // even if all of the promoted sources haven't responded yet.
        mDelayedExecutor.postDelayed(new Runnable() {
            public void run() {
                cursor.onNewResults();
            }
        }, PROMOTED_SOURCE_DEADLINE);
    }

    private HashSet<ComponentName> pickPromotedSources(ArrayList<SuggestionSource> sources) {
        HashSet<ComponentName> promoted = new HashSet<ComponentName>(sources.size());
        for (int i = 0; i < mNumPromotedSources && i < sources.size(); i++) {
            promoted.add(sources.get(i).getComponentName());
        }
        return promoted;
    }

    private class SessionCursorListener implements SuggestionCursor.CursorListener {
        private AsyncMux mAsyncMux;
        public SessionCursorListener(AsyncMux asyncMux) {
            mAsyncMux = asyncMux;
        }
        public void onClose() {
            if (DBG) Log.d(TAG, "onClose(\"" + mAsyncMux.getQuery() + "\")");

            mAsyncMux.cancel();
            // when the cursor closes and there aren't any outstanding requests, it means
            // the user has moved on (either clicked on something, dismissed the dialog, or
            // pivoted into app specific search)
            int refCount = mOutstandingQueryCount.decrementAndGet();
            if (DBG) Log.d(TAG, "Session reference count: " + refCount);
            if (refCount == 0) {
                close();
            }
        }

        public void onItemClicked(SuggestionData clicked,
                List<SuggestionData> viewedSuggestions) {
            if (DBG) Log.d(TAG, "onItemClicked()");

            // find click to report
            SuggestionData clickedSuggestion = null;
            // Only record clicks on suggestions from external sources
            if (isSourceSuggestion(clicked)) {
                clickedSuggestion = clicked;
            }

            // find impressions to report
            final int numViewed = viewedSuggestions.size();
            HashSet<ComponentName> sourceImpressions = new HashSet<ComponentName>();
            for (int i = 0; i < numViewed; i++) {
                final SuggestionData viewed = viewedSuggestions.get(i);
                // only add it if it is from a source we know of (e.g, not a built in one
                // used for special suggestions like "more results").
                if (isSourceSuggestion(viewed)) {
                    sourceImpressions.add(viewed.getSource());
                } else if (isCorpusSelector(viewed)) {
                    // a corpus result under "more results"; unpack the component
                    final ComponentName corpusName =
                            ComponentName.unflattenFromString(viewed.getIntentData());
                    if (corpusName != null && mAsyncMux.hasSourceStarted(corpusName)) {
                        // we only count an impression if the source has at least begun
                        // retrieving its results.
                        sourceImpressions.add(corpusName);
                    }
                }
            }

            reportStats(new SessionStats(mAsyncMux.getQuery(),
                    clickedSuggestion, sourceImpressions));
        }

        /**
         * Checks whether a suggestion comes from a source we know of (e.g, not a built in one
         * used for special suggestions like "more results").
         */
        private boolean isSourceSuggestion(SuggestionData suggestion) {
            return mSourceLookup.getSourceByComponentName(suggestion.getSource()) != null;
        }

        private boolean isCorpusSelector(SuggestionData suggestion) {
            return SearchManager.INTENT_ACTION_CHANGE_SEARCH_SOURCE.equals(
                    suggestion.getIntentAction());
        }

        public void onMoreVisible() {
            if (DBG) Log.d(TAG, "onMoreVisible");
            mAsyncMux.sendOffAdditionalSourcesQueries();
        }
    }

    private ArrayList<SuggestionData> getShortcuts(String query) {
        if (mShortcutRepo == null) return new ArrayList<SuggestionData>();
        return filterOnlyEnabled(mShortcutRepo.getShortcutsForQuery(query));
    }

    void reportStats(SessionStats stats) {
        if (mShortcutRepo != null) mShortcutRepo.reportStats(stats);
    }

    synchronized void close() {
        Log.d(TAG, "close()");
        if (mListener != null) mListener.closeSession();
    }

    /**
     * Filter the list of shortcuts to only include those come from enabled sources.
     *
     * @param shortcutsForQuery The shortcuts.
     * @return A list including only shortcuts from sources that are enabled.
     */
    private ArrayList<SuggestionData> filterOnlyEnabled(
            ArrayList<SuggestionData> shortcutsForQuery) {
        final int numShortcuts = shortcutsForQuery.size();
        if (numShortcuts == 0) return shortcutsForQuery;

        final ArrayList<SuggestionData> result = new ArrayList<SuggestionData>(
                shortcutsForQuery.size());
        for (int i = 0; i < numShortcuts; i++) {
            final SuggestionData shortcut = shortcutsForQuery.get(i);
            if (mAllowShortcutsFrom.contains(shortcut.getSource())) {
                result.add(shortcut);
            }
        }
        return result;
    }

    /**
     * @param sources The sources
     * @param queryCacheResults The cached results for the current query
     * @return A list of sources not including any of the cached results.
     */
    private ArrayList<SuggestionSource> removeCached(
            ArrayList<SuggestionSource> sources, QueryCacheResults queryCacheResults) {
        final int numSources = sources.size();
        final ArrayList<SuggestionSource> unCached = new ArrayList<SuggestionSource>(numSources);

        for (int i = 0; i < numSources; i++) {
            final SuggestionSource source = sources.get(i);
            if (queryCacheResults.getResult(source.getComponentName()) == null) {
                unCached.add(source);
            }
        }
        return unCached;
    }

    /**
     * Filter the sources to query based on properties of each source related to the query.
     *
     * @param query The query.
     * @param enabledSources The full list of sources.
     * @return A list of sources that should be queried.
     */
    private ArrayList<SuggestionSource> filterSourcesForQuery(
            String query, ArrayList<SuggestionSource> enabledSources) {
        final int queryLength = query.length();
        final int cutoff = Math.max(1, queryLength);
        final ArrayList<SuggestionSource> sourcesToQuery = new ArrayList<SuggestionSource>();

        if (queryLength == 0) return sourcesToQuery;

        if (DBG && SPEW) Log.d(TAG, "filtering enabled sources to those we want to query...");
        for (SuggestionSource enabledSource : enabledSources) {

            // query too short
            if (enabledSource.getQueryThreshold() > cutoff) {
                if (DBG && SPEW) {
                    Log.d(TAG, "skipping " + enabledSource.getLabel() + " (query thresh)");
                }
                continue;
            }

            final ComponentName sourceName = enabledSource.getComponentName();

            // source returned zero results for a prefix of query
            if (!enabledSource.queryAfterZeroResults()
                    && mSessionCache.hasReportedZeroResultsForPrefix(
                    query, sourceName)) {
                if (DBG && SPEW) {
                    Log.d(TAG, "skipping " + enabledSource.getLabel()
                            + " (zero results for prefix)");
                }
                continue;
            }

            if (DBG && SPEW) Log.d(TAG, "adding " + enabledSource.getLabel());
            sourcesToQuery.add(enabledSource);
        }
        return sourcesToQuery;
    }

    long getNow() {
        return System.currentTimeMillis();
    }

    /**
     * Caches results and information to avoid doing unnecessary work within the session.  Helps
     * the session to make the following optimizations:
     * - don't query same source more than once for a given query (subject to memory constraints)
     * - don't validate the same shortcut more than once
     * - don't query a source again if it returned zero results before for a prefix of a given query
     *
     * To avoid hogging memory the list of suggestions returned from sources are referenced from
     * soft references.
     */
    static class SessionCache {

        static final QueryCacheResults EMPTY = new QueryCacheResults();
        static private final String NO_ICON = "NO_ICON";

        private final HashMap<String, HashSet<ComponentName>> mZeroResultSources
                = new HashMap<String, HashSet<ComponentName>>();

        private final HashMap<String, SoftReference<QueryCacheResults>> mResultsCache;
        private final HashMap<String, String> mRefreshedShortcuts = new HashMap<String, String>();

        SessionCache(boolean cacheQueryResults) {
            mResultsCache = cacheQueryResults ?
                    new HashMap<String, SoftReference<QueryCacheResults>>() :
                    null;
        }

        /**
         * @param query The query
         * @param source Identifies the source
         * @return Whether the given source has returned zero results for any prefixes of the
         *   given query.
         */
        synchronized boolean hasReportedZeroResultsForPrefix(
                String query, ComponentName source) {
            final int queryLength = query.length();
            for (int i = 1; i < queryLength; i++) {
                final String subQuery = query.substring(0, queryLength - i);
                final HashSet<ComponentName> zeros = mZeroResultSources.get(subQuery);
                if (zeros != null && zeros.contains(source)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Reports that a source has refreshed a shortcut
         */
        synchronized void reportRefreshedShortcut(
                ComponentName source, String shortcutId, SuggestionData shortcut) {
            final String icon2 = (shortcut == null || !shortcut.isSpinnerWhileRefreshing()) ?
                    NO_ICON :
                    (shortcut.getIcon2() == null) ? NO_ICON : shortcut.getIcon2();
            mRefreshedShortcuts.put(makeShortcutKey(source, shortcutId), icon2);
        }

        /**
         * @param source Identifies the source
         * @param shortcutId The id of the shortcut
         * @return Whether the shortcut id has been validated already
         */
        synchronized boolean hasShortcutBeenRefreshed(
                ComponentName source, String shortcutId) {
            return mRefreshedShortcuts.containsKey(makeShortcutKey(source, shortcutId));
        }

        /**
         * @return The icon2 that was reported by the refreshed source, or null if there was no
         *         icon2 in the refreshed shortcut.  Also returns null if the shortcut was never
         *         refreshed, or if the shortcut is not
         *         {@link SuggestionData#isSpinnerWhileRefreshing()}.
         */
        synchronized String getRefreshedShortcutIcon2(ComponentName source, String shortcutId) {
            final String icon2 = mRefreshedShortcuts.get(makeShortcutKey(source, shortcutId));
            return (icon2 == null || icon2 == NO_ICON) ? null : icon2;
        }

        private static String makeShortcutKey(ComponentName name, String shortcutId) {
            final String nameStr = name.toShortString();
            return new StringBuilder(nameStr.length() + shortcutId.length() + 1)
                    .append(nameStr).append('_').append(shortcutId).toString();
        }

        /**
         * @param query The query
         * @return The results for any sources that have reported results.
         */
        synchronized QueryCacheResults getSourceResults(String query) {
            final QueryCacheResults queryCacheResults = getCachedResult(query);
            return queryCacheResults == null ? EMPTY : queryCacheResults;
        }


        /**
         * Reports that a source has provided results for a particular query.
         */
        synchronized void reportSourceResult(String query, SuggestionResult sourceResult) {

            // caching of query results
            if (mResultsCache != null) {
                QueryCacheResults queryCacheResults = getCachedResult(query);
                if (queryCacheResults == null) {
                    queryCacheResults = new QueryCacheResults();
                    mResultsCache.put(
                            query, new SoftReference<QueryCacheResults>(queryCacheResults));
                }
                queryCacheResults.addResult(sourceResult);
            }

            // book keeping about sources that have returned zero results
            if (!sourceResult.getSource().queryAfterZeroResults()
                    && sourceResult.getSuggestions().isEmpty()) {
                HashSet<ComponentName> zeros = mZeroResultSources.get(query);
                if (zeros == null) {
                    zeros = new HashSet<ComponentName>();
                    mZeroResultSources.put(query, zeros);
                }
                zeros.add(sourceResult.getSource().getComponentName());
            }
        }

        private QueryCacheResults getCachedResult(String query) {
            if (mResultsCache == null) return null;

            final SoftReference<QueryCacheResults> ref = mResultsCache.get(query);
            if (ref == null) return null;

            if (ref.get() == null) {
                if (DBG) Log.d(TAG, "soft ref to results for '" + query + "' GC'd");
            }
            return ref.get();
        }
    }

    /**
     * Holds the results reported back by the sources for a particular query.
     *
     * Preserves order of when they were reported back, provides efficient lookup for a given
     * source
     */
    static class QueryCacheResults {

        private final LinkedHashMap<ComponentName, SuggestionResult> mSourceResults
                = new LinkedHashMap<ComponentName, SuggestionResult>();

        public void addResult(SuggestionResult result) {
            mSourceResults.put(result.getSource().getComponentName(), result);
        }

        public Collection<SuggestionResult> getResults() {
            return mSourceResults.values();
        }

        public SuggestionResult getResult(ComponentName source) {
            return mSourceResults.get(source);
        }
    }

    /**
     * Asynchronously queries sources to get their results for a query and to validate shorcuts.
     *
     * Results are passed through to a wrapped {@link SuggestionBacker} after passing along stats
     * to the session cache.
     */
    static class AsyncMux extends SuggestionBacker {

        private final PerTagExecutor mQueryExecutor;
        private final Executor mRefreshExecutor;
        private final DelayedExecutor mDelayedExecutor;
        private final SessionCache mSessionCache;
        private final String mQuery;
        private final ArrayList<SuggestionData> mShortcutsToValidate;
        private final ArrayList<SuggestionSource> mSourcesToQuery;
        private final HashSet<ComponentName> mPromotedSources;
        private final SourceSuggestionBacker mBackerToReportTo;
        private final ShortcutRepository mRepo;

        private QueryMultiplexer mPromotedSourcesQueryMux;
        private QueryMultiplexer mAdditionalSourcesQueryMux;
        private ShortcutRefresher mShortcutRefresher;

        private volatile boolean mCanceled = false;

        /**
         * @param queryExecutor required by the query multiplexers.
         * @param refreshExecutor required by the refresh multiplexers.
         * @param delayedExecutor required by the query multiplexers.
         * @param sessionCache results are repoted to the cache as they come in
         * @param query the query the tasks pertain to
         * @param shortcutsToValidate the shortcuts that need to be validated
         * @param sourcesToQuery the sources that need to be queried
         * @param promotedSources those sources that are promoted
         * @param backerToReportTo the backer the results should be passed to
         * @param repo The shortcut repository needed to create the shortcut refresher.
         */
        AsyncMux(
                PerTagExecutor queryExecutor,
                Executor refreshExecutor,
                DelayedExecutor delayedExecutor,
                SessionCache sessionCache,
                String query,
                ArrayList<SuggestionData> shortcutsToValidate,
                ArrayList<SuggestionSource> sourcesToQuery,
                HashSet<ComponentName> promotedSources,
                SourceSuggestionBacker backerToReportTo,
                ShortcutRepository repo) {
            mQueryExecutor = queryExecutor;
            mRefreshExecutor = refreshExecutor;
            mDelayedExecutor = delayedExecutor;
            mSessionCache = sessionCache;
            mQuery = query;
            mShortcutsToValidate = shortcutsToValidate;
            mSourcesToQuery = sourcesToQuery;
            mPromotedSources = promotedSources;
            mBackerToReportTo = backerToReportTo;
            mRepo = repo;
        }

        public String getQuery() {
            return mQuery;
        }

        @Override
        public void snapshotSuggestions(ArrayList<SuggestionData> dest, boolean expandAdditional) {
            mBackerToReportTo.snapshotSuggestions(dest, expandAdditional);
        }

        @Override
        public boolean isResultsPending() {
            return mBackerToReportTo.isResultsPending();
        }

        @Override
        public boolean isShowingMore() {
            return mBackerToReportTo.isShowingMore();
        }

        @Override
        public int getMoreResultPosition() {
            return mBackerToReportTo.getMoreResultPosition();
        }

        @Override
        public boolean reportSourceStarted(ComponentName source) {
            return mBackerToReportTo.reportSourceStarted(source);
        }

        @Override
        public boolean hasSourceStarted(ComponentName source) {
            return mBackerToReportTo.hasSourceStarted(source);
        }

        @Override
        protected boolean addSourceResults(SuggestionResult suggestionResult) {
            if (suggestionResult.getResultCode() == SuggestionResult.RESULT_OK) {
                mSessionCache.reportSourceResult(mQuery, suggestionResult);
            }
            return mBackerToReportTo.addSourceResults(suggestionResult);
        }

        @Override
        protected boolean refreshShortcut(
                ComponentName source, String shortcutId, SuggestionData shortcut) {
            mSessionCache.reportRefreshedShortcut(source, shortcutId, shortcut);
            return mBackerToReportTo.refreshShortcut(source, shortcutId, shortcut);
        }

        void sendOffShortcutRefreshers(SourceLookup sourceLookup) {
            if (mCanceled) return;
            if (mShortcutRefresher != null) {
                throw new IllegalStateException("Already refreshed once");
            }
            mShortcutRefresher = new ShortcutRefresher(
                    mRefreshExecutor, sourceLookup, mShortcutsToValidate,
                    MAX_RESULTS_TO_DISPLAY, this, mRepo);
            if (DBG) Log.d(TAG, "sending shortcut refresher tasks for " +
                    mShortcutsToValidate.size() + " shortcuts.");
            mShortcutRefresher.refresh();
        }

        void sendOffPromotedSourceQueries() {
            if (mCanceled) return;
            if (mPromotedSourcesQueryMux != null) {
                throw new IllegalStateException("Already queried once");
            }

            ArrayList<SuggestionSource> promotedSources =
                    new ArrayList<SuggestionSource>(mPromotedSources.size());

            for (SuggestionSource source : mSourcesToQuery) {
                if (mPromotedSources.contains(source.getComponentName())) {
                    promotedSources.add(source);
                }
            }
            mPromotedSourcesQueryMux = new QueryMultiplexer(
                    mQuery, promotedSources, MAX_RESULTS_PER_SOURCE, MAX_RESULTS_PER_SOURCE,
                    this, mQueryExecutor, mDelayedExecutor);
            if (DBG) Log.d(TAG, "sending '" + mQuery + "' off to " + promotedSources.size() +
                    " promoted sources");
            mBackerToReportTo.reportPromotedQueryStartTime();
            mPromotedSourcesQueryMux.sendQuery();
        }

        void sendOffAdditionalSourcesQueries() {
            if (mCanceled) return;
            if (mAdditionalSourcesQueryMux != null) {
                throw new IllegalStateException("Already queried once");
            }

            final int numAdditional = mSourcesToQuery.size() - mPromotedSources.size();

            if (numAdditional <= 0) {
                return;
            }

            ArrayList<SuggestionSource> additional = new ArrayList<SuggestionSource>(numAdditional);
            for (SuggestionSource source : mSourcesToQuery) {
                if (!mPromotedSources.contains(source.getComponentName())) {
                    additional.add(source);
                }
            }

            mAdditionalSourcesQueryMux = new QueryMultiplexer(
                    mQuery, additional, MAX_RESULTS_TO_DISPLAY, MAX_RESULTS_PER_SOURCE,
                    this, mQueryExecutor, mDelayedExecutor);
            if (DBG) Log.d(TAG, "sending queries off to " + additional.size() + " promoted " +
                    "sources");
            mAdditionalSourcesQueryMux.sendQuery();
        }

        void cancel() {
            mCanceled = true;

            if (mShortcutRefresher != null) {
                mShortcutRefresher.cancel();
            }
            if (mPromotedSourcesQueryMux != null) {
                mPromotedSourcesQueryMux.cancel();
            }
            if (mAdditionalSourcesQueryMux != null) {
                mAdditionalSourcesQueryMux.cancel();
            }
        }
    }
}
