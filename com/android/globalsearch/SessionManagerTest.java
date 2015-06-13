/*
 * Copyright (C) The Android Open Source Project
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

import junit.framework.TestCase;
import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.ComponentName;
import com.google.android.collect.Lists;

/**
 * Contains tests for logic in {@link SessionManager}
 *
 * TODO: refactor out hard coded 'promotableWhenInsufficientRankingInfo' list in session manager
 * to make these tests less brittle.
 */
public class SessionManagerTest extends TestCase {


    static final ComponentName WEB =
            new ComponentName("com.example","com.example.WEB");

    static final ComponentName B =
            new ComponentName("com.android.contacts","com.example.B");

    static final ComponentName C =
            new ComponentName("com.android.contacts","com.example.C");

    static final ComponentName D =
            new ComponentName("com.android.contacts","com.example.D");

    static final ComponentName E =
            new ComponentName("com.android.contacts","com.example.E");

    static final ComponentName F =
            new ComponentName("com.android.contacts","com.example.F");

    private SimpleSourceLookup mSourceLookup;

    private ArrayList<SuggestionSource> mAllSuggestionSources;


    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAllSuggestionSources = Lists.newArrayList(
                makeSource(B), makeSource(C), makeSource(D),
                makeSource(E), makeSource(F));

        mSourceLookup = new SimpleSourceLookup(mAllSuggestionSources, makeSource(WEB));
    }

    private SuggestionSource makeSource(ComponentName componentName) {
        return new TestSuggestionSource.Builder().setComponent(componentName).create();
    }

    public void testOrderSources_onlyIncludeEnabled() {
        SessionManager.Sources sources1 = SessionManager.orderSources(
                Lists.newArrayList(makeSource(B)),
                mSourceLookup,
                Lists.newArrayList(C, D, WEB), // ranking
                3);
        assertContentsInOrder(
                "should only include enabled source, even if the ranking includes more.",
                sources1.mPromotableSources,
                makeSource(WEB), makeSource(B));

        SessionManager.Sources sources2 = SessionManager.orderSources(
                Lists.newArrayList(makeSource(B)),
                mSourceLookup,
                Lists.newArrayList(C, B, WEB), // ranking
                3);

        assertContentsInOrder(
                "should only include enabled source, even if the ranking includes more.",
                sources2.mPromotableSources,
                makeSource(WEB), makeSource(B));
    }


    public void testOrderSources_webAlwaysFirst() {
        SessionManager.Sources sources = SessionManager.orderSources(
                mAllSuggestionSources,
                mSourceLookup,
                Lists.newArrayList(C, D, WEB), // ranking
                3);

        assertContentsInOrder(
                "web source should be first even if its stats are worse.",
                sources.mPromotableSources,
                // first the web
                makeSource(WEB),
                // then the rest of the ranked
                makeSource(C), makeSource(D),
                // then the rest
                makeSource(B), makeSource(E), makeSource(F));
    }

    public void testOrderSources_unRankedAfterPromoted() {
        SessionManager.Sources sources = SessionManager.orderSources(
                mAllSuggestionSources,
                mSourceLookup,
                Lists.newArrayList(C, D, WEB, B), // ranking
                3);
        assertContentsInOrder(
                "unranked sources should be ordered after the ranked sources in the promoted " +
                        "slots.",
                sources.mPromotableSources,
                // first the web
                makeSource(WEB),
                // then enough of the ranked to fill the remaining promoted slots
                makeSource(C), makeSource(D),
                // then the unranked
                makeSource(E), makeSource(F),
                // finally, the rest of the ranked
                makeSource(B));
    }

    static void assertContentsInOrder(Iterable<?> actual, Object... expected) {
        assertContentsInOrder(null, actual, expected);
    }

    /**
     * an implementation of {@link android.test.MoreAsserts#assertContentsInOrder}
     * that isn't busted.  a bug has been filed about that, but for now this works.
     */
    static void assertContentsInOrder(
            String message, Iterable<?> actual, Object... expected) {
        ArrayList actualList = new ArrayList();
        for (Object o : actual) {
            actualList.add(o);
        }
        Assert.assertEquals(message, Arrays.asList(expected), actualList);
    }
}
