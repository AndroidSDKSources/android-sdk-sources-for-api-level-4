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

/**
 * Holds data for each suggest item including the display data and how to launch the result.
 * Used for passing from the provider to the suggest cursor.
 * Use {@link Builder} to create new instances.
 */
public final class SuggestionData {
    private final ComponentName mSource;
    private final String mFormat;
    private final String mTitle;
    private final String mDescription;
    private final String mIcon1;
    private final String mIcon2;
    private final String mIntentAction;
    private final String mIntentData;
    private final String mIntentQuery;
    private final String mActionMsgCall;
    private final String mIntentExtraData;
    private final String mIntentComponentName;
    private final String mShortcutId;
    private final int mBackgroundColor;
    private final boolean mPinToBottom;
    private final boolean mSpinnerWhileRefreshing;

    SuggestionData(
            ComponentName source,
            String format,
            String title,
            String description,
            String icon1,
            String icon2,
            String intentAction,
            String intentData,
            String intentQuery,
            String actionMsgCall,
            String intentExtraData,
            String intentComponentName,
            String shortcutId,
            int backgroundColor,
            boolean pinToBottom,
            boolean spinnerWhileRefreshing) {
        mSource = source;
        mFormat = format;
        mTitle = title;
        mDescription = description;
        mIcon1 = icon1;
        mIcon2 = icon2;
        mIntentAction = intentAction;
        mIntentData = intentData;
        mIntentQuery = intentQuery;
        mActionMsgCall = actionMsgCall;
        mIntentExtraData = intentExtraData;
        mIntentComponentName = intentComponentName;
        mShortcutId = shortcutId;
        mBackgroundColor = backgroundColor;
        mPinToBottom = pinToBottom;
        mSpinnerWhileRefreshing = spinnerWhileRefreshing;
    }

    /**
     * Gets the component name of the suggestion source that created this suggestion.
     */
    public ComponentName getSource() {
        return mSource;
    }

    /**
     * Gets the format of the text in the title and description.
     */
    public String getFormat() {
        return mFormat;
    }

    /**
     * Gets the display title (typically shown as the first line).
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Gets the display description (typically shown as the second line).
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Resource ID or URI for the first icon (typically shown on the left).
     */
    public String getIcon1() {
        return mIcon1;
    }

    /**
     * Resource ID or URI for the second icon (typically shown on the right).
     */
    public String getIcon2() {
        return mIcon2;
    }

    /**
     * The intent action to launch.
     */
    public String getIntentAction() {
        return mIntentAction;
    }

    /**
     * The intent data to use.
     */
    public String getIntentData() {
        return mIntentData;
    }

    /**
     * The suggested query.
     */
    public String getIntentQuery() {
        return mIntentQuery;
    }

    /**
     * The action key message for the CALL key.
     */
    public String getActionMsgCall() {
        return mActionMsgCall;
    }

    /**
     * The intent extra data.
     */
    public String getIntentExtraData() {
        return mIntentExtraData;
    }

    /**
     * The intent component name.
     */
    public String getIntentComponentName() {
        return mIntentComponentName;
    }

    /**
     * The shortcut id.
     */
    public String getShortcutId() {
        return mShortcutId;
    }
    
    /**
     * The background color.
     */
    public int getBackgroundColor() {
        return mBackgroundColor;
    }
    
    /**
     * Whether this suggestion should be pinned to the very bottom of the suggestion list.
     */
    public boolean isPinToBottom() {
        return mPinToBottom;
    }
    
    /**
     * Whether this suggestion should show a spinner while refreshing.
     */
    public boolean isSpinnerWhileRefreshing() {
        return mSpinnerWhileRefreshing;
    }

