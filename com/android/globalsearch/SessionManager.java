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

import android.database.Cursor;
import android.content.Context;
import android.content.ComponentName;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Holds onto the current {@link SuggestionSession} and manages its lifecycle.  When a session ends,
 * it gets the session stats and reports them to the {@link ShortcutRepository}.
 */
public class SessionManager implements SuggestionSession.SessionCallback {

    private static final String TAG = "SessionManager";
    private static final boolean DBG = false;
    private static SessionManager sInstance;

    private final Context mContext;

    public static synchronized SessionManager getInstance() {
        return sInstance;
    }

    /**
     * Refreshes the global session manager.
     *
     * @param sources The suggestion sources.
     * @param shortcutRepo The shortcut repository.
     * @param queryExecutor The executor used to execute search suggestion tasks.
     * @param refreshExecutor The executor used execute shortcut refresh tasks.
     * @param handler The handler passed along to the session.
     * @return The up to date session manager.
     */
    public static synchronized SessionManager refreshSessionmanager(Context context,
            SuggestionSources sources, ShortcutRepository shortcutRepo,
            PerTagExecutor queryExecutor,
            Executor refreshExecutor, Handler handler) {
        if (DBG) Log.d(TAG, "refreshSessionmanager()");

        sInstance = new SessionManager(context, sources, shortcutRepo,
                queryExecutor, refreshExecutor, handler);
        return sInstance;
    }

    private SessionManager(Context context,
            SuggestionSources sources, ShortcutRepository shortcutRepo,
            PerTagExecutor queryExecutor, Executor refreshExecutor, Handler handler) {
        mContext = context;
        mSources = sources;
        mShortcutRepo = shortcutRepo;
        mQueryExecutor = queryExecutor;
        mRefreshExecutor = refreshExecutor;
        mHandler = handler;
    }

    private final SuggestionSources mSources;
    private final ShortcutRepository mShortcutRepo;
    private final PerTagExecutor mQueryExecutor;
    private final Executor mRefreshExecutor;
    private final Handler mHandler;
    private SuggestionSession mSession;

    /**
     * Queries the current session for results.
     *
     * @see SuggestionSession#query(String)
     */
    public synchronized Cursor query(Context context, String query) {
        // create a new session if there is none,
        // or when starting a new typing session
        if (mSession == null || TextUtils.isEmpty(query)) {
            mSession = createSession();
        }

        return mSession.query(query);
    }

    /** {@inheritDoc} */
    public synchronized void closeSession() {
        if (DBG) Log.d(TAG, "closeSession()");
        mSession = null;
    }

    private SuggestionSession createSession() {
        if (DBG) Log.d(TAG, "createSession()");
        final SuggestionSource webSearchSource = mSources.getSelectedWebSearchSource();
        
        // Fire off a warm-up query to the web search source, which that source can use for
        // whatever it sees fit. For example, EnhancedGoogleSearchProvider uses this to
        // determine whether a opt-in needs to be shown for use of location.
        if (webSearchSource != null) {
            warmUpWebSource(webSearchSource);
        }

        Sources sources = orderSources(
                mSources.getEnabledSuggestionSources(),
                mSources,
                mShortcutRepo.getSourceRanking(),
                SuggestionSession.NUM_PROMOTED_SOURCES);

        // implement the delayed executor using the handler
        final DelayedExecutor delayedExecutor = new DelayedExecutor() {
            public void postDelayed(Runnable runnable, long delayMillis) {
                mHandler.postDelayed(runnable, delayMillis);
            }

            public void postAtTime(Runnable runnable, long uptimeMillis) {
                mHandler.postAtTime(runnable, uptimeMillis);
            }
        };

        SuggestionSession session = new SuggestionSession(
                mSources, sources.mPromotableSources, sources.mUnpromotableSources,
                mQueryExecutor,
                mRefreshExecutor,
                delayedExecutor, new SuggestionFactoryImpl(mContext),
                SuggestionSession.CACHE_SUGGESTION_RESULTS);
        session.setListener(this);
        session.setShortcutRepo(mShortcutRepo);
        return session;
    }

    private void warmUpWebSource(final SuggestionSource webSearchSource) {
        mQueryExecutor.execute("warmup", new Runnable() {
            public void run() {
                try {
                    webSearchSource.getSuggestionTask("", 0, 0).call();
                } catch (Exception e) {
                    Log.e(TAG, "exception from web search warm-up query", e);
                }
            }
        });
    }

