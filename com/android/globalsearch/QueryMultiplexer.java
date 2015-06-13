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

import static com.android.globalsearch.SuggestionSession.SOURCE_TIMEOUT_MILLIS;

import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.ArrayList;

/**
 * Responsible for sending out a query to a list of {@link SuggestionSource}s asynchronously and
 * reporting them back as they arrive to a {@link SuggestionBacker}.
 */
public class QueryMultiplexer implements Runnable {

    // set to true to enable the more verbose debug logging for this file
    private static final boolean DBG = false;
    private static final boolean DBG_LTNCY = false;
    private static final String TAG = "GlobalSearch";

    private final PerTagExecutor mExecutor;
    private final DelayedExecutor mDelayedExecutor;
    private final List<SuggestionSource> mSources;
    private final SuggestionBacker mReceiver;
    private final String mQuery;
    private final int mMaxResultsPerSource;
    private final int mQueryLimit;

    private ArrayList<SuggestionRequest> mSentRequests;

    /**
     * @param query The query to send to each source.
     * @param sources The sources.
     * @param maxResultsPerSource The maximum number of results each source should respond with,
     *        passsed along to each source as part of the query.
     * @param queryLimit An advisory maximum number that each source should return
     *        in {@link com.android.globalsearch.SuggestionResult#getCount()}.
     * @param receiver The receiver of results.
     * @param executor Used to execute each source's {@link SuggestionSource#getSuggestionTask}
     * @param delayedExecutor Used to enforce a timeout on each query.
     */
    public QueryMultiplexer(String query, List<SuggestionSource> sources, int maxResultsPerSource,
                            int queryLimit, SuggestionBacker receiver, PerTagExecutor executor,
                            DelayedExecutor delayedExecutor) {
        mExecutor = executor;
        mQuery = query;
        mSources = sources;
        mReceiver = receiver;
        mMaxResultsPerSource = maxResultsPerSource;
        mQueryLimit = queryLimit;
        mDelayedExecutor = delayedExecutor;
        mSentRequests = new ArrayList<SuggestionRequest>(mSources.size());
    }

    /**
     * Convenience for usage as {@link Runnable}.
     */
    public void run() {
        sendQuery();
    }

    /**
     * Sends the query to the sources.
     */
    public void sendQuery() {
        for (SuggestionSource source : mSources) {
            final SuggestionRequest suggestionRequest = new SuggestionRequest(source);
            suggestionRequest.setScheduledTime(System.nanoTime());
            mSentRequests.add(suggestionRequest);
            final String tag = source.getComponentName().flattenToShortString();
            final boolean queued = mExecutor.execute(tag, suggestionRequest);
            if (queued) {
                // if the task was queued because the source has too many already running, still
                // make sure we report back the result as cancelled after the timeout is exceeded
                // so the spinner doesn't continue forever.
                mDelayedExecutor.postDelayed(new Runnable() {
                    public void run() {
                        if (!suggestionRequest.isDone()) {
                            mReceiver.onNewSuggestionResult(
                                    SuggestionResult.createCancelled(
                                            suggestionRequest.getSuggestionSource()));
                        }
                    }
                }, SOURCE_TIMEOUT_MILLIS);
            }
        }
    }

    /**
     * Cancels the requests that are in progress from sending off the query.
     */
    public void cancel() {
        for (SuggestionRequest sentRequest : mSentRequests) {
            sentRequest.cancel(true);
        }
    }
    /**
     * Converts nanoseconds to milliseconds.
     */
    static int ms(long ns) {
        return (int) (ns / 1000000);
    }


    /**
     * Once a result of a suggestion task is complete, it will report the suggestions to the mixer.
     */
    class SuggestionRequest extends FutureTask<SuggestionResult> {

        private final SuggestionSource mSuggestionSource;
        private long mScheduledTime = -1; // when we tell the executor to run this
        private long mStartTime = -1;     // when it actually starts running

        /**
         * @param suggestionSource The suggestion source that this request is for.
         */
        SuggestionRequest(SuggestionSource suggestionSource) {
            super(suggestionSource.getSuggestionTask(mQuery, mMaxResultsPerSource, mQueryLimit));
            mSuggestionSource = suggestionSource;
        }

        public SuggestionSource getSuggestionSource() {
            return mSuggestionSource;
        }

        public void setScheduledTime(long scheduledTime) {
            mScheduledTime = scheduledTime;
        }

        @Override
        public void run() {
            mStartTime = System.nanoTime();
            mReceiver.onSourceQueryStart(mSuggestionSource.getComponentName());

            // note to self: stop running if we're still at it after timeout deadline
            mDelayedExecutor.postDelayed(new Runnable() {
                public void run() {
                    if (!isDone()) {
                        Log.w(TAG, "source '" + mSuggestionSource.getLabel() + "' took longer than "
                                + SOURCE_TIMEOUT_MILLIS + " millis for query '" + mQuery + "', "
                                + "attempting to cancel it.");
                        if (!cancel(true)) {
                            // if we couldn't cancel it, report back directly so the spinner doesn't
                            // go indefinitely
                            mReceiver.onNewSuggestionResult(
                                    SuggestionResult.createCancelled(mSuggestionSource));
                        }
                    }
                }
            }, SOURCE_TIMEOUT_MILLIS);
            if (DBG) Log.d(TAG, "starting query for " + mSuggestionSource.getLabel());
            super.run();
        }

