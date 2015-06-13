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
import android.app.SearchManager.DialogCursorProtocol;
import android.database.AbstractCursor;
import android.database.CursorIndexOutOfBoundsException;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This is the Cursor that we return from SuggestionProvider.  It relies on its
 * {@link SuggestionBacker} for results and notifications of changes to the results.
 *
 * The backer is attached via {@link #attachBacker(SuggestionBacker)} once it is ready.
 *
 * Important: a local consistent copy of the suggestions is stored in the cursor.  The only safe
 * place to update this copy is in {@link #requery}.
 */
public class SuggestionCursor extends AbstractCursor implements SuggestionBacker.Listener {
    // set to true to enable the more verbose debug logging for this file
    private static final boolean DBG = false;

    // set to true along with DBG to be even more verbose
    private static final boolean SPEW = false;

    // set to true to dump a full list of the suggestions each time the cursor is requeried
    private static final boolean DUMP_SUGGESTIONS = false;

    private static final String TAG = SuggestionCursor.class.getSimpleName();

    // The extra used to tell a cursor to close itself. This is a hack to work around
    // the fact that cross-process cursors currently don't get closed by Cursor.close(),
    // http://b/issue?id=2015069
    private static final String EXTRA_CURSOR_RESPOND_CLOSE_CURSOR = "cursor_respond_close_cursor";

    // the same as the string in suggestActionMsgColumn in res/xml/searchable.xml
    private static final String SUGGEST_COLUMN_ACTION_MSG_CALL = "suggest_action_msg_call";

    private static final String[] COLUMNS = {
            "_id",
            SearchManager.SUGGEST_COLUMN_FORMAT,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_ICON_2,
            SearchManager.SUGGEST_COLUMN_QUERY,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SUGGEST_COLUMN_ACTION_MSG_CALL,
            SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
            SearchManager.SUGGEST_COLUMN_INTENT_COMPONENT_NAME,
            SearchManager.SUGGEST_COLUMN_SHORTCUT_ID,
            SearchManager.SUGGEST_COLUMN_BACKGROUND_COLOR
            };

    // Indices into COLUMNS
    private static final int _ID = 0;
    private static final int FORMAT = 1;
    private static final int TEXT_1 = 2;
    private static final int TEXT_2 = 3;
    private static final int ICON_1 = 4;
    private static final int ICON_2 = 5;
    private static final int QUERY = 6;
    private static final int INTENT_ACTION = 7;
    private static final int INTENT_DATA = 8;
    private static final int ACTION_MSG_CALL = 9;
    private static final int INTENT_EXTRA_DATA = 10;
    private static final int INTENT_COMPONENT_NAME = 11;
    private static final int SHORTCUT_ID = 12;
    private static final int BACKGROUND_COLOR = 13;

    private boolean mOnMoreCalled = false;

    private final String mQuery;
    private final DelayedExecutor mDelayedExecutor;
    private boolean mIncludeSources;
    private CursorListener mListener;

    private SuggestionBacker mBacker;
    private long mNextNotify = 0;

    // we keep a consistent snapshot locally
    private ArrayList<SuggestionData> mData = new ArrayList<SuggestionData>(10);

    /**
     * We won't call {@link AbstractCursor#onChange} more than once per window.
     */
    private static final int CURSOR_NOTIFY_WINDOW_MS = 100;

    /**
     * Interface for receiving notification from the cursor.
     */
    public interface CursorListener {
        /**
         * Called when the cursor has been closed.
         */
        void onClose();

        /**
         * Called when an item is clicked.
         *
         * @param click The suggestion that was clicked.
         * @param displayedSuggestions The suggestions that have been displayed to the user.
         */
        void onItemClicked(SuggestionData clicked, List<SuggestionData> displayedSuggestions);

        /**
         * Called the first time "more" becomes visible
         */
        void onMoreVisible();
    }

    /**
     * @param delayedExecutor used to post messages.
     * @param query The query that was sent.
     */
    public SuggestionCursor(DelayedExecutor delayedExecutor, String query) {
        mQuery = query;
        mDelayedExecutor = delayedExecutor;
        mIncludeSources = false;
    }

    /**
     * Set the suggestion backer, triggering the initial snapshot.
     *
     * @param backer The backer.
     */
    public void attachBacker(SuggestionBacker backer) {
        mBacker = backer;
        mBacker.snapshotSuggestions(mData, mIncludeSources);
        onNewResults();
    }

    /**
     * Prefills the results from this cursor with the results from another.  This is used when no
     * other results are initially available to provide a smoother experience.
     *
     * @param other The other cursor to get the results from.
     */
    public void prefill(SuggestionCursor other) {
        if (!mData.isEmpty()) {
            throw new IllegalStateException("prefilled when we aleady have results");
        }
        mData.clear();
        mData.addAll(other.mData);
    }

    @Override
    public String[] getColumnNames() {
        return COLUMNS;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    /**
     * Handles out-of-band messages from the search dialog.
     */
    @Override
    public Bundle respond(Bundle extras) {
        if (DBG) Log.d(TAG, "respond(" + extras + ")");

        // Hack to work around http://b/issue?id=2015069,
        // "CursorToBulkCursorAdaptor.close() does not call mCursor.close()"
        if (extras.getBoolean(EXTRA_CURSOR_RESPOND_CLOSE_CURSOR)) {
            close();
            return Bundle.EMPTY;
        }

        final int method = extras.getInt(SearchManager.DialogCursorProtocol.METHOD, -1);

        if (method == -1) {
            Log.w(TAG, "received unexpectd respond: no DialogCursorProtocol.METHOD specified.");
            return Bundle.EMPTY;
        }

        switch (method) {
            case DialogCursorProtocol.POST_REFRESH:
                return respondPostRefresh(extras);
            case DialogCursorProtocol.CLICK:
                return respondClick(extras);
            case DialogCursorProtocol.THRESH_HIT:
                return respondThreshHit(extras);
            default:
                Log.e(TAG, "unexpected DialogCursorProtocol.METHOD " + method);
                return Bundle.EMPTY;
        }
    }

    /**
     * Handle receiving and sending back information associated with
     * {@link DialogCursorProtocol#POST_REFRESH}.
     *
     * @param request The bundle sent.
     * @return The response bundle.
     */
    private Bundle respondPostRefresh(Bundle request) {
        Bundle response = new Bundle(2);
        response.putBoolean(
                DialogCursorProtocol.POST_REFRESH_RECEIVE_ISPENDING, isResultsPending());

        if (isShowingMore() && !mOnMoreCalled) {
            // tell the dialog we want to be notified when "more results" is first displayed
            response.putInt(
                    DialogCursorProtocol.POST_REFRESH_RECEIVE_DISPLAY_NOTIFY,
                    getMoreResultsPosition());
        }
        return response;
    }

    private boolean isResultsPending() {
        // asssume results are pending until we get the backer
        return mBacker == null ? true : mBacker.isResultsPending();
    }

    private boolean isShowingMore() {
        return mBacker != null && mBacker.isShowingMore();
    }

    private int getMoreResultsPosition() {
        return mBacker == null ?
                mData.size() :
                mBacker.getMoreResultPosition();
    }

    /**
     * Handle receiving and sending back information associated with
     * {@link DialogCursorProtocol#CLICK}.
     *
     * @param request The bundle sent.
     * @return The response bundle.
     */
    private Bundle respondClick(Bundle request) {
        final int pos = request.getInt(DialogCursorProtocol.CLICK_SEND_POSITION, -1);
        int maxDisplayed = request.getInt(DialogCursorProtocol.CLICK_SEND_MAX_DISPLAY_POS, -1);
        if (DBG) Log.d(TAG, "respondClick(), pos=" + pos + ", maxDisplayed=" + maxDisplayed);

        if (pos == -1) {
            Log.w(TAG, "DialogCursorProtocol.CLICK didn't come with extra CLICK_SEND_POSITION");
            return Bundle.EMPTY;
        }

        // avoid exceptions from List.subList()
        final int numSuggestions = mData.size();
        if (maxDisplayed < -1) maxDisplayed = -1;
        if (maxDisplayed >= numSuggestions) maxDisplayed = numSuggestions - 1;
        List<SuggestionData> displayedSuggestions = mData.subList(0, maxDisplayed + 1);

        if (mListener != null) mListener.onItemClicked(mData.get(pos), displayedSuggestions);

        // if they click on the "more results item"
        if (pos == getMoreResultsPosition()) {
            // toggle the expansion of the additional sources
            mIncludeSources = !mIncludeSources;
            onNewResults();

            if (mIncludeSources) {
                // if we have switched to expanding,
                // tell the search dialog to select the position of the "more" entry so that
                // the additional corpus entries will become visible without having to
                // manually scroll
                final Bundle response = new Bundle();
                response.putInt(DialogCursorProtocol.CLICK_RECEIVE_SELECTED_POS, pos);
                return response;
            }
        }
        return Bundle.EMPTY;
    }

    /**
     * Handle receiving and sending back information associated with
     * {@link DialogCursorProtocol#THRESH_HIT}.
     *
     * We use this to get notified when "more" is first scrolled onto screen.
     *
     * @param request The bundle sent.
     * @return The response bundle.
     */
    private Bundle respondThreshHit(Bundle request) {
        mOnMoreCalled = true;
        if (mListener != null) mListener.onMoreVisible();
        return Bundle.EMPTY;
    }

    @Override
    public void close() {
        if (DBG) Log.d(TAG, "close()");
        super.close();
        if (mListener != null) {
            mListener.onClose();
        }
    }

    @Override
    protected void finalize() {
        if (!mClosed) {
            Log.w(TAG, "SuggestionCursor finalized without being closed. Someone is leaking.");
            close();
        }
        super.finalize();
    }

    /**
     * We don't copy over a fresh copy of the data, instead, we notify the cursor that the
     * data has changed, and wait to for {@link #requery} to be called.  This way, any
     * adapter backed by this cursor will have a consistent view of the data, and {@link #requery}
     * us when ready.
     *
     * Calls {@link AbstractCursor#onChange} only if there isn't already one planned to be called
     * within {@link #CURSOR_NOTIFY_WINDOW_MS}.
     *
     * {@inheritDoc}
     */
    public synchronized void onNewResults() {
        if (DBG) Log.d(TAG, "onNewResults()");
        if (!isClosed()) {
            long now = SystemClock.uptimeMillis();
            if (now < mNextNotify) {
                if (DBG) Log.d(TAG, "-avoided a notify!");
                return;
            }
            mNextNotify = now + CURSOR_NOTIFY_WINDOW_MS;

            if (DBG) Log.d(TAG, "-posting onChange(false)");
            mDelayedExecutor.postAtTime(mNotifier, mNextNotify);
        }
    }

    private final Runnable mNotifier = new Runnable() {
        public void run() {
            SuggestionCursor.this.onChange(false);
        }
    };

    /**
     * Gets the current suggestion.
     */
    private SuggestionData get() {
        if (mPos < 0) {
            throw new CursorIndexOutOfBoundsException("Before first row.");
        }
        if (mPos >= mData.size()) {
            throw new CursorIndexOutOfBoundsException("After last row.");
        }

        SuggestionData suggestion = mData.get(mPos);
        if (DBG && SPEW) Log.d(TAG, "get(" + mPos + ")");
        if (DBG && SPEW) Log.d(TAG, suggestion.toString());
        return suggestion;
    }

    @Override
    public boolean requery() {
        if (mBacker != null) {
            mBacker.snapshotSuggestions(mData, mIncludeSources);
            if (DBG) Log.d(TAG, "requery(), now " + mData.size() + " items");
        }

        if (DUMP_SUGGESTIONS) {
            Log.d(TAG, "");
            Log.d(TAG, "");
            final int num = mData.size();
            for (int i = 0; i < num; i++) {
                final SuggestionData s = mData.get(i);
                Log.d(TAG, "/" + i + "---------\\");
                Log.d(TAG, "- " + s.getSource().getShortClassName());
                Log.d(TAG, "- " + s.getTitle());
                Log.d(TAG, "- " + s.getDescription());
                Log.d(TAG, "- " + s.getIntentAction());
                Log.d(TAG, "- " + s.getIntentData());
                Log.d(TAG, "\\---------/");
            }
        }

        return super.requery();
    }

    @Override
    public double getDouble(int column) {
        return Double.valueOf(getString(column));
    }

    @Override
    public float getFloat(int column) {
        return Float.valueOf(getString(column));
    }

    @Override
    public int getInt(int column) {
        return Integer.valueOf(getString(column));
    }

    @Override
    public long getLong(int column) {
        return Long.valueOf(getString(column));
    }

    @Override
    public short getShort(int column) {
        return Short.valueOf(getString(column));
    }


    @Override
    public String getString(int columnIndex) {
        if (DBG && SPEW) Log.d(TAG, "getString(columnIndex=" + columnIndex + ")");
        return (String) getColumnValue(get(), columnIndex);
    }

    private Object getColumnValue(SuggestionData suggestion, int columnIndex) {
        switch(columnIndex) {
            case _ID: return Integer.toString(mPos);
            case FORMAT: return suggestion.getFormat();
            case TEXT_1: return suggestion.getTitle();
            case TEXT_2: return suggestion.getDescription();
            case ICON_1: return suggestion.getIcon1();
            case ICON_2: return suggestion.getIcon2();
            case QUERY: return suggestion.getIntentQuery();
            case INTENT_ACTION: return suggestion.getIntentAction();
            case INTENT_DATA: return suggestion.getIntentData();
            case ACTION_MSG_CALL: return suggestion.getActionMsgCall();
            case INTENT_EXTRA_DATA: return suggestion.getIntentExtraData();
            case INTENT_COMPONENT_NAME: return suggestion.getIntentComponentName();
            case SHORTCUT_ID: return suggestion.getShortcutId();
            case BACKGROUND_COLOR: return Integer.toString(suggestion.getBackgroundColor());
            default:
                throw new RuntimeException("we musta forgot about one of the columns :-/");
        }
    }

    @Override
    public boolean isNull(int column) {
        return getString(column) == null;
    }

    /**
     * Sets the listener which will be notified if the "more results" entry is shown, and when
     * the cursor has been closed.
     *
     * @param listener The listener. May be <code>null</code> to remove
     * the current listener.
     */
    public void setListener(CursorListener listener) {
        mListener = listener;
    }
}
