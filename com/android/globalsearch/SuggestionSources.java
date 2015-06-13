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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.server.search.SearchableInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Maintains the list of all suggestion sources.
 */
public class SuggestionSources implements SourceLookup {

    // set to true to enable the more verbose debug logging for this file
    private static final boolean DBG = false;
    private static final String TAG = "SuggestionSources";

    // Name of the preferences file used to store suggestion source preferences
    public static final String PREFERENCES_NAME = "SuggestionSources";

    // The key for the preference that holds the selected web search source
    public static final String WEB_SEARCH_SOURCE_PREF = "web_search_source";
    
    // An override value for web search providers to provide more results than others.
    private static final int WEB_RESULTS_OVERRIDE_LIMIT = 20;

    private Context mContext;
    private SearchManager mSearchManager;
    private SharedPreferences mPreferences;
    private HashSet<String> mTrustedPackages;
    private boolean mLoaded;

    // All available suggestion sources.
    private SourceList mSuggestionSources;

    // The web search source to use. This is the source selected in the preferences,
    // or the default source if no source has been selected.
    private SuggestionSource mSelectedWebSearchSource;

    // All enabled suggestion sources. This does not include the web search source.
    private ArrayList<SuggestionSource> mEnabledSuggestionSources;
    
    // Updates the inclusion of the web search provider.
    private ShowWebSuggestionsSettingChangeObserver mShowWebSuggestionsSettingChangeObserver;

    /**
     *
     * @param context Used for looking up source information etc.
     */
    public SuggestionSources(Context context) {
        mContext = context;
        mSearchManager = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        mPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        mLoaded = false;
    }

    /**
     * Gets all suggestion sources. This does not include any web search sources.
     *
     * @return A list of suggestion sources, including sources that are not enabled.
     *         Callers must not modify the returned list.
     */
    public synchronized Collection<SuggestionSource> getSuggestionSources() {
        if (!mLoaded) {
            Log.w(TAG, "getSuggestionSources() called, but sources not loaded.");
            return Collections.<SuggestionSource>emptyList();
        }
        return mSuggestionSources.values();
    }

    /** {@inheritDoc} */
    public synchronized SuggestionSource getSourceByComponentName(ComponentName componentName) {
        SuggestionSource source = mSuggestionSources.get(componentName);
        
        // If the source was not found, back off to check the web source in case it's that.
        if (source == null) {
            if (mSelectedWebSearchSource != null &&
                    mSelectedWebSearchSource.getComponentName().equals(componentName)) {
                source = mSelectedWebSearchSource;
            }
        }
        return source;
    }

    /**
     * Gets all enabled suggestion sources.
     *
     * @return All enabled suggestion sources (does not include the web search source).
     *         Callers must not modify the returned list.
     */
    public synchronized List<SuggestionSource> getEnabledSuggestionSources() {
        if (!mLoaded) {
            Log.w(TAG, "getEnabledSuggestionSources() called, but sources not loaded.");
            return Collections.<SuggestionSource>emptyList();
        }
        return mEnabledSuggestionSources;
    }

    /**
     * Checks whether a suggestion source is enabled by default. For now, only trusted sources are.
     */
    public boolean isSourceDefaultEnabled(SuggestionSource source) {
        return isTrustedSource(source);
    }

    /** {@inheritDoc} */
    public synchronized SuggestionSource getSelectedWebSearchSource() {
        if (!mLoaded) {
            Log.w(TAG, "getSelectedWebSearchSource() called, but sources not loaded.");
            return null;
        }
        return mSelectedWebSearchSource;
    }

    /**
     * Gets the preference key of the preference for whether the given source
     * is enabled. The preference is stored in the {@link #PREFERENCES_NAME}
     * preferences file.
     */
    public String getSourceEnabledPreference(SuggestionSource source) {
        return "enable_source_" + source.getComponentName().flattenToString();
    }