    /**
     * Gets a builder initialized with the values from this suggestion.
     */
    public Builder buildUpon() {
        return new Builder(mSource)
                .format(mFormat)
                .title(mTitle)
                .description(mDescription)
                .icon1(mIcon1)
                .icon2(mIcon2)
                .intentAction(mIntentAction)
                .intentData(mIntentData)
                .intentQuery(mIntentQuery)
                .actionMsgCall(mActionMsgCall)
                .intentExtraData(mIntentExtraData)
                .intentComponentName(mIntentComponentName)
                .shortcutId(mShortcutId)
                .backgroundColor(mBackgroundColor)
                .pinToBottom(mPinToBottom)
                .spinnerWhileRefreshing(mSpinnerWhileRefreshing);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SuggestionData that = (SuggestionData) o;

        if (!mSource.equals(that.mSource)) return false;
        if (notEqual(mFormat, that.mFormat)) return false;
        if (notEqual(mTitle, that.mTitle)) return false;
        if (notEqual(mDescription, that.mDescription)) return false;
        if (notEqual(mIcon1, that.mIcon1)) return false;
        if (notEqual(mIcon2, that.mIcon2)) return false;
        if (notEqual(mIntentAction, that.mIntentAction)) return false;
        if (notEqual(mIntentData, that.mIntentData)) return false;
        if (notEqual(mIntentQuery, that.mIntentQuery)) return false;
        if (notEqual(mActionMsgCall, that.mActionMsgCall)) return false;
        if (notEqual(mIntentExtraData, that.mIntentExtraData)) return false;
        if (notEqual(mIntentComponentName, that.mIntentComponentName)) return false;
        if (notEqual(mShortcutId, that.mShortcutId)) return false;
        if (mBackgroundColor != that.mBackgroundColor) return false;
        if (mPinToBottom != that.mPinToBottom) return false;
        if (mSpinnerWhileRefreshing != that.mSpinnerWhileRefreshing) return false;
        return true;
    }

    private static boolean notEqual(String x, String y) {
        return x != null ? !x.equals(y) : y != null;
    }

    @Override
    public int hashCode() {
        int result = mSource.hashCode();
        result = addHashCode(result, mFormat);
        result = addHashCode(result, mTitle);
        result = addHashCode(result, mDescription);
        result = addHashCode(result, mIcon1);
        result = addHashCode(result, mIcon2);
        result = addHashCode(result, mIntentAction);
        result = addHashCode(result, mIntentData);
        result = addHashCode(result, mIntentQuery);
        result = addHashCode(result, mActionMsgCall);
        result = addHashCode(result, mIntentExtraData);
        result = addHashCode(result, mIntentComponentName);
        result = addHashCode(result, mShortcutId);
        result = addHashCode(result, Integer.toString(mBackgroundColor));
        result = addHashCode(result, String.valueOf(mPinToBottom));
        result = addHashCode(result, String.valueOf(mSpinnerWhileRefreshing));
        return result;
    }

    private static int addHashCode(int old, String str) {
        return 31 * old + (str != null ? str.hashCode() : 0);
    }

    /**
     * Returns a string representation of the contents of this SuggestionData,
     * for debugging purposes.
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("SuggestionData(");
        builder.append("source=").append(mSource.toShortString())
                .append(", title=").append(mTitle);
        if (mIntentAction != null) {
            builder.append(", intentAction=").append(mIntentAction)
                .append(", intentData=").append(mIntentData);
        }

        if (mIntentQuery != null && mIntentQuery.length() > 0) {
            builder.append(", intent query=").append(mIntentQuery);
        }
        if (mShortcutId != null) {
            builder.append(", shortcutid=").append(mShortcutId);
        }
        if (mPinToBottom) {
            builder.append(", pin to bottom=true");
        }
        if (mSpinnerWhileRefreshing) {
            builder.append(", spinner while refreshing=true");
        }

        builder.append(")");
        return builder.toString();
    }

    /**
     * Builder for {@link SuggestionData}.
     */
    public static class Builder {
        private ComponentName mSource;
        private String mFormat;
        private String mTitle;
        private String mDescription;
        private String mIcon1;
        private String mIcon2;
        private String mIntentAction;
        private String mIntentData;
        private String mIntentQuery;
        private String mActionMsgCall;
        private String mIntentExtraData;
        private String mIntentComponentName;
        private String mShortcutId;
        private int mBackgroundColor;
        private boolean mPinToBottom;
        private boolean mSpinnerWhileRefreshing;