    /**
     * Orders sources by source ranking, and into two groups: one that are candidates for the
     * promoted list (mPromotableSources), and the other containing sources that should not be in
     * the promoted list (mUnpromotableSources).
     *
     * The promotable list is as follows:
     * - the web source
     * - up to 'numPromoted' - 1 of the best ranked sources, among source for whom we have enough
     * data (e.g are in the 'sourceRanking' list)
     *
     * The unpromotoable list is as follows:
     * - the sources lacking any impression / click data
     * - the rest of the ranked sources
     *
     * The idea is to have the best ranked sources in the promoted list, and give newer sources the
     * best slots under the "more results" positions to get a little extra attention until we have
     * enough data to rank them as usual.
     *
     * Finally, to solve the empty room problem when there is no data about any sources, we allow
     * a for a small whitelist of known system apps to be in the promoted list when there is no other
     * ranked source available.  This should only take effect for the first few usages of
     * Quick search box.
     *
     * @param enabledSources The enabled sources.
     * @param sourceRanking The order the sources should be in.
     * @param sourceLookup For getting the web search source and trusted sources.
     * @param numPromoted  The number of promoted sources.
     * @return The order of the promotable and non-promotable sources.
     */
    static Sources orderSources(
            List<SuggestionSource> enabledSources,
            SourceLookup sourceLookup,
            ArrayList<ComponentName> sourceRanking,
            int numPromoted) {

        // get any sources that are in the enabled sources in the order
        final int numSources = enabledSources.size();
        HashMap<ComponentName, SuggestionSource> linkMap =
                new LinkedHashMap<ComponentName, SuggestionSource>(numSources);
        for (int i = 0; i < numSources; i++) {
            final SuggestionSource source = enabledSources.get(i);
            linkMap.put(source.getComponentName(), source);
        }

        Sources sources = new Sources();

        // gather set of ranked
        final HashSet<ComponentName> allRanked = new HashSet<ComponentName>(sourceRanking);

        // start with the web source if it exists
        SuggestionSource webSearchSource = sourceLookup.getSelectedWebSearchSource();
        if (webSearchSource != null) {
            if (DBG) Log.d(TAG, "Adding web search source: " + webSearchSource);
            sources.add(webSearchSource, true);
        }

        // add ranked for rest of promoted slots
        final int numRanked = sourceRanking.size();
        int nextRanked = 0;
        for (; nextRanked < numRanked && sources.mPromotableSources.size() < numPromoted;
                nextRanked++) {
            final ComponentName ranked = sourceRanking.get(nextRanked);
            final SuggestionSource source = linkMap.remove(ranked);
            if (DBG) Log.d(TAG, "Adding promoted ranked source: (" + ranked + ") " + source);
            sources.add(source, true);
        }

        // now add the unranked
        final Iterator<SuggestionSource> sourceIterator = linkMap.values().iterator();
        while (sourceIterator.hasNext()) {
            SuggestionSource source = sourceIterator.next();
            if (!allRanked.contains(source.getComponentName())) {
                if (DBG) Log.d(TAG, "Adding unranked source: " + source);
                // To fix the empty room problem, we allow a small set of system apps
                // to start putting their results in the promoted list before we
                // have enough data to pick the high ranking ones.
                sources.add(source, sourceLookup.isTrustedSource(source));
                sourceIterator.remove();
            }
        }

        // finally, add any remaining ranked
        for (int i = nextRanked; i < numRanked; i++) {
            final ComponentName ranked = sourceRanking.get(i);
            final SuggestionSource source = linkMap.get(ranked);
            if (source != null) {
                if (DBG) Log.d(TAG, "Adding ranked source: (" + ranked + ") " + source);
                sources.add(source, sourceLookup.isTrustedSource(source));
            }
        }

        if (DBG) Log.d(TAG, "Promotable sources: " + sources.mPromotableSources);
        if (DBG) Log.d(TAG, "Unpromotable sources: " + sources.mUnpromotableSources);

        return sources;
    }

    static class Sources {
        public final ArrayList<SuggestionSource> mPromotableSources;
        public final ArrayList<SuggestionSource> mUnpromotableSources;
        public Sources() {
            mPromotableSources = new ArrayList<SuggestionSource>();
            mUnpromotableSources = new ArrayList<SuggestionSource>();
        }
        public void add(SuggestionSource source, boolean forcePromotable) {
            if (source == null) return;
            if (forcePromotable) {
                if (DBG) Log.d(TAG, "  Promotable: " + source);
                mPromotableSources.add(source);
            } else {
                if (DBG) Log.d(TAG, "  Unpromotable: " + source);
                mUnpromotableSources.add(source);
            }
        }
    }
}
