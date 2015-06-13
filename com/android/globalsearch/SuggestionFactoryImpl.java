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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.TypedValue;
import android.app.SearchManager;
import android.text.TextUtils;
import android.text.util.Regex;

import java.util.List;

import static com.android.globalsearch.SourceSuggestionBacker.SourceStat;

/**
 * Implements {@link SuggestionFactory}.
 */
public class SuggestionFactoryImpl implements SuggestionFactory {

    private static final ComponentName BUILTIN_SOURCE_COMPONENT
            = new ComponentName("com.android.globalsearch",
                    "com.android.globalsearch.GlobalSearch");

    private final Context mContext;

    // The ID of the ColorStateList to be applied to urls of website suggestions, as derived from
    // the current theme. This is not set until/unless applySearchUrlColor is called, at which point
    // this variable caches the color value.
    private static String mSearchUrlColorId;

    // The background color of the 'more' item and other corpus items.
    private int mCorpusItemBackgroundColor;

    /**
     * @param context The context.
     */
    public SuggestionFactoryImpl(Context context) {
        mContext = context;
        TypedValue colorValue = new TypedValue();
        mContext.getTheme().resolveAttribute(
                        com.android.internal.R.attr.searchWidgetCorpusItemBackground,
                        colorValue, true);
        mCorpusItemBackgroundColor = mContext.getResources().getColor(colorValue.resourceId);
    }

    /** {@inheritDoc} */
    public SuggestionData getMoreEntry(
            boolean expanded, List<SourceSuggestionBacker.SourceStat> sourceStats) {
        StringBuilder desc = new StringBuilder();
        String appSeparator = mContext.getString(R.string.result_count_app_separator);
        String countSeparator = mContext.getString(R.string.result_count_count_separator);
        int sourceCount = sourceStats.size();

        // TODO: In the code below where we append an extra space after the appSeparator,
        // ideally we'd like to encode this space in the localized
        // string instead. However trailing whitespace is trimmed from strings in strings.xml,
        // so this doesn't work. Figure out how to make that respect whitespace.
        boolean anyPending = false;
        for (int i = 0; i < sourceCount; i++) {
            SourceSuggestionBacker.SourceStat sourceStat = sourceStats.get(i);
            if (sourceStat.getResponseStatus() != SourceStat.RESPONSE_FINISHED) {
                anyPending = true;
            }
            int suggestionCount = sourceStat.getNumResults();
            if (suggestionCount > 0) {
                if (desc.length() > 0) {
                    desc.append(appSeparator);
                }
                desc.append(sourceStat.getLabel()).append(countSeparator)
                        .append(getCountString(suggestionCount, sourceStat.getQueryLimit()));

            }
        }
        int icon = expanded ? R.drawable.more_results_expanded : R.drawable.more_results;

        final SuggestionData.Builder builder = new SuggestionData.Builder(BUILTIN_SOURCE_COMPONENT)
                .format("html")
                .title("<i>" + mContext.getString(R.string.more_results) + "</i>")
                .description("<i>" + desc.toString() + "</i>")
                .icon1(icon)
                .shortcutId(SearchManager.SUGGEST_NEVER_MAKE_SHORTCUT)
                .backgroundColor(mCorpusItemBackgroundColor)
                .intentAction(SearchManager.INTENT_ACTION_NONE);  // no intent launched for this
        if (anyPending) {
            builder.icon2(com.android.internal.R.drawable.search_spinner);
        }
        return builder.build();
    }

    /**
     * Return a rounded representation of a suggestion count if, e.g. "10+".
     *
     * @param count The number of items.
     * @param limit The maximum number that was requested.
     * @return If the {@code count} is exact, the value of {@code count} is returned.
     *         Otherwise, a rounded valued followed by "+" is returned.
     */
    private String getCountString(int count, int limit) {
        if (limit == 0 || count < limit) {
            return String.valueOf(count);
        } else {
            if (limit > 10) {
                // round to nearest lower multiple of 10
                count = 10 * ((limit - 1) / 10);
            }
            return count + "+";
        }
    }

    /** {@inheritDoc} */
    public SuggestionData getCorpusEntry(
            String query, SourceSuggestionBacker.SourceStat sourceStat) {
        int suggestionCount = sourceStat.getNumResults();
        final SuggestionData.Builder builder = new SuggestionData.Builder(sourceStat.getName())
                .title(sourceStat.getLabel())
                .shortcutId(SearchManager.SUGGEST_NEVER_MAKE_SHORTCUT)
                .icon1(sourceStat.getIcon())
                .intentAction(SearchManager.INTENT_ACTION_CHANGE_SEARCH_SOURCE)
                .intentData(sourceStat.getName().flattenToString())
                .backgroundColor(mCorpusItemBackgroundColor)
                .intentQuery(query);

        final int responseStatus = sourceStat.getResponseStatus();

        if (responseStatus == SourceStat.RESPONSE_FINISHED) {
            final Resources resources = mContext.getResources();
            final String description = sourceStat.isShowingPromotedResults() ?
                    resources.getQuantityString(
                            R.plurals.additional_result_count, suggestionCount, suggestionCount) :
                    resources.getQuantityString(
                            R.plurals.total_result_count, suggestionCount, suggestionCount);
            builder.description(description);
        }

        if (responseStatus == SourceStat.RESPONSE_IN_PROGRESS) {
            builder.icon2(com.android.internal.R.drawable.search_spinner);
        }

        return builder.build();
    }

    /**
     * Creates a one-off suggestion for searching the web with the current query.
     * The description can be a format string with one string value, which will be
     * filled in by the provided query argument.
     *
     * @param query The query
     */
    public SuggestionData createSearchTheWebSuggestion(String query) {
        if (TextUtils.isEmpty(query)) {
            return null;
        }
        String suggestion = mContext.getString(R.string.search_the_web, query);
        int ix = suggestion.indexOf('\n');

        return new SuggestionData.Builder(BUILTIN_SOURCE_COMPONENT)
                .title(suggestion.substring(0, ix))
                .description(suggestion.substring(ix + 1))
                .icon1(R.drawable.magnifying_glass)
                .intentAction(Intent.ACTION_WEB_SEARCH)
                .intentQuery(query)
                .shortcutId(SearchManager.SUGGEST_NEVER_MAKE_SHORTCUT)
                .build();
    }

    /**
     * Creates a one-off suggestion for visiting the url specified by the current query,
     * or null if the current query does not look like a url.
     *
     * @param query The query
     */
    public SuggestionData createGoToWebsiteSuggestion(String query) {
        if (!Regex.WEB_URL_PATTERN.matcher(query).matches()) {
            return null;
        }

        String url = query.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return new SuggestionData.Builder(BUILTIN_SOURCE_COMPONENT)
                .format("html")
                .title(mContext.getString(R.string.go_to_website_title))
                .description(applySearchUrlColor(query))
                .icon1(R.drawable.globe)
                .intentAction(Intent.ACTION_VIEW)
                .intentData(url)
                .build();
    }

    /**
     * Wraps the provided url string in the appropriate html formatting to apply the
     * theme-based search url color.
     */
    private String applySearchUrlColor(String url) {
        if (mSearchUrlColorId == null) {
            // Get the color used for this purpose from the current theme.
            TypedValue colorValue = new TypedValue();
            mContext.getTheme().resolveAttribute(
                    com.android.internal.R.attr.textColorSearchUrl, colorValue, true);
            mSearchUrlColorId = Integer.toString(colorValue.resourceId);
        }

        return "<font color=\"@" + mSearchUrlColorId + "\">" + url + "</font>";
    }

}