        /**
         * Cancels the suggestion request.
         *
         * @param mayInterruptIfRunning Whether to interrupt the thread
         * running the suggestion request. Always pass <code>true</code>,
         * to ensure that the request finishes quickly.
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean canceled = super.cancel(mayInterruptIfRunning);
            if (DBG) Log.d(TAG, getTag() + ": Cancelling: " + canceled);
            return canceled;
        }

        // Used in debugging logs.
        private String getTag() {
            return "\"" + mQuery + "\": "
                    + mSuggestionSource.getComponentName().flattenToShortString();
        }

        @Override
        protected void done() {
            final boolean cancelled = isCancelled();
            if (DBG_LTNCY) logLatency(cancelled);

            try {
                if (cancelled) {
                    if (DBG) Log.d(TAG, getTag() + " was cancelled");
                    mReceiver.onNewSuggestionResult(
                            SuggestionResult.createCancelled(mSuggestionSource));
                    return;
                }
                final SuggestionResult suggestionResult = get();
                if (suggestionResult == null) {
                    mReceiver.onNewSuggestionResult(
                            SuggestionResult.createErrorResult(mSuggestionSource));
                } else {
                    if (DBG) {
                        Log.d(TAG, getTag() + " returned "
                                + suggestionResult.getSuggestions().size() + " items");
                    }
                    mReceiver.onNewSuggestionResult(suggestionResult);
                }
            } catch (CancellationException e) {
                // The suggestion request was canceled, do nothing.
                // This can happen when the Cursor is closed before
                // the suggestion source returns, but without
                // interrupting any waits.
                if (DBG) Log.d(TAG, getTag() + " threw CancellationException.");
                mReceiver.onNewSuggestionResult(
                        SuggestionResult.createCancelled(mSuggestionSource));
            } catch (InterruptedException e) {
                // The suggestion request was interrupted, do nothing.
                // This can happen when the Cursor is closed before
                // the suggestion source returns, by interrupting
                // a wait somewhere.
                if (DBG) Log.d(TAG, getTag() + " threw InterruptedException.");
                mReceiver.onNewSuggestionResult(
                        SuggestionResult.createCancelled(mSuggestionSource));
            } catch (ExecutionException e) {
                // The suggestion source threw an exception. We just catch and log it,
                // since we don't want to crash the suggestion provider just
                // because of a buggy suggestion source.
                Log.e(TAG, getTag() + " failed.", e.getCause());
                mReceiver.onNewSuggestionResult(
                        SuggestionResult.createErrorResult(mSuggestionSource));
            } catch (Throwable t) {
                // in case we blew it some how in the above post-processing of the result
                Log.e(TAG, getTag() + " failed: this is our fault!!", t);
                mReceiver.onNewSuggestionResult(
                        SuggestionResult.createErrorResult(mSuggestionSource));
            }
        }

        // logs a line about the time spent waiting to execute and executing with padding that will
        // result in the entries being aligned, e.g:
        // f'                   Apps  Glo #9  total=58	twait=2       duration=56
        private void logLatency(boolean cancelled) {
            final boolean everStarted = mStartTime != -1;
            final long now = System.nanoTime();
            final String rawtname = Thread.currentThread().getName();
            final String tname =
                    rawtname.substring(0, 3) + rawtname.substring(rawtname.length() - 3);
            long threadwait = ms(mStartTime - mScheduledTime);
            long durationMillis = ms(now - mStartTime);
            long total = ms(now - mScheduledTime);

            final StringBuilder sb = new StringBuilder(300);
            padQ(sb, mQuery, 20);
            sb.append(mSuggestionSource.getLabel().substring(0, 4)).append("  ");
            sb.append(tname).append("  ");
            sb.append("total=").append(total).append("\t");
            if (everStarted) {
                sb.append("twait=");
                pad(sb, Long.toString(threadwait), 8);
            }
            if (!cancelled) {
                sb.append("duration=");
                pad(sb, Long.toString(durationMillis), 8);
            } else {
                if (!everStarted) {
                    sb.append("twait=");
                    pad(sb, Long.toString(total), 8);
                    sb.append("(cancelled before running)");
                } else {
                    sb.append("duration=");
                    pad(sb, Long.toString(durationMillis), 8);
                    sb.append("(cancelled)");
                }
            }
            Log.d(TAG, sb.toString());
        }
    }

    /**
     * Appends a string to the string builder with enough padding to make the entire addition a
     * specific width.
     */
    static void pad(StringBuilder sb, String string, int width) {
        sb.append(string);
        final int padding = width - string.length();
        for (int i = 0; i < padding; i++) {
            sb.append(' ');
        }
    }

    /**
     * Appends a string to the string builder with enough padding to make the entire addition a
     * specific width.  The string is surrounded by single quotes.
     */
    static void padQ(StringBuilder sb, String string, int width) {
        sb.append('\'').append(string).append('\'');
        final int padding = width - string.length();
        for (int i = 0; i < padding; i++) {
            sb.append(' ');
        }
    }
}
