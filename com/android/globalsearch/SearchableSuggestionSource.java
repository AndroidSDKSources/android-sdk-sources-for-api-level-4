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
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.server.search.SearchableInfo;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Suggestion source that uses the {@link SearchableInfo} of a given component
 * to get suggestions.
 */
public class SearchableSuggestionSource extends AbstractSuggestionSource {

    private static final boolean DBG = false;
    private static final String LOG_TAG = SearchableSuggestionSource.class.getSimpleName();

    private Context mContext;

    private SearchableInfo mSearchable;

    private ActivityInfo mActivityInfo;

    // Flattend name of the searchable activity
    private String mFlattenedComponentName;

    // Prefix for URIs for resources in the package of the searchable activity
    private String mPackageResourceUriPrefix;
    // Prefix for URIs for resources in the package of the suggestion provider.
    private String mProviderResourceUriPrefix;

    // Cached label for the activity
    private String mLabel;

    // Cached icon for the activity
    private String mIcon;
    
    // An override value for the max number of results to provide.
    private int mMaxResultsOverride;

    // Action key info for KEYCODE_CALL
    private String mCallActionMsg = null;
    private String mCallActionMsgCol = null;

    // A private column the web search source uses to instruct us to pin a result
    // (like "Manage search history") to the bottom of the list when appropriate.
    private static final String SUGGEST_COLUMN_PIN_TO_BOTTOM = "suggest_pin_to_bottom";

