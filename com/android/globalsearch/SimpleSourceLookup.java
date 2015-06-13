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
 * Simple mock implementation of SourceLookup.
 */
public class SimpleSourceLookup implements SourceLookup {
    private final ArrayList<SuggestionSource> mSources;
    private final SuggestionSource mWebSource;

    public SimpleSourceLookup(ArrayList<SuggestionSource> sources, SuggestionSource webSource) {
        mSources = sources;
        mWebSource = webSource;
    }

    public SuggestionSource getSourceByComponentName(ComponentName componentName) {
        for (SuggestionSource source : mSources) {
            if (componentName.equals(source.getComponentName())) {
                return source;
            }
        }
        return null;
    }

    public SuggestionSource getSelectedWebSearchSource() {
        return mWebSource;
    }

    public boolean isTrustedSource(SuggestionSource source) {
        final String packageName = source.getComponentName().getPackageName();
        return "com.android.contacts".equals(packageName)
                || "com.android.browser".equals(packageName)
                || "com.android.providers.applications".equals(packageName);
    }
}