    // Broadcast receiver for package change notifications
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED.equals(action)
                    || SearchManager.INTENT_ACTION_SEARCH_SETTINGS_CHANGED.equals(action)) {
                // TODO: Instead of rebuilding the whole list on every change,
                // just add, remove or update the application that has changed.
                // Adding and updating seem tricky, since I can't see an easy way to list the
                // launchable activities in a given package.
                updateSources();
            }
        }
    };

    /**
     * After calling, clients must call {@link #close()} when done with this object.
     */
    public synchronized void load() {
        if (mLoaded) {
            Log.w(TAG, "Already loaded, ignoring call to load().");
            return;
        }

        loadTrustedPackages();

        // Listen for searchables changes.
        mContext.registerReceiver(mBroadcastReceiver,
                new IntentFilter(SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED));

        // Listen for search preference changes.
        mContext.registerReceiver(mBroadcastReceiver,
                new IntentFilter(SearchManager.INTENT_ACTION_SEARCH_SETTINGS_CHANGED));
        
        mShowWebSuggestionsSettingChangeObserver = new ShowWebSuggestionsSettingChangeObserver();
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SHOW_WEB_SUGGESTIONS),
                true,
                mShowWebSuggestionsSettingChangeObserver);

        // update list of sources
        updateSources();
        mLoaded = true;
    }

    /**
     * Releases all resources used by this object. It is possible to call
     * {@link #load()} again after calling this method.
     */
    public synchronized void close() {
        if (!mLoaded) {
            Log.w(TAG, "Not loaded, ignoring call to close().");
            return;
        }
        mContext.unregisterReceiver(mBroadcastReceiver);
        mContext.getContentResolver().unregisterContentObserver(
                mShowWebSuggestionsSettingChangeObserver);

        mSuggestionSources = null;
        mSelectedWebSearchSource = null;
        mEnabledSuggestionSources = null;
        mLoaded = false;
    }

    private void loadTrustedPackages() {
        mTrustedPackages = new HashSet<String>();

        // Get the list of trusted packages from a resource, which allows vendor overlays.
        String[] trustedPackages = mContext.getResources().getStringArray(
                R.array.trusted_search_providers);
        
        if (trustedPackages == null) {
            Log.w(TAG, "Could not load list of trusted search providers, trusting none");
            return;
        }
        
        for (String trustedPackage : trustedPackages) {
            mTrustedPackages.add(trustedPackage);
        }
    }

    /**
     * Loads the list of suggestion sources. This method is package private so that
     * it can be called efficiently from inner classes.
     */
    /* package */ synchronized void updateSources() {
        mSuggestionSources = new SourceList();
        addExternalSources();

        mEnabledSuggestionSources = findEnabledSuggestionSources();
        mSelectedWebSearchSource = findWebSearchSource();
    }

    private void addExternalSources() {
        ArrayList<SuggestionSource> trusted = new ArrayList<SuggestionSource>();
        ArrayList<SuggestionSource> untrusted = new ArrayList<SuggestionSource>();
        for (SearchableInfo searchable : mSearchManager.getSearchablesInGlobalSearch()) {
            SuggestionSource source = new SearchableSuggestionSource(mContext, searchable);
            if (isTrustedSource(source)) {
                trusted.add(source);
            } else {
                untrusted.add(source);
            }
        }
        for (SuggestionSource s : trusted) {
            addSuggestionSource(s);
        }
        for (SuggestionSource s : untrusted) {
            addSuggestionSource(s);
        }
    }

    private void addSuggestionSource(SuggestionSource source) {
        if (DBG) Log.d(TAG, "Adding source: " + source);
        SuggestionSource old = mSuggestionSources.put(source);
        if (old != null) {
            Log.w(TAG, "Replaced source " + old + " for " + source.getComponentName());
        }
    }

    /**
     * Computes the list of enabled suggestion sources.
     */
    private ArrayList<SuggestionSource> findEnabledSuggestionSources() {
        ArrayList<SuggestionSource> enabledSources = new ArrayList<SuggestionSource>();
        for (SuggestionSource source : mSuggestionSources.values()) {
            if (isSourceEnabled(source)) {
                if (DBG) Log.d(TAG, "Adding enabled source " + source);
                enabledSources.add(source);
            }
        }
        return enabledSources;
    }

    private boolean isSourceEnabled(SuggestionSource source) {
        boolean defaultEnabled = isSourceDefaultEnabled(source);
        if (mPreferences == null) {
            Log.w(TAG, "Search preferences " + PREFERENCES_NAME + " not found.");
            return true;
        }
        String sourceEnabledPref = getSourceEnabledPreference(source);
        return mPreferences.getBoolean(sourceEnabledPref, defaultEnabled);
    }

    public boolean isTrustedSource(SuggestionSource source) {
        if (source == null) return false;
        final String packageName = source.getComponentName().getPackageName();
        return mTrustedPackages != null && mTrustedPackages.contains(packageName);
    }

    /**
     * Finds the selected web search source.
     */
    private SuggestionSource findWebSearchSource() {
        SuggestionSource webSearchSource = null;
        if (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SHOW_WEB_SUGGESTIONS,
                1 /* default on until user actually changes it */) == 1) {
            SearchableInfo webSearchable = mSearchManager.getDefaultSearchableForWebSearch();
            if (webSearchable != null) {
                if (DBG) Log.d(TAG, "Adding web source " + webSearchable.getSearchActivity());
                // Construct a SearchableSuggestionSource around the web search source. Allow
                // the web search source to provide a larger number of results with
                // WEB_RESULTS_OVERRIDE_LIMIT.
                webSearchSource = SearchableSuggestionSource.create(
                        mContext, webSearchable.getSearchActivity(), WEB_RESULTS_OVERRIDE_LIMIT);
            }
        }
        return webSearchSource;
    }

    /**
     * This works like a map from ComponentName to SuggestionSource,
     * but supports a zero-allocation method for listing all the sources.
     */
    private static class SourceList {

        private HashMap<ComponentName,SuggestionSource> mSourcesByComponent;
        private ArrayList<SuggestionSource> mSources;

        public SourceList() {
            mSourcesByComponent = new HashMap<ComponentName,SuggestionSource>();
            mSources = new ArrayList<SuggestionSource>();
        }

        public SuggestionSource get(ComponentName componentName) {
            return mSourcesByComponent.get(componentName);
        }

        /**
         * Adds a source. Replaces any previous source with the same component name.
         *
         * @return The previous source that was replaced, if any.
         */
        public SuggestionSource put(SuggestionSource source) {
            if (source == null) {
                return null;
            }
            SuggestionSource old = mSourcesByComponent.put(source.getComponentName(), source);
            if (old != null) {
                // linear search is ok here, since addSource() is only called when the
                // list of sources is updated, which is infrequent. Also, collisions would only
                // happen if there are two sources with the same component name, which should
                // only happen as long as we have hard-coded sources.
                mSources.remove(old);
            }
            mSources.add(source);
            return old;
        }

        /**
         * Gets the suggestion sources.
         */
        public ArrayList<SuggestionSource> values() {
            return mSources;
        }

        /**
         * Checks whether the list is empty.
         */
        public boolean isEmpty() {
            return mSources.isEmpty();
        }
    }
    
    /**
     * ContentObserver which updates the list of enabled sources to include or exclude
     * the web search provider depending on the state of the
     * {@link Settings.System#SHOW_WEB_SUGGESTIONS} setting.
     */
    private class ShowWebSuggestionsSettingChangeObserver extends ContentObserver {
        public ShowWebSuggestionsSettingChangeObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSources();
        }
    }
}
