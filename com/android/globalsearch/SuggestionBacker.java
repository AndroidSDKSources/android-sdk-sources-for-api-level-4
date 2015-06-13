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

import java.util.ArrayList;

/**
 * Holds suggestions and provides methods for getting a snapshot of them and finding a particular
 * suggestion by the intent.
 */
public abstract class SuggestionBacker {

    interface Listener {

        /**
         * Called whenever the data has changed.  This is not called from a synchronized block.
         */
        void onNewResults();
    }

    private volatile Listener mListener;

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Places a consistent snapshot of the suggestions into dest.  Clears dest before copying
     * over the snapshot.
     *
     * The return value indicates the index at which the "more results" entry is.  The convention
     * is to return the number of items if there is no "more results" entry included, since N is
     * one past the last index (N - 1), and will never be asked for by the cursor.
     *
     * @param dest The destination to place the copy.
     * @param expandAdditional Whether to expand the list of additional sources (only applicable if
     *   {@link #isShowingMore()} is true).
     */
    public abstract void snapshotSuggestions(
            ArrayList<SuggestionData> dest, boolean expandAdditional);


    /**
     * Reports that a source has begun retrieving its results, and notifies the listener if
     * appropriate.
     *
     * @param source The name of the source.
     */
    public void onSourceQueryStart(ComponentName source) {
        if (reportSourceStarted(source)) {
            notifyListener();
        }
    }

    /**
     * Reports the results from a source, and notifies the listener if appropriate.
     *
     * @param suggestionResult The result reported back from a particular source.
     */
    public void onNewSuggestionResult(SuggestionResult suggestionResult) {
        if (addSourceResults(suggestionResult)) {
            notifyListener();
        }
    }

    /**
     * Reports that a shortcut needs to be refreshed, and notifies the listener if appropriate.
     *
     * @param source Identifies the source of the shortcut.
     * @param shortcutId The id of the shortcut
     * @param suggestionData The refreshed suggestion, or <code>null</code> if the shortcut is no
     *   longer valid and should be removed.
     */
    public void onRefreshShortcut(
            ComponentName source, String shortcutId, SuggestionData suggestionData) {
        if (refreshShortcut(source, shortcutId, suggestionData)) {
            notifyListener();
        }
    }

    /**
     * Indicates whether there are still results pending from some sources, i.e. the
     * backer is still working.
     *
     * @return true if still waiting for results from some sources
     */
    public abstract boolean isResultsPending();

    /**
     * @return Whether the "more results" entry is showing.
     */
    public abstract boolean isShowingMore();

    /**
     * @return the position of the entry containing "more results".  If the "more results" entry
     *   is not yet showing, this will return a position greater than the number of items so that
     *   any check for whether the "more results" entry was clicked on will always be false.
     */
    public abstract int getMoreResultPosition();


    /**
     * Reports that a source has begun work retrieving its results.
     *
     * @param source The name of the source.
     * @return true if the listener should be notified.
     */
    protected abstract boolean reportSourceStarted(ComponentName source);

    /**
     * Returns whether a source has begun retrieving its results yet.
     *
     * @param source The name of the source.
     * @return Whether the source has begun retrieving its results yet.
     */
    public abstract boolean hasSourceStarted(ComponentName source);

    /**
     * Add the results from a source.
     *
     * @param suggestionResult The results from a source.
     * @return true if the listener should be notified.
     */
    protected abstract boolean addSourceResults(SuggestionResult suggestionResult);

    /**
     * Refresh a shortcut result.
     *
     * @param source Identifies the source of the shortcut.
     * @param shortcutId The id of the shortcut
     * @param shortcut The refreshed suggestion, or <code>null</code> if the shortcut is
     *   now invalid and should be removed.
     * @return true if the listener should be notified.
     */
    protected abstract boolean refreshShortcut(
            ComponentName source, String shortcutId, SuggestionData shortcut);

    /**
     * Tells the listener to check back for changes via {@link #snapshotSuggestions}
     */
    public final void notifyListener() {
        final Listener listener = mListener;
        if (listener != null) listener.onNewResults();
    }

    /**
     * Used for timing related heuristics (exposed for testing).
     */
    protected long getNow() {
        return System.currentTimeMillis();
    }

}