        /**
         * Creates a new suggestion builder.
         *
         * @param source The component name of the suggestion source that this suggestion
         *  comes from.
         */
        public Builder(ComponentName source) {
            mSource = source;
            mBackgroundColor = 0;
        }

        /**
         * Builds a suggestion using the values set in the builder.
         */
        public SuggestionData build() {
            return new SuggestionData(
                    mSource,
                    mFormat,
                    mTitle,
                    mDescription,
                    mIcon1,
                    mIcon2,
                    mIntentAction,
                    mIntentData,
                    mIntentQuery,
                    mActionMsgCall,
                    mIntentExtraData,
                    mIntentComponentName,
                    mShortcutId,
                    mBackgroundColor,
                    mPinToBottom,
                    mSpinnerWhileRefreshing);
        }

        /**
         * Sets the format of the text in the title and description.
         */
        public Builder format(String format) {
            mFormat = format;
            return this;
        }

        /**
         * Sets the display title (typically shown as the first line).
         */
        public Builder title(String title) {
            mTitle = title;
            return this;
        }

        /**
         * Sets the display description (typically shown as the second line).
         */
        public Builder description(String description) {
            mDescription = description;
            return this;
        }

        /**
         * Sets the resource ID or URI for the first icon (typically shown on the left).
         */
        public Builder icon1(String icon1) {
            mIcon1 = icon1;
            return this;
        }

        /**
         * Sets the resource ID for the first icon (typically shown on the left).
         */
        public Builder icon1(int icon1) {
            return icon1(String.valueOf(icon1));
        }

        /**
         * Sets the resource ID or URI for the second icon (typically shown on the right).
         */
        public Builder icon2(String icon2) {
            mIcon2 = icon2;
            return this;
        }

        /**
         * Sets the resource ID for the second icon (typically shown on the right).
         */
        public Builder icon2(int icon2) {
            return icon2(String.valueOf(icon2));
        }

        /**
         * Sets the intent action to launch.
         */
        public Builder intentAction(String intentAction) {
            mIntentAction = intentAction;
            return this;
        }

        /**
         * Sets the intent data to use.
         */
        public Builder intentData(String intentData) {
            mIntentData = intentData;
            return this;
        }

        /**
         * Sets the suggested query.
         */
        public Builder intentQuery(String intentQuery) {
            mIntentQuery = intentQuery;
            return this;
        }

        /**
         * Sets the action message for the CALL key.
         */
        public Builder actionMsgCall(String actionMsgCall) {
            mActionMsgCall = actionMsgCall;
            return this;
        }

        /**
         * Sets the intent extra data.
         */
        public Builder intentExtraData(String intentExtraData) {
            mIntentExtraData = intentExtraData;
            return this;
        }

        /**
         * Sets the intent component name.
         */
        public Builder intentComponentName(String intentComponentName) {
            mIntentComponentName = intentComponentName;
            return this;
        }

        /**
         * Sets the shortcut id.
         */
        public Builder shortcutId(String shortcutId) {
            mShortcutId = shortcutId;
            return this;
        }
        
        /**
         * Sets the background color.
         */
        public Builder backgroundColor(int backgroundColor) {
            mBackgroundColor = backgroundColor;
            return this;
        }
        
        /**
         * Sets whether to pin this suggestion to the very bottom of the suggestion list.
         */
        public Builder pinToBottom(boolean pinToBottom) {
            mPinToBottom = pinToBottom;
            return this;
        }
        
        /**
         * Sets whether this suggestion should show a spinner while refreshing.
         */
        public Builder spinnerWhileRefreshing(boolean spinnerWhileRefreshing) {
            mSpinnerWhileRefreshing = spinnerWhileRefreshing;
            return this;
        }
    }
}
