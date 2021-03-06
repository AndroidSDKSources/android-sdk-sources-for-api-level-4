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

package android.app;

import android.app.SearchManager.DialogCursorProtocol;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.server.search.SearchableInfo;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Filter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.WeakHashMap;

/**
 * Provides the contents for the suggestion drop-down list.in {@link SearchDialog}.
 *
 * @hide
 */
class SuggestionsAdapter extends ResourceCursorAdapter {

    private static final boolean DBG = false;
    private static final String LOG_TAG = "SuggestionsAdapter";

    private SearchManager mSearchManager;
    private SearchDialog mSearchDialog;
    private SearchableInfo mSearchable;
    private Context mProviderContext;
    private WeakHashMap<String, Drawable.ConstantState> mOutsideDrawablesCache;
    private SparseArray<Drawable.ConstantState> mBackgroundsCache;
    private boolean mGlobalSearchMode;
    private boolean mClosed = false;

    // Cached column indexes, updated when the cursor changes.
    private int mFormatCol;
    private int mText1Col;
    private int mText2Col;
    private int mIconName1Col;
    private int mIconName2Col;
    private int mBackgroundColorCol;
    
    // The extra used to tell a cursor to close itself. This is a hack, see the description by
    // its use later in this file.
    private static final String EXTRA_CURSOR_RESPOND_CLOSE_CURSOR = "cursor_respond_close_cursor";

    // The bundle which contains {EXTRA_CURSOR_RESPOND_CLOSE_CURSOR=true}, just cached once
    // so we don't bother recreating it a bunch.
    private final Bundle mCursorRespondCloseCursorBundle;

    // This value is stored in SuggestionsAdapter by the SearchDialog to indicate whether
    // a particular list item should be selected upon the next call to notifyDataSetChanged.
    // This is used to indicate the index of the "More results..." list item so that when
    // the data set changes after a click of "More results...", we can correctly tell the
    // ListView to scroll to the right line item. It gets reset to NONE every time it
    // is consumed.
    private int mListItemToSelect = NONE;
    static final int NONE = -1;

    // holds the maximum position that has been displayed to the user
    int mMaxDisplayed = NONE;

    // holds the position that, when displayed, should result in notifying the cursor
    int mDisplayNotifyPos = NONE;

    private final Runnable mStartSpinnerRunnable;
    private final Runnable mStopSpinnerRunnable;

    /**
     * The amount of time we delay in the filter when the user presses the delete key.
     * @see Filter#setDelayer(android.widget.Filter.Delayer).
     */
    private static final long DELETE_KEY_POST_DELAY = 500L;

    public SuggestionsAdapter(Context context, SearchDialog searchDialog,
            SearchableInfo searchable,
            WeakHashMap<String, Drawable.ConstantState> outsideDrawablesCache,
            boolean globalSearchMode) {
        super(context,
                com.android.internal.R.layout.search_dropdown_item_icons_2line,
                null,   // no initial cursor
                true);  // auto-requery
        mSearchManager = (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
        mSearchDialog = searchDialog;
        mSearchable = searchable;

        // set up provider resources (gives us icons, etc.)
        Context activityContext = mSearchable.getActivityContext(mContext);
        mProviderContext = mSearchable.getProviderContext(mContext, activityContext);

        mOutsideDrawablesCache = outsideDrawablesCache;
        mBackgroundsCache = new SparseArray<Drawable.ConstantState>();
        mGlobalSearchMode = globalSearchMode;

        mStartSpinnerRunnable = new Runnable() {
                public void run() {
                    mSearchDialog.setWorking(true);
                }
            };

        mStopSpinnerRunnable = new Runnable() {
            public void run() {
                mSearchDialog.setWorking(false);
            }
        };
        
        // Create this once because we'll reuse it a bunch.
        mCursorRespondCloseCursorBundle = new Bundle();
        mCursorRespondCloseCursorBundle.putBoolean(EXTRA_CURSOR_RESPOND_CLOSE_CURSOR, true);

        // delay 500ms when deleting
        getFilter().setDelayer(new Filter.Delayer() {

            private int mPreviousLength = 0;

            public long getPostingDelay(CharSequence constraint) {
                if (constraint == null) return 0;
                
                long delay = constraint.length() < mPreviousLength ? DELETE_KEY_POST_DELAY : 0;
                mPreviousLength = constraint.length();
                return delay;
            }
        });
    }

    /**
     * Overridden to always return <code>false</code>, since we cannot be sure that
     * suggestion sources return stable IDs.
     */
    @Override
    public boolean hasStableIds() {
        return false;
    }

    /**
     * Use the search suggestions provider to obtain a live cursor.  This will be called
     * in a worker thread, so it's OK if the query is slow (e.g. round trip for suggestions).
     * The results will be processed in the UI thread and changeCursor() will be called.
     */
    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        if (DBG) Log.d(LOG_TAG, "runQueryOnBackgroundThread(" + constraint + ")");
        String query = (constraint == null) ? "" : constraint.toString();
        if (!mGlobalSearchMode) {
            /**
             * for in app search we show the progress spinner until the cursor is returned with
             * the results.  for global search we manage the progress bar using
             * {@link DialogCursorProtocol#POST_REFRESH_RECEIVE_ISPENDING}.
             */
            mSearchDialog.getWindow().getDecorView().post(mStartSpinnerRunnable);
        }
        try {
            final Cursor cursor = mSearchManager.getSuggestions(mSearchable, query);
            // trigger fill window so the spinner stays up until the results are copied over and
            // closer to being ready
            if (!mGlobalSearchMode && cursor != null) cursor.getCount();
            return cursor;
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Search suggestions query threw an exception.", e);
            return null;
        } finally {
            if (!mGlobalSearchMode) {
                mSearchDialog.getWindow().getDecorView().post(mStopSpinnerRunnable);
            }
        }
    }