    public SearchableSuggestionSource(Context context, SearchableInfo searchable) {
        mContext = context;
        mSearchable = searchable;
        ComponentName componentName = mSearchable.getSearchActivity();
        mFlattenedComponentName = componentName.flattenToShortString();
        try {
            mActivityInfo = context.getPackageManager()
                    .getActivityInfo(componentName, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException ex) {
            throw new RuntimeException("Searchable activity " + componentName + " not found.");
        }
        mPackageResourceUriPrefix = "android.resource://" + componentName.getPackageName() + "/";

        // The suggestions may come from a provider different than the activity which contains the
        // searchable (e.g. Music app pointing to the system provided media provider). So find out
        // the provider package and form a resource URI out of it for suggestion icons.
        String suggestProviderPackage = componentName.getPackageName();
        String suggestAuthority = mSearchable.getSuggestAuthority();
        if (suggestAuthority != null) {
            ProviderInfo pi = mContext.getPackageManager().resolveContentProvider(
                    suggestAuthority, 0);
            if (pi != null) suggestProviderPackage = pi.packageName;
        }
        mProviderResourceUriPrefix = "android.resource://" + suggestProviderPackage + "/";

        mLabel = findLabel();
        mIcon = findIcon();
        mMaxResultsOverride = 0;

        SearchableInfo.ActionKeyInfo actionCall = mSearchable.findActionKey(KeyEvent.KEYCODE_CALL);
        if (actionCall != null) {
            mCallActionMsg = actionCall.getSuggestActionMsg();
            mCallActionMsgCol = actionCall.getSuggestActionMsgColumn();
        }

    }
    
    public SearchableSuggestionSource(Context context, SearchableInfo searchable,
            int maxResultsOverride) {
        this(context, searchable);
        mMaxResultsOverride = maxResultsOverride;
    }

    /**
     * Gets the Context that this suggestion source runs in.
     */
    public Context getContext() {
        return mContext;
    }

    @Override
    public int getQueryThreshold() {
        return mSearchable.getSuggestThreshold();
    }

    /**
     * Gets the localized, human-readable label for this source.
     */
    public String getLabel() {
        return mLabel;
    }

    /**
     * Gets the icon for this suggestion source as an android.resource: URI.
     */
    public String getIcon() {
        return mIcon;
    }
    
    /**
     * Gets the description to use for this source in system search settings.
     */
    public String getSettingsDescription() {
        return mSearchable.getSettingsDescription();
    }

    /**
     * Gets the name of the activity that this source is for.
     */
    public ComponentName getComponentName() {
        return mSearchable.getSearchActivity();
    }

    @Override
    public SuggestionResult getSuggestions(String query, int maxResults, int queryLimit) {
        Cursor cursor = getCursor(query, queryLimit);
        // Be resilient to non-existent suggestion providers, as the build this is running on
        // is not guaranteed to have anything in particular.
        if (cursor == null) {
            if (DBG) Log.d(LOG_TAG, getComponentName().flattenToShortString() + " returned null");
            return SuggestionResult.createErrorResult(this);
        }

        try {
            // Return without touching the cursor if we have been interrupted. This avoids
            // filling cursor windows and triggering evaluation of lazy cursors.
            if (Thread.interrupted()) {
                if (DBG) Log.d(LOG_TAG, "Interrupted");
                return SuggestionResult.createCancelled(this);
            }

            maxResults = (mMaxResultsOverride > 0) ? mMaxResultsOverride : maxResults;

            int count = cursor.getCount();
            if (DBG) {
                Log.d(LOG_TAG, getComponentName().flattenToShortString()
                        + " returned " + count + " suggestions");
            }
            ColumnCachingCursor myCursor = new ColumnCachingCursor(cursor, mCallActionMsgCol);
            ArrayList<SuggestionData> suggestions = new ArrayList<SuggestionData>(count);
            while (cursor.moveToNext() && suggestions.size() < maxResults) {
                if (Thread.interrupted()) {
                    if (DBG) Log.d(LOG_TAG, "Interrupted");
                    return SuggestionResult.createCancelled(this);
                }
                SuggestionData suggestion = makeSuggestion(myCursor);
                if (suggestion != null) {
                    suggestions.add(suggestion);
                }
            }
            return new SuggestionResult(this, suggestions, count, queryLimit);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Gets a Cursor containing suggestions.
     *
     * @param query The query to get suggestions for.
     * @return A Cursor.
     */
    protected Cursor getCursor(String query, int queryLimit) {
        return getSuggestions(getContext(), mSearchable, query, queryLimit);
    }

    /**
     * This is a copy of {@link SearchManager#getSuggestions(SearchableInfo, String)}.
     * The only difference is that it adds "?limit={maxResults}".
     */
    private static Cursor getSuggestions(Context context, SearchableInfo searchable, String query,
            int queryLimit) {
        if (searchable == null) {
            return null;
        }

        String authority = searchable.getSuggestAuthority();
        if (authority == null) {
            return null;
        }

        Uri.Builder uriBuilder = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority);

        // if content path provided, insert it now
        final String contentPath = searchable.getSuggestPath();
        if (contentPath != null) {
            uriBuilder.appendEncodedPath(contentPath);
        }

        // append standard suggestion query path
        uriBuilder.appendPath(SearchManager.SUGGEST_URI_PATH_QUERY);

        // get the query selection, may be null
        String selection = searchable.getSuggestSelection();
        // inject query, either as selection args or inline
        String[] selArgs = null;
        if (selection != null) {    // use selection if provided
            selArgs = new String[] { query };
        } else {                    // no selection, use REST pattern
            uriBuilder.appendPath(query);
        }

        uriBuilder.appendQueryParameter("limit", String.valueOf(queryLimit));

        Uri uri = uriBuilder
                .fragment("")  // TODO: Remove, workaround for a bug in Uri.writeToParcel()
                .build();

        // finally, make the query
        if (DBG) {
            Log.d(LOG_TAG, "query(" + uri + ",null," + selection + ","
                    + Arrays.toString(selArgs) + ",null)");
        }
        return context.getContentResolver().query(uri, null, selection, selArgs, null);
    }

    @Override
    protected SuggestionData validateShortcut(SuggestionData shortcut) {
        Cursor cursor = getValidationCursor(shortcut);
        if (cursor == null) return null;
        
        try {
            if (Thread.interrupted()) {
                if (DBG) Log.d(LOG_TAG, "Interrupted");
                return null;
            }
            int count = cursor.getCount();
            if (count == 0) return null;
            if (count > 1) {
                Log.w(LOG_TAG, "received " + count + " results for validation of a single shortcut");
            }
            cursor.moveToNext();
            return makeSuggestion(new ColumnCachingCursor(cursor, mCallActionMsgCol));
        } finally {
          cursor.close();
        }
    }

    protected Cursor getValidationCursor(SuggestionData shortcut) {
        String shortcutId = shortcut.getShortcutId();
        String extraData = shortcut.getIntentExtraData();

        String authority = mSearchable.getSuggestAuthority();
        if (authority == null) {
            return null;
        }

        Uri.Builder uriBuilder = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(authority);

        // if content path provided, insert it now
        final String contentPath = mSearchable.getSuggestPath();
        if (contentPath != null) {
            uriBuilder.appendEncodedPath(contentPath);
        }

        // append the shortcut path and id
        uriBuilder.appendPath(SearchManager.SUGGEST_URI_PATH_SHORTCUT);
        uriBuilder.appendPath(shortcutId);

        Uri uri = uriBuilder
                .appendQueryParameter(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA, extraData)
                .fragment("")  // TODO: Remove, workaround for a bug in Uri.writeToParcel()
                .build();

        // finally, make the query
        return getContext().getContentResolver().query(uri, null, null, null, null);
    }

    /**
     * Builds a suggestion for the current entry in the cursor.
     * The default implementation calls {@link #getTitle(ColumnCachingCursor)} and friends.
     * Subclasses may override this method if overriding the methods for
     * the individual fields is not enough.
     *
     * @return A suggestion, or <code>null</code> if no suggestion can be made
     * from the current record.
     */
    protected SuggestionData makeSuggestion(ColumnCachingCursor cursor) {
        String format = getFormat(cursor);
        String title = getTitle(cursor);
        String description = getDescription(cursor);
        if (description == null) {
            description = "";
        }
        String icon1 = getIcon1(cursor);
        String icon2 = getIcon2(cursor);
        String intentAction = getIntentAction(cursor);
        if (intentAction == null) {
            intentAction = Intent.ACTION_DEFAULT;
        }
        String intentData = getIntentData(cursor);
        String query = getQuery(cursor);
        String actionMsgCall = getActionMsgCall(cursor);
        String intentExtraData = getIntentExtraData(cursor);
        String shortcutId = getShortcutId(cursor);
        boolean pinToBottom = isPinToBottom(cursor);
        boolean spinnerWhileRefreshing = isSpinnerWhileRefreshing(cursor);

        // note: avoiding using SuggestionData.Builder in this case to avoid object allocation, and
        // this is the location where suggestions are created the most.
        return new SuggestionData(
                getComponentName(),
                format,
                title,
                description,
                icon1,
                icon2,
                intentAction,
                intentData,
                query,
                actionMsgCall,
                intentExtraData,
                // The following overwrites any value provided by the searchable since we only
                // direct intents provided by third-party searchables to that searchable activity.
                mFlattenedComponentName,
                shortcutId,
                0,  // background color
                pinToBottom,
                spinnerWhileRefreshing);
    }

    /**
     * Gets the text format.
     *
     * @return The value of the optional {@link SearchManager#SUGGEST_COLUMN_FORMAT} column,
     *         or <code>null</code> if the cursor does not contain that column.
     */
    protected String getFormat(ColumnCachingCursor cursor) {
        return cursor.getColumnString(ColumnCachingCursor.FORMAT);
    }

    /**
     * Gets the text to put in the first line of the suggestion for the current entry.
     * Subclasses may want to override this to provide better titles.
     *
     * @return The value of the required {@link SearchManager#SUGGEST_COLUMN_TEXT_1} column.
     */
    protected String getTitle(ColumnCachingCursor cursor) {
        return cursor.getColumnString(ColumnCachingCursor.TEXT_1);
    }

    /**
     * Gets the text to put in the second line of the suggestion for the current entry.
     * Subclasses may want to override this to provide better descriptions.
     *
     * @return The value of the {@link SearchManager#SUGGEST_COLUMN_TEXT_1} column.
     * or <code>null</code> if the cursor does not contain that column.
     */
    protected String getDescription(ColumnCachingCursor cursor) {
        return cursor.getColumnString(ColumnCachingCursor.TEXT_2);
    }

    /**
     * Gets the first icon for the current entry (displayed on the left side of
     * the suggestion). This should be a string containing the resource ID or URI
     * of a {@link Drawable}.
     *
     * If no icon was provided in the cursor, the default icon for this searchable
     * (provided by {@link #getIcon()}) will be used.
     *
     * Subclasses may want to override this to provide better icons.
     *
     * @return The value of the {@link SearchManager#SUGGEST_COLUMN_ICON_1} column,
     * or the value of {@link #getIcon()} if the cursor does not contain that column.
     */
    protected String getIcon1(ColumnCachingCursor cursor) {
        // Get the icon provided in the cursor. If none, get the source's icon.
        String icon = getIcon(cursor, ColumnCachingCursor.ICON_1);
        if (icon == null) {
            icon = getIcon();  // the app's icon
        }
        return icon;
    }

    /**
     * Gets the second icon for the current entry (displayed on the right side of
     * the suggestion). This should be a string containing the resource ID or URI
     * of a {@link Drawable}.
     *
     * Subclasses may want to override this to provide better icons.
     *
     * @return The value of the {@link SearchManager#SUGGEST_COLUMN_ICON_2} column,
     * or <code>null</code> if the cursor does not contain that column.
     */
    protected String getIcon2(ColumnCachingCursor cursor) {
        return getIcon(cursor, ColumnCachingCursor.ICON_2);
    }

    /**
     * Gets an icon URI from a cursor. If the cursor returns a resource ID,
     * this is converted into an android.resource:// URI.
     */
    protected String getIcon(ColumnCachingCursor cursor, int key) {
        String icon = cursor.getColumnString(key);
        if (icon == null || icon.length() == 0 || "0".equals(icon)) {
            // SearchManager specifies that null or zero can be returned to indicate
            // no icon. We also allow empty string.
            return null;
        } else if (!Character.isDigit(icon.charAt(0))){
            return icon;
        } else {
            return new StringBuilder(mProviderResourceUriPrefix).append(icon).toString();
        }
    }

    /**
     * Gets the intent action for the current entry.
     */
    protected String getIntentAction(ColumnCachingCursor cursor) {
        String intentAction = cursor.getColumnString(ColumnCachingCursor.INTENT_ACTION);
        if (intentAction == null) {
            intentAction = mSearchable.getSuggestIntentAction();
        }
        return intentAction;
    }

    /**
     * Gets the intent data for the current entry. This includes the value of
     * {@link SearchManager#SUGGEST_COLUMN_INTENT_DATA_ID}.
     */
    protected String getIntentData(ColumnCachingCursor cursor) {
        String intentData = cursor.getColumnString(ColumnCachingCursor.INTENT_DATA);
        if (intentData == null) {
            intentData = mSearchable.getSuggestIntentData();
        }
        if (intentData == null) {
            return null;
        }
        String intentDataId = cursor.getColumnString(ColumnCachingCursor.INTENT_DATA_ID);
        return intentDataId == null ? intentData : intentData + "/" + Uri.encode(intentDataId);
    }

    /**
     * Gets the intent extra data for the current entry.
     *
     * @return The value of the {@link SearchManager#SUGGEST_COLUMN_INTENT_EXTRA_DATA} column,
     * or <code>null</code> if the cursor does not contain that column.
     */
    protected String getIntentExtraData(ColumnCachingCursor cursor) {
        return cursor.getColumnString(ColumnCachingCursor.INTENT_EXTRA_DATA);
    }

    /**
     * Gets the search query for the current entry.
     *
     * @return The value of the {@link SearchManager#SUGGEST_COLUMN_QUERY} column,
     * or <code>null</code> if the cursor does not contain that column.
     */
    protected String getQuery(ColumnCachingCursor cursor) {
        return cursor.getColumnString(ColumnCachingCursor.QUERY);
    }

    /**
     * Gets the shortcut id for the current entry.
     *
     * @return The value of the {@link SearchManager#SUGGEST_COLUMN_SHORTCUT_ID} column,
     * or <code>null</code> if the cursor does not contain that column.
     */
    protected String getShortcutId(ColumnCachingCursor cursor) {
        return cursor.getColumnString(ColumnCachingCursor.SHORTCUT_ID);
    }
    
    /**
     * Determines whether this suggestion is a pin-to-bottom suggestion.
     * 
     * @return The value of the {@link #SUGGEST_COLUMN_PIN_TO_BOTTOM} column, or
     * <code>false</code> if the cursor does not contain that column.
     */
    protected boolean isPinToBottom(ColumnCachingCursor cursor) {
        return "true".equals(cursor.getColumnString(ColumnCachingCursor.PIN_TO_BOTTOM));
    }
    
    /**
     * Determines whether this suggestion should show a spinner while refreshing.
     * 
     * @return The value of the {@link SearchManager#SUGGEST_COLUMN_SPINNER_WHILE_REFRESHING}
     * column, or <code>false</code> if the cursor does not contain that column.
     */
    protected boolean isSpinnerWhileRefreshing(ColumnCachingCursor cursor) {
        return "true".equals(cursor.getColumnString(ColumnCachingCursor.SPINNER_WHILE_REFRESHING));
    }

    /**
     * Gets the action message for the CALL key for the current entry.
     */
    protected String getActionMsgCall(ColumnCachingCursor cursor) {
        String suggestActionMsg = cursor.getColumnString(ColumnCachingCursor.ACTION_MSG_CALL);
        if (suggestActionMsg == null) {
            suggestActionMsg = mCallActionMsg;
        }
        return suggestActionMsg;
    }

    private String findLabel() {
        CharSequence label = null;
        PackageManager pm = getContext().getPackageManager();
        // First try the activity label
        int labelRes = mActivityInfo.labelRes;
        if (labelRes != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(mActivityInfo.applicationInfo);
                label = resources.getString(labelRes);
            } catch (NameNotFoundException ex) {
                // shouldn't happen, but if it does, let label remain null
            }
        }
        // Fall back to the application label
        if (label == null) {
            label = pm.getApplicationLabel(mActivityInfo.applicationInfo);
            if (DBG) Log.d(LOG_TAG, getComponentName() + " application label = " + label);
        }
        if (label == null) {
            return null;
        }
        return label.toString();
    }

    private String findIcon() {
        // Try the activity or application icon.
        int iconId = mActivityInfo.getIconResource();
        if (DBG) Log.d(LOG_TAG, getComponentName() + " activity icon = " + iconId);
        // No icon, use default activity icon
        if (iconId == 0) {
            iconId = android.R.drawable.sym_def_app_icon;
        }
        return new StringBuilder(mPackageResourceUriPrefix).append(iconId).toString();
    }

    @Override
    public String toString() {
        return super.toString() + "{component=" + mFlattenedComponentName + "}";
    }

    /**
     * Factory method. Creates a suggestion source from the searchable
     * information of a given component.
     *
     * @param context Context to use in the suggestion source.
     * @param componentName Component whose searchable information will be
     * used to construct ths suggestion source.
     * @return A suggestion source, or <code>null</code> if the given component
     * is not searchable.
     */
    public static SearchableSuggestionSource create(Context context, ComponentName componentName) {
        SearchManager searchManager = (SearchManager)
                context.getSystemService(Context.SEARCH_SERVICE);
        SearchableInfo si = searchManager.getSearchableInfo(componentName, false);
        if (si == null) {
            return null;
        }
        return new SearchableSuggestionSource(context, si);
    }
    
    /**
     * Factory method. Creates a suggestion source from the searchable
     * information of a given component.
     *
     * @param context Context to use in the suggestion source.
     * @param componentName Component whose searchable information will be
     * used to construct this suggestion source.
     * @param maxResultsOverride An override value to use for the number of results that
     * this source should be allowed to provide.
     * @return A suggestion source, or <code>null</code> if the given component
     * is not searchable.
     */
    public static SearchableSuggestionSource create(Context context, ComponentName componentName,
            int maxResultsOverride) {
        SearchManager searchManager = (SearchManager)
                context.getSystemService(Context.SEARCH_SERVICE);
        SearchableInfo si = searchManager.getSearchableInfo(componentName, false);
        if (si == null) {
            return null;
        }
        return new SearchableSuggestionSource(context, si, maxResultsOverride);
    }

    /**
     * Checks whether this source needs to be invoked after an earlier query returned zero results.
     *
     * @return <code>true</code> if this source needs to be invoked after returning zero results.
     */
    public boolean queryAfterZeroResults() {
        return mSearchable.queryAfterZeroResults();
    }

    /**
     * Wraps a cursor and caches column indexes.
     */
    public static class ColumnCachingCursor extends CursorWrapper {
        // These index into mIndices.
        public static final int FORMAT = 0;
        public static final int TEXT_1 = FORMAT + 1;
        public static final int TEXT_2 = TEXT_1 + 1;
        public static final int ICON_1 = TEXT_2 + 1;
        public static final int ICON_2 = ICON_1 + 1;
        public static final int QUERY = ICON_2 + 1;
        public static final int INTENT_ACTION = QUERY + 1;
        public static final int INTENT_DATA = INTENT_ACTION + 1;
        public static final int INTENT_DATA_ID = INTENT_DATA + 1;
        public static final int INTENT_EXTRA_DATA = INTENT_DATA_ID + 1;
        public static final int SHORTCUT_ID = INTENT_EXTRA_DATA + 1;
        public static final int SPINNER_WHILE_REFRESHING = SHORTCUT_ID + 1;
        public static final int PIN_TO_BOTTOM = SPINNER_WHILE_REFRESHING + 1;
        public static final int ACTION_MSG_CALL = PIN_TO_BOTTOM + 1;
        private static final int KEY_COUNT = ACTION_MSG_CALL + 1;

        // Maps column names to the constants above
        private static final HashMap<String,Integer> mKeys = buildKeys();

        private static HashMap<String,Integer> buildKeys() {
            HashMap<String,Integer> map = new HashMap<String,Integer>();
            map.put(SearchManager.SUGGEST_COLUMN_FORMAT, FORMAT);
            map.put(SearchManager.SUGGEST_COLUMN_TEXT_1, TEXT_1);
            map.put(SearchManager.SUGGEST_COLUMN_TEXT_2, TEXT_2);
            map.put(SearchManager.SUGGEST_COLUMN_ICON_1, ICON_1);
            map.put(SearchManager.SUGGEST_COLUMN_ICON_2, ICON_2);
            map.put(SearchManager.SUGGEST_COLUMN_QUERY, QUERY);
            map.put(SearchManager.SUGGEST_COLUMN_INTENT_ACTION, INTENT_ACTION);
            map.put(SearchManager.SUGGEST_COLUMN_INTENT_DATA, INTENT_DATA);
            map.put(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID, INTENT_DATA_ID);
            map.put(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA, INTENT_EXTRA_DATA);
            map.put(SearchManager.SUGGEST_COLUMN_SHORTCUT_ID, SHORTCUT_ID);
            map.put(SearchManager.SUGGEST_COLUMN_SPINNER_WHILE_REFRESHING,
                    SPINNER_WHILE_REFRESHING);
            map.put(SUGGEST_COLUMN_PIN_TO_BOTTOM, PIN_TO_BOTTOM);
            return map;
        }

        // For each column constant above, this contains the column index in the cursor, or -1.
        private int[] mIndices;

        /**
         * Creates a column index cache.
         *
         * @param cursor A suggestion cursor.
         * @param actionCallColumn The name of the KEYCODE_CALL action column, if any.
         */
        public ColumnCachingCursor(Cursor cursor, String actionCallColumn) {
            super(cursor);
            mIndices = new int[KEY_COUNT];
            Arrays.fill(mIndices, -1);
            String[] columns = cursor.getColumnNames();
            int count = columns.length;
            for (int i = 0; i < count; i++) {
                String col = columns[i];
                if (col == null) continue;
                Integer key = mKeys.get(col);
                if (key == null) {
                    if (col.equals(actionCallColumn)) {
                        mIndices[ACTION_MSG_CALL] = i;
                    }
                } else {
                    mIndices[key] = i;
                }
            }
        }

        /**
         * Gets a column by key.
         *
         * @param key One of the column keys declared in this class.
         * @return A string, or {@code null} if the column is not present.
         */
        public String getColumnString(int key) {
            int col = mIndices[key];
            if (col == -1) {
                return null;
            }
            return getString(col);
        }
    }

}
