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
import android.content.ComponentName;
import android.test.MoreAsserts;
import com.google.android.collect.Sets;
import com.google.android.collect.Lists;

import static com.android.globalsearch.SourceSuggestionBacker.SourceStat;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Arrays;

/**
 * Tests {@link SourceSuggestionBacker}
 *
 * see the {@link #setUp()} method for the default setup of the backer that is used by most
 * test cases.
 */
public class SourceSuggestionBackerTest extends TestCase
        implements SourceSuggestionBacker.MoreExpanderFactory,
        SourceSuggestionBacker.CorpusResultFactory {

    private ComponentName mName1;
    private ComponentName mName2;
    private ComponentName mName3;
    private TestSuggestionSource mSource1;
    private TestSuggestionSource mSource2;
    private TestSuggestionSource mSource3;
    private TestBacker mBacker;
    private static final int MAX_PROMOTED_SHOWING = 6;
    private static final long NOW = 700L;
    private static final long DEADLINE = 2000L;
    private SuggestionData mShortcut1;
    private SuggestionData mGoToWebsite;
    private SuggestionData mSearchTheWeb;
    private SuggestionData mMoreNotExpanded;
    private SuggestionData mMoreExpanded;
    private static final String SOURCE1_LABEL = "source1 label";
    private static final String SOURCE2_LABEL = "source2 label";
    private static final String SOURCE3_LABEL = "source3 label";


    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mName1 = new ComponentName(
                "com.android.globalsearch", "com.android.globalsearch.One");

        mName2 = new ComponentName(
                "com.android.globalsearch", "com.android.globalsearch.Two");

        mName3 = new ComponentName(
                "com.android.globalsearch", "com.android.globalsearch.Three");

        mSource1 = new TestSuggestionSource.Builder()
                .setComponent(mName1)
                .setLabel(SOURCE1_LABEL)
                .create();

        mSource2 = new TestSuggestionSource.Builder()
                .setComponent(mName2)
                .setLabel(SOURCE2_LABEL)
                .create();

        mSource3 = new TestSuggestionSource.Builder()
                .setComponent(mName3)
                .setLabel(SOURCE3_LABEL)
                .create();

        mShortcut1 = new SuggestionData.Builder(mName1)
                .title("shortcut")
                .description("description")
                .shortcutId("shortcutid")
                .intentAction("action.VIEW")
                .intentData("shorctut1")
                .build();

        // Normally we pass null in for this value; we only use this value to
        // explicitly test the "go to website" functionality.
        mGoToWebsite = new SuggestionData.Builder(mName1)
                .title("go to website")
                .description("google.com")
                .build();

        mSearchTheWeb = new SuggestionData.Builder(mName1)
                .title("search the web for 'yo'")
                .description("description")
                .build();

        mBacker = new TestBacker(
                "query",
                Lists.newArrayList(mShortcut1),
                Lists.<SuggestionSource>newArrayList(mSource1, mSource2, mSource3),
                Sets.newHashSet(mName1, mName2), // promoted sources
                mSource1,             // source1 is the web souce
                Lists.<SuggestionResult>newArrayList(),
                null,                 // no "go to website" suggestion
                mSearchTheWeb,        // the "search the web" suggestion
                MAX_PROMOTED_SHOWING,
                DEADLINE,
                this,        /** more factory points to {@link #getMoreEntry} */
                this);       /** corpus factory points to {@link #getCorpusEntry} */

        mMoreNotExpanded = new SuggestionData.Builder(mName1)
                .title("more unexpanded")
                .build();

        mMoreExpanded = new SuggestionData.Builder(mName1)
                .title("more expanded")
                .build();

        mBacker.setNow(NOW);
    }

    /** {@inheritDoc} */
    public SuggestionData getMoreEntry(
            boolean expanded,
            List<SourceSuggestionBacker.SourceStat> sourceStats) {
        return expanded ? mMoreExpanded : mMoreNotExpanded;
    }

    /** {@inheritDoc} */
    public SuggestionData getCorpusEntry(
            String query, SourceSuggestionBacker.SourceStat sourceStat) {
        return makeCorpusEntry(
                sourceStat.getLabel(),
                sourceStat.getResponseStatus(),
                sourceStat.getNumResults());
    }


    public void testNoResultsReported() {

        assertContentsInOrder(
                "should only be shortcuts before deadline.",
                getSnapshotFromBacker(false),
                mShortcut1);

        assertContentsInOrder(
                "should only be shortcuts before deadline.",
                getSnapshotFromBacker(true),
                mShortcut1);

        mBacker.setNow(NOW + DEADLINE);

        assertContentsInOrder(
                "after deadline should see 'search the web' and 'more' entry.",
                getSnapshotFromBacker(false),
                mShortcut1,
                mSearchTheWeb,
                mMoreNotExpanded);

        assertContentsInOrder(
                "after deadline (expanded) should see 'search the web' and 'more' entries.",
                getSnapshotFromBacker(true),
                mShortcut1,
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE1_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0),
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0),
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0));
    }

    public void testSomeResultsReported() {

        // source 1 reports back 4 results
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2),
                        makeSourceResult(mName1, 3)
                )));

        assertContentsInOrder(
                "before deadline, should show shortcuts and chunks of promoted sources.",
                getSnapshotFromBacker(false),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1));

        mBacker.setNow(NOW + DEADLINE);

        // source 3 is in progress
        mBacker.reportSourceStarted(mName3);

        assertContentsInOrder(
                "after deadline(expanded), should show shortcuts, chunks of promoted sources, " +
                        "rest of promoted slots filled, and more.",
                getSnapshotFromBacker(true),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName1, 2),
                makeSourceResult(mName1, 3),
                mSearchTheWeb,
                mMoreExpanded,
                // no "more" result for source 1 since we've displayed all of its entries now
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0),
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_IN_PROGRESS, 0));
    }

    public void testPromotedSourceRespondsAfterDeadline() {
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1))));

        mBacker.setNow(NOW + DEADLINE);

        assertContentsInOrder(
                "after deadline pending promoted source should be under 'more'.",
                getSnapshotFromBacker(true),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                mSearchTheWeb,
                mMoreExpanded,
                // source 2 hasn't responded yet
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0),
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0));

        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1))));

        assertContentsInOrder(
                "after deadline late reporting promoted source should be under 'more'.",
                getSnapshotFromBacker(true),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_FINISHED, 2),
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0));
    }

    public void testZeroReportingSources() {

        assertFalse("reporting zero results before ever being shown should not require updating.",
                mBacker.addSourceResults(new SuggestionResult(mSource1)));

        assertContentsInOrder(
                "zero reporting before deadline.",
                getSnapshotFromBacker(true),
                mShortcut1);

        mBacker.setNow(NOW + DEADLINE);

        assertContentsInOrder(
                "zero reporting before deadline, viewing after.",
                getSnapshotFromBacker(true),
                mShortcut1,
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0),
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0));

        // source 2 reports after deadline
        assertTrue("reporting zero results after being shown should require updating.",
                mBacker.addSourceResults(new SuggestionResult(mSource2)));

        assertContentsInOrder(
                "zero reporting after deadline.",
                getSnapshotFromBacker(true),
                mShortcut1,
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_FINISHED, 0),
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0));

        mBacker.addSourceResults(
                new SuggestionResult(mSource3, Lists.newArrayList(
                        makeSourceResult(mName3, 0),
                        makeSourceResult(mName3, 1))));

        assertContentsInOrder(
                "last source reported after deadline.",
                getSnapshotFromBacker(true),
                mShortcut1,
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_FINISHED, 0),
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_FINISHED, 2));
    }

    public void testSourceReportsAfterDeadlineWithResults() {
        mBacker = new TestBacker(
                "query",
                Lists.<SuggestionData>newArrayList(),  // no shortcuts
                Lists.<SuggestionSource>newArrayList(mSource1, mSource2, mSource3),
                Sets.newHashSet(mName1, mName2, mName3), // all 3 are promoted sources
                mSource1,
                Lists.<SuggestionResult>newArrayList(),
                null,
                mSearchTheWeb,
                MAX_PROMOTED_SHOWING,
                DEADLINE,
                this,
                this);

        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2)
                        )));

        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1),
                        makeSourceResult(mName2, 2)
                        )));

        mBacker.setNow(NOW + DEADLINE);

        assertContentsInOrder(
                "after deadline passes, we mix in the remaining promoted slots among the " +
                        "promoted  sources that have responded.",
                getSnapshotFromBacker(true),
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                makeSourceResult(mName1, 2),  // remaining space (now that deadline has passed)
                makeSourceResult(mName2, 2),
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0)
        );

        mBacker.addSourceResults(
                new SuggestionResult(mSource3, Lists.newArrayList(
                        makeSourceResult(mName3, 0),
                        makeSourceResult(mName3, 1),
                        makeSourceResult(mName3, 2)
                        )));

        assertContentsInOrder(
                "promoted source reported after deadline, results should remain stable (even " +
                        "though this is not the optimal order if we had known the third promoted " +
                        "source was going to miss the deadline).",
                getSnapshotFromBacker(true),
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                makeSourceResult(mName1, 2),
                makeSourceResult(mName2, 2),
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_FINISHED, 3)
        );
    }

    public void testFillSpaceLargerThanChunkSizeAfterDeadline() {
        mBacker = new TestBacker(
                "query",
                Lists.<SuggestionData>newArrayList(),  // no shortcuts
                Lists.<SuggestionSource>newArrayList(mSource1, mSource2, mSource3),
                Sets.newHashSet(mName1, mName2, mName3), // all 3 are promoted sources
                mSource1,
                Lists.<SuggestionResult>newArrayList(),
                null,
                mSearchTheWeb,
                MAX_PROMOTED_SHOWING,
                DEADLINE,
                this,
                this);

        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2),
                        makeSourceResult(mName1, 3),
                        makeSourceResult(mName1, 4),
                        makeSourceResult(mName1, 5)
                        )));

        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0)
                        )));

        mBacker.setNow(NOW + DEADLINE);

        mBacker.addSourceResults(
                new SuggestionResult(mSource3, Lists.newArrayList(
                        makeSourceResult(mName3, 0),
                        makeSourceResult(mName3, 1),
                        makeSourceResult(mName3, 2),
                        makeSourceResult(mName3, 3),
                        makeSourceResult(mName3, 4),
                        makeSourceResult(mName3, 5)
                        )));

        assertContentsInOrder(
                "after deadline has passed, promoted sources that have reported should fill in " +
                        "the remaining slots",
                getSnapshotFromBacker(true),
                // chunk 1
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                // chunk 2
                makeSourceResult(mName2, 0),
                // remaining space
                makeSourceResult(mName1, 2),
                makeSourceResult(mName1, 3),
                makeSourceResult(mName1, 4),
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE1_LABEL, SourceStat.RESPONSE_FINISHED, 1),   // 1 remaining result
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_FINISHED, 6));  // reported after deadline
    }

    public void testAllResultsReported() {

        // each one reports 4 results
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2),
                        makeSourceResult(mName1, 3)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1),
                        makeSourceResult(mName2, 2),
                        makeSourceResult(mName2, 3)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource3, Lists.newArrayList(
                        makeSourceResult(mName3, 0),
                        makeSourceResult(mName3, 1),
                        makeSourceResult(mName3, 2),
                        makeSourceResult(mName3, 3)
                )));

        assertContentsInOrder(
                "all responded.",
                getSnapshotFromBacker(false),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                makeSourceResult(mName1, 2),  // remaining slots (source 3 is not promoted)
                mSearchTheWeb,
                mMoreNotExpanded);

        assertContentsInOrder(
                "all responded (expanded).",
                getSnapshotFromBacker(true),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                makeSourceResult(mName1, 2),
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE1_LABEL, SourceStat.RESPONSE_FINISHED, 1),  // 1 remaining
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_FINISHED, 2),  // 2 remaining
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_FINISHED, 4));
    }

    public void testNoWebSource() {
        mBacker = new TestBacker(
                "query",
                Lists.newArrayList(mShortcut1),
                Lists.<SuggestionSource>newArrayList(mSource1, mSource2, mSource3),
                Sets.newHashSet(mName1, mName2), // promoted sources
                null,             // NO WEB SOURCE
                Lists.<SuggestionResult>newArrayList(),
                null,                 // no "go to website" suggestion
                mSearchTheWeb,        // the "search the web" suggestion
                MAX_PROMOTED_SHOWING,
                DEADLINE,
                this,        /** more factory points to {@link #getMoreEntry} */
                this);       /** corpus factory points to {@link #getCorpusEntry} */


        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2),
                        makeSourceResult(mName1, 3)
                )));

        assertContentsInOrder(
                "expecting business as usual even though there is no web source enabled.",
                getSnapshotFromBacker(false),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1));
    }

    public void testDuplicatesOfShortcut() {

        // four results from source 1, the first of which is a dupe of the shortcut
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        mShortcut1,
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2),
                        makeSourceResult(mName1, 3)
                )));

        // before deadline, not all have reported, so just one chunk from source 1
        assertContentsInOrder(
                "expecting duplicate to be removed.",
                getSnapshotFromBacker(false),
                mShortcut1,
                makeSourceResult(mName1, 1),
                makeSourceResult(mName1, 2));
    }

    public void testDuplicatesBetweenSources() {
        // two different suggestions for facebook with the same intent action and intent data
        final SuggestionData facebook1 = new SuggestionData.Builder(mName1)
                .title("facebook")
                .intentAction("action.VIEW")
                .intentData("http://www.facebook.com")
                .build();
        final SuggestionData facebook2 = new SuggestionData.Builder(mName2)
                .title("facebook home")
                .intentAction("action.VIEW")
                .intentData("http://www.facebook.com")
                .build();

        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        facebook1
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        facebook2
                )));

        assertContentsInOrder(
                "expecting duplicate to be removed.",
                getSnapshotFromBacker(false),
                mShortcut1,
                facebook1,
                mSearchTheWeb,
                mMoreNotExpanded);
    }

    /**
     * When there are duplicates removed, we still want to fill the valuable slots with more
     * information.
     */
    public void testDuplicatesRemovedSlotsStillFilled() {
        // two different suggestions for facebook with the same intent action and intent data
        final SuggestionData facebook1 = new SuggestionData.Builder(mName1)
                .title("facebook")
                .intentAction("action.VIEW")
                .intentData("http://www.facebook.com")
                .build();
        final SuggestionData facebook2 = new SuggestionData.Builder(mName2)
                .title("facebook home")
                .intentAction("action.VIEW")
                .intentData("http://www.facebook.com")
                .build();

        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        facebook1,
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        facebook2,
                        makeSourceResult(mName2, 1),
                        makeSourceResult(mName2, 2),
                        makeSourceResult(mName2, 3)
                )));

        assertContentsInOrder(
                "after duplicate is removed, expecting all 6 promoted slots to still be filled.",
                getSnapshotFromBacker(false),
                mShortcut1,
                // chunk 1
                facebook1,
                makeSourceResult(mName1, 1),
                // chunk 2
                makeSourceResult(mName2, 1),
                makeSourceResult(mName2, 2),
                // remainder
                makeSourceResult(mName1, 2),
                mSearchTheWeb,
                mMoreNotExpanded);
    }

    public void testShortcutsOnly() {
        mBacker = new TestBacker(
                "query",
                Lists.newArrayList(mShortcut1),
                Lists.<SuggestionSource>newArrayList(), // no sources
                Sets.<ComponentName>newHashSet(),
                mSource1,
                Lists.<SuggestionResult>newArrayList(),
                null,
                mSearchTheWeb,
                MAX_PROMOTED_SHOWING,
                DEADLINE,
                this,
                this);
        mBacker.setNow(NOW);

        assertContentsInOrder(
                "shortcuts only.",
                getSnapshotFromBacker(false),
                mShortcut1);

        assertContentsInOrder(
                "shortcuts only.",
                getSnapshotFromBacker(true),
                mShortcut1);
    }

    public void testAllSourcesPromotedResponded_resultsFitInPromotedSlots() {
        mBacker = new TestBacker(
                "query",
                Lists.newArrayList(mShortcut1),
                Lists.<SuggestionSource>newArrayList(mSource1, mSource2),
                Sets.<ComponentName>newHashSet(mName1, mName2), // every source is promoted
                mSource1,
                Lists.<SuggestionResult>newArrayList(),
                null,
                mSearchTheWeb,
                MAX_PROMOTED_SHOWING,
                DEADLINE,
                this,
                this);
        mBacker.setNow(NOW);

        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1))));
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1))));

        assertContentsInOrder(
                "should not show 'more' entries if all results fit, and there are no unpromoted " +
                        "sources.",
                getSnapshotFromBacker(true),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                mSearchTheWeb);
    }

    public void testCachedSourceResults() {

        final ArrayList<SuggestionResult> cached = Lists.newArrayList(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1))),
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1))),
                new SuggestionResult(mSource3, Lists.newArrayList(
                        makeSourceResult(mName3, 0),
                        makeSourceResult(mName3, 1))));

        mBacker = new TestBacker(
                "query",
                Lists.newArrayList(mShortcut1),
                Lists.<SuggestionSource>newArrayList(mSource1, mSource2, mSource3),
                Sets.<ComponentName>newHashSet(mName1, mName2), // promoted sources
                mSource1,
                cached,
                null,
                mSearchTheWeb,
                MAX_PROMOTED_SHOWING,
                DEADLINE,
                this,
                this);

        assertContentsInOrder(
                "three cached sources, 2 promoted, one not.",
                getSnapshotFromBacker(true),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_FINISHED, 2));
    }

    public void testGoToWebsiteSuggestion() {
        // Recreate the backer, this time with the "go to website" suggestion passed in.
        // Then check that the backer correctly shows the suggestion even when there are
        // lots of other results.
        mBacker = new TestBacker(
                "query",
                Lists.newArrayList(mShortcut1),
                Lists.<SuggestionSource>newArrayList(mSource1, mSource2, mSource3),
                Sets.newHashSet(mName1, mName2), // promoted sources
                mSource1,
                Lists.<SuggestionResult>newArrayList(),
                mGoToWebsite,
                mSearchTheWeb,
                MAX_PROMOTED_SHOWING,
                DEADLINE,
                this,
                this);

        // each one reports 4 results
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2),
                        makeSourceResult(mName1, 3)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1),
                        makeSourceResult(mName2, 2),
                        makeSourceResult(mName2, 3)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource3, Lists.newArrayList(
                        makeSourceResult(mName3, 0),
                        makeSourceResult(mName3, 1),
                        makeSourceResult(mName3, 2),
                        makeSourceResult(mName3, 3)
                )));

        assertContentsInOrder(
                "first suggestion should be go to website suggestion",
                getSnapshotFromBacker(false),
                mGoToWebsite,
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                makeSourceResult(mName1, 2),  // remaining slots (source 3 is not promoted)
                mSearchTheWeb,
                mMoreNotExpanded);
    }

    public void testPinToBottomSuggestion() {
        // each one reports 4 results; source 1 reports a pin-to-bottom suggestion last
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2),
                        makeSourceResult(mName1, 3),
                        makePinToBottomSourceResult(mName1, 4)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1),
                        makeSourceResult(mName2, 2),
                        makeSourceResult(mName2, 3)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource3, Lists.newArrayList(
                        makeSourceResult(mName3, 0),
                        makeSourceResult(mName3, 1),
                        makeSourceResult(mName3, 2),
                        makeSourceResult(mName3, 3)
                )));

        assertContentsInOrder(
                "pin to bottom non-expanded.",
                getSnapshotFromBacker(false),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                makeSourceResult(mName1, 2),  // remaining slots (source 3 is not promoted)
                mSearchTheWeb,
                makePinToBottomSourceResult(mName1, 4),
                mMoreNotExpanded);

        assertContentsInOrder(
                "pin to bottom expanded.",
                getSnapshotFromBacker(true),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                makeSourceResult(mName1, 2),
                mSearchTheWeb,
                makePinToBottomSourceResult(mName1, 4),
                mMoreExpanded,
                makeCorpusEntry(SOURCE1_LABEL, SourceStat.RESPONSE_FINISHED, 1),  // 1 remaining
                makeCorpusEntry(SOURCE2_LABEL, SourceStat.RESPONSE_FINISHED, 2),  // 2 remaining
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_FINISHED, 4));
    }

    public void testPinToBottomNotShownIfSourceNotInPromotedList() {
        // a couple source return before deadline
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1),
                        makeSourceResult(mName2, 2),
                        makeSourceResult(mName2, 3)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource3, Lists.newArrayList(
                        makeSourceResult(mName3, 0),
                        makeSourceResult(mName3, 1),
                        makeSourceResult(mName3, 2),
                        makeSourceResult(mName3, 3)
                )));

        // another returns after the deadline with a pin to bottom
        mBacker.setNow(NOW + DEADLINE);
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1),
                        makeSourceResult(mName1, 2),
                        makeSourceResult(mName1, 3),
                        makePinToBottomSourceResult(mName1, 4)
                )));

        assertContentsInOrder(
                "pin to bottom shouldn't be shown if its source responded after deadline.",
                getSnapshotFromBacker(false),
                mShortcut1,
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                makeSourceResult(mName2, 2),
                makeSourceResult(mName2, 3),
                mSearchTheWeb,
                mMoreNotExpanded);
    }

    /**
     * If non promoted sources report zero results before the user has expanded "more", then
     * we don't bother showing "source X reported 0 results" upon expansion.
     *
     * We also remove the "more results" once we know there are no sources under "more results"
     * to show.
     */
    public void testNonPromotedSourcesWithZeroResults_reportedBeforeViewed() {
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1)
                )));

        assertContentsInOrder(
                "non promoted source with zero results before viewed.",
                getSnapshotFromBacker(false),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                mSearchTheWeb,
                mMoreNotExpanded);

        // non-promoted source 3
        mBacker.addSourceResults(new SuggestionResult(mSource3));

        assertContentsInOrder(
                "non promoted source with zero results after expansion.",
                getSnapshotFromBacker(true),  // EXPANDED
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                mSearchTheWeb);  // no "more" results entry at all
    }

    /**
     * Similar to {@link #testNonPromotedSourcesWithZeroResults_reportedBeforeViewed()}, but in
     * this case, the user has expanded the "more results" and thus viewed the entries; we can't
     * remove them at this point and will continue to show them, even though they returned
     * zero results.
     */
    public void testNonPromotedSourcesWithZeroResults_reportedAfterViewed() {
        mBacker.addSourceResults(
                new SuggestionResult(mSource1, Lists.newArrayList(
                        makeSourceResult(mName1, 0),
                        makeSourceResult(mName1, 1)
                )));
        mBacker.addSourceResults(
                new SuggestionResult(mSource2, Lists.newArrayList(
                        makeSourceResult(mName2, 0),
                        makeSourceResult(mName2, 1)
                )));

        assertContentsInOrder(
                "non promoted source with zero results viewed before report.",
                getSnapshotFromBacker(true),
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_NOT_STARTED, 0));

        // non-promoted source 3
        mBacker.addSourceResults(new SuggestionResult(mSource3));

        assertContentsInOrder(
                "non promoted source with zero results pinned after being viewed.",
                getSnapshotFromBacker(true),  // EXPANDED
                mShortcut1,
                makeSourceResult(mName1, 0),
                makeSourceResult(mName1, 1),
                makeSourceResult(mName2, 0),
                makeSourceResult(mName2, 1),
                mSearchTheWeb,
                mMoreExpanded,
                makeCorpusEntry(SOURCE3_LABEL, SourceStat.RESPONSE_FINISHED, 0));
    }

    List<SuggestionData> getSnapshotFromBacker(boolean expandAdditional) {
        final ArrayList<SuggestionData> list = Lists.newArrayList();
        mBacker.snapshotSuggestions(list, expandAdditional);
        return list;
    }

    private SuggestionData makeCorpusEntry(
            String label, int responseStatus, int numResultsUndisplayed) {
        final SuggestionData.Builder builder = new SuggestionData.Builder(mName1);
        builder.title("more_" + label + " " + getResponseStatusString(responseStatus)
                + ", numleft: " + numResultsUndisplayed);
        return builder
                .build();
    }

    private String getResponseStatusString(int responseStatus) {
        switch (responseStatus) {
            case SourceStat.RESPONSE_NOT_STARTED:
                return "RESPONSE_NOT_STARTED";
            case SourceStat.RESPONSE_IN_PROGRESS:
                return "RESPONSE_IN_PROGRESS";
            case SourceStat.RESPONSE_FINISHED:
                return "RESPONSE_FINISHED";
            default:
                throw new IllegalArgumentException("unknown status " + responseStatus);
        }
    }

    private SuggestionData makeSourceResult(ComponentName name, int index) {
        return new SuggestionData.Builder(name)
                .title(name.getClassName() + " " + index)
                .intentAction(name.getClassName())
                .intentData("" + index)
                .build();
    }

    private SuggestionData makePinToBottomSourceResult(ComponentName name, int index) {
        return new SuggestionData.Builder(name)
                .title(name.getClassName() + " manage search history " + index)
                .intentAction(name.getClassName())
                .intentData("" + index)
                .pinToBottom(true)
                .build();
    }

    /**
     * Allows setting what "now" is for testing
     */
    private static class TestBacker extends SourceSuggestionBacker {

        long now = 0L;

        public TestBacker(
                String query,
                List<SuggestionData> shortcuts,
                List<SuggestionSource> sources,
                HashSet<ComponentName> promotedSources,
                SuggestionSource selectedWebSearchSource,
                List<SuggestionResult> cachedResults,
                SuggestionData goToWebsite,
                SuggestionData searchTheWeb,
                int maxPromotedSlots,
                long deadline,
                MoreExpanderFactory moreFactory,
                CorpusResultFactory corpusFactory) {
            super(query, shortcuts, sources, promotedSources, selectedWebSearchSource, cachedResults,
                    goToWebsite, searchTheWeb, maxPromotedSlots, deadline, moreFactory,
                    corpusFactory);
        }


        public long getNow() {
            return now;
        }

        public void setNow(long now) {
            this.now = now;
        }
    }

    static void assertContentsInOrder(Iterable<?> actual, Object... expected) {
        assertContentsInOrder(null, actual, expected);
    }

    /**
     * an implementation of {@link MoreAsserts#assertContentsInOrder(String, Iterable, Object[])}
     * with some additional information placed in the assert message in the error case to make it
     * easier to see where the mismatch is.
     */
    static void assertContentsInOrder(
            String message, Iterable<?> actual, Object... expected) {
        ArrayList actualList = new ArrayList();
        for (Object o : actual) {
            actualList.add(o);
        }
        StringBuilder sb = new StringBuilder();
        if (message != null) sb.append(message);
        final List<Object> expectedList = Arrays.asList(expected);

        if (expectedList.size() != actualList.size()) {
            sb.append("\nsize mismatch (expected: ").append(expectedList.size())
                    .append(" actual: ").append(actualList.size()).append('.');
        }
        for (int i = 0; i < Math.min(expectedList.size(), actualList.size()); i++) {
            final Object expectedItem = expectedList.get(i);
            final Object actualItem = actualList.get(i);
            if (!expectedItem.equals(actualItem)) {
                sb.append("\n").append("at index ").append(i)
                        .append(" expected: ").append(expectedItem)
                        .append("\n").append("actual: ").append(actualItem);
            }
        }
        Assert.assertEquals(sb.toString(), expectedList, actualList);
    }
}