    public void close() {
        if (DBG) Log.d(LOG_TAG, "close()");
        changeCursor(null);
        mClosed = true;
    }

    /**
     * Cache columns.
     */
    @Override
    public void changeCursor(Cursor c) {
        if (DBG) Log.d(LOG_TAG, "changeCursor(" + c + ")");

        if (mClosed) {
            Log.w(LOG_TAG, "Tried to change cursor after adapter was closed.");
            if (c != null) c.close();
            return;
        }

        try {
            Cursor oldCursor = getCursor();
            super.changeCursor(c);
            
            // We send a special respond to the cursor to tell it to close itself directly because
            // it may not happen correctly for some cursors currently. This was originally
            // included as a fix to http://b/2036290, in which the search dialog was holding
            // on to references to the web search provider unnecessarily. This is being caused by
            // the fact that the cursor is not being correctly closed in
            // BulkCursorToCursorAdapter#close, which remains unfixed (see http://b/2015069).
            //
            // TODO: Remove this hack once http://b/2015069 is fixed.
            if (oldCursor != null && oldCursor != c) {
                oldCursor.respond(mCursorRespondCloseCursorBundle);
            }
            
            if (c != null) {
                mFormatCol = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_FORMAT);
                mText1Col = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1);
                mText2Col = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_2);
                mIconName1Col = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_1);
                mIconName2Col = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_2);
                mBackgroundColorCol = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_BACKGROUND_COLOR);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "error changing cursor and caching columns", e);
        }
    }

    @Override
    public void notifyDataSetChanged() {
        if (DBG) Log.d(LOG_TAG, "notifyDataSetChanged");
        super.notifyDataSetChanged();

        callCursorPostRefresh(mCursor);

        // look out for the pending item we are supposed to scroll to
        if (mListItemToSelect != NONE) {
            mSearchDialog.setListSelection(mListItemToSelect);
            mListItemToSelect = NONE;
        }
    }

    /**
     * Handle sending and receiving information associated with
     * {@link DialogCursorProtocol#POST_REFRESH}.
     *
     * @param cursor The cursor to call.
     */
    private void callCursorPostRefresh(Cursor cursor) {
        if (!mGlobalSearchMode) return;
        final Bundle request = new Bundle();
        request.putInt(DialogCursorProtocol.METHOD, DialogCursorProtocol.POST_REFRESH);
        final Bundle response = cursor.respond(request);

        mSearchDialog.setWorking(
                response.getBoolean(DialogCursorProtocol.POST_REFRESH_RECEIVE_ISPENDING, false));

        mDisplayNotifyPos =
                response.getInt(DialogCursorProtocol.POST_REFRESH_RECEIVE_DISPLAY_NOTIFY, -1);
    }

    /**
     * Tell the cursor which position was clicked, handling sending and receiving information
     * associated with {@link DialogCursorProtocol#CLICK}.
     *
     * @param cursor The cursor
     * @param position The position that was clicked.
     */
    void callCursorOnClick(Cursor cursor, int position) {
        if (!mGlobalSearchMode) return;
        final Bundle request = new Bundle(1);
        request.putInt(DialogCursorProtocol.METHOD, DialogCursorProtocol.CLICK);
        request.putInt(DialogCursorProtocol.CLICK_SEND_POSITION, position);
        request.putInt(DialogCursorProtocol.CLICK_SEND_MAX_DISPLAY_POS, mMaxDisplayed);
        final Bundle response = cursor.respond(request);
        mMaxDisplayed = -1;
        mListItemToSelect = response.getInt(
                DialogCursorProtocol.CLICK_RECEIVE_SELECTED_POS, SuggestionsAdapter.NONE);
    }

    /**
     * Tags the view with cached child view look-ups.
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View v = super.newView(context, cursor, parent);
        v.setTag(new ChildViewCache(v));
        return v;
    }

    /**
     * Cache of the child views of drop-drown list items, to avoid looking up the children
     * each time the contents of a list item are changed.
     */
    private final static class ChildViewCache {
        public final TextView mText1;
        public final TextView mText2;
        public final ImageView mIcon1;
        public final ImageView mIcon2;

        public ChildViewCache(View v) {
            mText1 = (TextView) v.findViewById(com.android.internal.R.id.text1);
            mText2 = (TextView) v.findViewById(com.android.internal.R.id.text2);
            mIcon1 = (ImageView) v.findViewById(com.android.internal.R.id.icon1);
            mIcon2 = (ImageView) v.findViewById(com.android.internal.R.id.icon2);
        }
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ChildViewCache views = (ChildViewCache) view.getTag();
        final int pos = cursor.getPosition();

        // update the maximum position displayed since last refresh
        if (pos > mMaxDisplayed) {
            mMaxDisplayed = pos;
        }

        // if the cursor wishes to be notified about this position, send it
        if (mGlobalSearchMode && mDisplayNotifyPos != NONE && pos == mDisplayNotifyPos) {
            final Bundle request = new Bundle();
            request.putInt(DialogCursorProtocol.METHOD, DialogCursorProtocol.THRESH_HIT);
            mCursor.respond(request);
            mDisplayNotifyPos = NONE;  // only notify the first time
        }

        int backgroundColor = 0;
        if (mBackgroundColorCol != -1) {
            backgroundColor = cursor.getInt(mBackgroundColorCol);
        }
        Drawable background = getItemBackground(backgroundColor);
        view.setBackgroundDrawable(background);

        final boolean isHtml = mFormatCol > 0 && "html".equals(cursor.getString(mFormatCol));
        setViewText(cursor, views.mText1, mText1Col, isHtml);
        setViewText(cursor, views.mText2, mText2Col, isHtml);

        if (views.mIcon1 != null) {
            setViewDrawable(views.mIcon1, getIcon1(cursor));
        }
        if (views.mIcon2 != null) {
            setViewDrawable(views.mIcon2, getIcon2(cursor));
        }
    }

    /**
     * Gets a drawable with no color when selected or pressed, and the given color when
     * neither selected nor pressed.
     *
     * @return A drawable, or {@code null} if the given color is transparent.
     */
    private Drawable getItemBackground(int backgroundColor) {
        if (backgroundColor == 0) {
            return null;
        } else {
            Drawable.ConstantState cachedBg = mBackgroundsCache.get(backgroundColor);
            if (cachedBg != null) {
                if (DBG) Log.d(LOG_TAG, "Background cache hit for color " + backgroundColor);
                return cachedBg.newDrawable();
            }
            if (DBG) Log.d(LOG_TAG, "Creating new background for color " + backgroundColor);
            ColorDrawable transparent = new ColorDrawable(0);
            ColorDrawable background = new ColorDrawable(backgroundColor);
            StateListDrawable newBg = new StateListDrawable();
            newBg.addState(new int[]{android.R.attr.state_selected}, transparent);
            newBg.addState(new int[]{android.R.attr.state_pressed}, transparent);
            newBg.addState(new int[]{}, background);
            mBackgroundsCache.put(backgroundColor, newBg.getConstantState());
            return newBg;
        }
    }

    private void setViewText(Cursor cursor, TextView v, int textCol, boolean isHtml) {
        if (v == null) {
            return;
        }
        CharSequence text = null;
        if (textCol >= 0) {
            String str = cursor.getString(textCol);
            if (isHtml && looksLikeHtml(str)) {
                text = Html.fromHtml(str);
            } else {
                text = str;
            }
        }
        // Set the text even if it's null, since we need to clear any previous text.
        v.setText(text);

        if (TextUtils.isEmpty(text)) {
            v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);
        }
    }

    private static boolean looksLikeHtml(String str) {
        if (TextUtils.isEmpty(str)) return false;
        for (int i = str.length() - 1; i >= 0; i--) {
            char c = str.charAt(i);
            if (c == '<' || c == '&') return true;
        }
        return false;
    }

    private Drawable getIcon1(Cursor cursor) {
        if (mIconName1Col < 0) {
            return null;
        }
        String value = cursor.getString(mIconName1Col);
        Drawable drawable = getDrawableFromResourceValue(value);
        if (drawable != null) {
            return drawable;
        }
        return getDefaultIcon1(cursor);
    }

    private Drawable getIcon2(Cursor cursor) {
        if (mIconName2Col < 0) {
            return null;
        }
        String value = cursor.getString(mIconName2Col);
        return getDrawableFromResourceValue(value);
    }

    /**
     * Sets the drawable in an image view, makes sure the view is only visible if there
     * is a drawable.
     */
    private void setViewDrawable(ImageView v, Drawable drawable) {
        // Set the icon even if the drawable is null, since we need to clear any
        // previous icon.
        v.setImageDrawable(drawable);

        if (drawable == null) {
            v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);

            // This is a hack to get any animated drawables (like a 'working' spinner)
            // to animate. You have to setVisible true on an AnimationDrawable to get
            // it to start animating, but it must first have been false or else the
            // call to setVisible will be ineffective. We need to clear up the story
            // about animated drawables in the future, see http://b/1878430.
            drawable.setVisible(false, false);
            drawable.setVisible(true, false);
        }
    }

    /**
     * Gets the text to show in the query field when a suggestion is selected.
     *
     * @param cursor The Cursor to read the suggestion data from. The Cursor should already
     *        be moved to the suggestion that is to be read from.
     * @return The text to show, or <code>null</code> if the query should not be
     *         changed when selecting this suggestion.
     */
    @Override
    public CharSequence convertToString(Cursor cursor) {
        if (cursor == null) {
            return null;
        }

        String query = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_QUERY);
        if (query != null) {
            return query;
        }

        if (mSearchable.shouldRewriteQueryFromData()) {
            String data = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_INTENT_DATA);
            if (data != null) {
                return data;
            }
        }

        if (mSearchable.shouldRewriteQueryFromText()) {
            String text1 = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_TEXT_1);
            if (text1 != null) {
                return text1;
            }
        }

        return null;
    }

    /**
     * This method is overridden purely to provide a bit of protection against
     * flaky content providers.
     *
     * @see android.widget.ListAdapter#getView(int, View, ViewGroup)
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            return super.getView(position, convertView, parent);
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Search suggestions cursor threw exception.", e);
            // Put exception string in item title
            View v = newView(mContext, mCursor, parent);
            if (v != null) {
                ChildViewCache views = (ChildViewCache) v.getTag();
                TextView tv = views.mText1;
                tv.setText(e.toString());
            }
            return v;
        }
    }

    /**
     * Gets a drawable given a value provided by a suggestion provider.
     *
     * This value could be just the string value of a resource id
     * (e.g., "2130837524"), in which case we will try to retrieve a drawable from
     * the provider's resources. If the value is not an integer, it is
     * treated as a Uri and opened with
     * {@link ContentResolver#openOutputStream(android.net.Uri, String)}.
     *
     * All resources and URIs are read using the suggestion provider's context.
     *
     * If the string is not formatted as expected, or no drawable can be found for
     * the provided value, this method returns null.
     *
     * @param drawableId a string like "2130837524",
     *        "android.resource://com.android.alarmclock/2130837524",
     *        or "content://contacts/photos/253".
     * @return a Drawable, or null if none found
     */
    private Drawable getDrawableFromResourceValue(String drawableId) {
        if (drawableId == null || drawableId.length() == 0 || "0".equals(drawableId)) {
            return null;
        }

        // First, check the cache.
        Drawable.ConstantState cached = mOutsideDrawablesCache.get(drawableId);
        if (cached != null) {
            if (DBG) Log.d(LOG_TAG, "Found icon in cache: " + drawableId);
            return cached.newDrawable();
        }

        Drawable drawable = null;
        try {
            // Not cached, try using it as a plain resource ID in the provider's context.
            int resourceId = Integer.parseInt(drawableId);
            drawable = mProviderContext.getResources().getDrawable(resourceId);
            if (DBG) Log.d(LOG_TAG, "Found icon by resource ID: " + drawableId);
        } catch (NumberFormatException nfe) {
            // The id was not an integer resource id.
            // Let the ContentResolver handle content, android.resource and file URIs.
            try {
                Uri uri = Uri.parse(drawableId);
                InputStream stream = mProviderContext.getContentResolver().openInputStream(uri);
                if (stream != null) {
                    try {
                        drawable = Drawable.createFromStream(stream, null);
                    } finally {
                        try {
                            stream.close();
                        } catch (IOException ex) {
                            Log.e(LOG_TAG, "Error closing icon stream for " + uri, ex);
                        }
                    }
                }
                if (DBG) Log.d(LOG_TAG, "Opened icon input stream: " + drawableId);
            } catch (FileNotFoundException fnfe) {
                if (DBG) Log.d(LOG_TAG, "Icon stream not found: " + drawableId);
                // drawable = null;
            }

            // If we got a drawable for this resource id, then stick it in the
            // map so we don't do this lookup again.
            if (drawable != null) {
                mOutsideDrawablesCache.put(drawableId, drawable.getConstantState());
            }
        } catch (Resources.NotFoundException nfe) {
            if (DBG) Log.d(LOG_TAG, "Icon resource not found: " + drawableId);
            // drawable = null;
        }

        return drawable;
    }

    /**
     * Gets the left-hand side icon that will be used for the current suggestion
     * if the suggestion contains an icon column but no icon or a broken icon.
     *
     * @param cursor A cursor positioned at the current suggestion.
     * @return A non-null drawable.
     */
    private Drawable getDefaultIcon1(Cursor cursor) {
        // First check the component that the suggestion is originally from
        String c = getColumnString(cursor, SearchManager.SUGGEST_COLUMN_INTENT_COMPONENT_NAME);
        if (c != null) {
            ComponentName component = ComponentName.unflattenFromString(c);
            if (component != null) {
                Drawable drawable = getActivityIconWithCache(component);
                if (drawable != null) {
                    return drawable;
                }
            } else {
                Log.w(LOG_TAG, "Bad component name: " + c);
            }
        }

        // Then check the component that gave us the suggestion
        Drawable drawable = getActivityIconWithCache(mSearchable.getSearchActivity());
        if (drawable != null) {
            return drawable;
        }

        // Fall back to a default icon
        return mContext.getPackageManager().getDefaultActivityIcon();
    }

    /**
     * Gets the activity or application icon for an activity.
     * Uses the local icon cache for fast repeated lookups.
     *
     * @param component Name of an activity.
     * @return A drawable, or {@code null} if neither the activity nor the application
     *         has an icon set.
     */
    private Drawable getActivityIconWithCache(ComponentName component) {
        // First check the icon cache
        String componentIconKey = component.flattenToShortString();
        // Using containsKey() since we also store null values.
        if (mOutsideDrawablesCache.containsKey(componentIconKey)) {
            Drawable.ConstantState cached = mOutsideDrawablesCache.get(componentIconKey);
            return cached == null ? null : cached.newDrawable();
        }
        // Then try the activity or application icon
        Drawable drawable = getActivityIcon(component);
        // Stick it in the cache so we don't do this lookup again.
        Drawable.ConstantState toCache = drawable == null ? null : drawable.getConstantState();
        mOutsideDrawablesCache.put(componentIconKey, toCache);
        return drawable;
    }

    /**
     * Gets the activity or application icon for an activity.
     *
     * @param component Name of an activity.
     * @return A drawable, or {@code null} if neither the acitivy or the application
     *         have an icon set.
     */
    private Drawable getActivityIcon(ComponentName component) {
        PackageManager pm = mContext.getPackageManager();
        final ActivityInfo activityInfo;
        try {
            activityInfo = pm.getActivityInfo(component, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException ex) {
            Log.w(LOG_TAG, ex.toString());
            return null;
        }
        int iconId = activityInfo.getIconResource();
        if (iconId == 0) return null;
        String pkg = component.getPackageName();
        Drawable drawable = pm.getDrawable(pkg, iconId, activityInfo.applicationInfo);
        if (drawable == null) {
            Log.w(LOG_TAG, "Invalid icon resource " + iconId + " for "
                    + component.flattenToShortString());
            return null;
        }
        return drawable;
    }

    /**
     * Gets the value of a string column by name.
     *
     * @param cursor Cursor to read the value from.
     * @param columnName The name of the column to read.
     * @return The value of the given column, or <code>null</null>
     *         if the cursor does not contain the given column.
     */
    public static String getColumnString(Cursor cursor, String columnName) {
        int col = cursor.getColumnIndex(columnName);
        if (col == NONE) {
            return null;
        }
        try {
            return cursor.getString(col);
        } catch (Exception e) {
            Log.e(LOG_TAG,
                    "unexpected error retrieving valid column from cursor, "
                            + "did the remote process die?", e);
            return null;
        }
    }

}
