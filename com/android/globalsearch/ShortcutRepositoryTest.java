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

import android.app.SearchManager;
import android.content.ComponentName;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.Assert;
import com.google.android.collect.Lists;

/**
 * Abstract base class for tests of  {@link com.android.globalsearch.ShortcutRepository}
 * implementations.  Most importantly, verifies the
 * stuff we are doing with sqlite works how we expect it to.
 *
 * Attempts to test logic independent of the (sql) details of the implementation, so these should
 * be useful even in the face of a schema change.
 */
public class ShortcutRepositoryTest extends AndroidTestCase {

    static final long NOW = 1239841162000L; // millis since epoch. some time in 2009

    static final ComponentName APP_COMPONENT =
            new ComponentName("com.example.app","com.example.app.App");

    static final ComponentName CONTACTS_COMPONENT =
        new ComponentName("com.android.contacts","com.android.contacts.Contacts");

    static final ComponentName BOOKMARKS_COMPONENT =
        new ComponentName("com.android.browser","com.android.browser.Bookmarks");

    static final ComponentName HISTORY_COMPONENT =
        new ComponentName("com.android.browser","com.android.browser.History");

    static final ComponentName MUSIC_COMPONENT =
        new ComponentName("com.android.music","com.android.music.Music");

    static final ComponentName MARKET_COMPONENT =
        new ComponentName("com.android.vending","com.android.vending.Market");


    protected ShortcutRepositoryImplLog mRepo;
    protected SuggestionData mApp1;
    protected SuggestionData mApp2;
    protected SuggestionData mContact1;
    protected SuggestionData mContact2;

    protected ShortcutRepositoryImplLog createShortcutRepository() {
        return new ShortcutRepositoryImplLog(getContext(), "test-shortcuts-log.db");
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mRepo = createShortcutRepository();

        mApp1 = makeApp("app1");
        mApp2 = makeApp("app2");

        mContact1 = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("Joe Blow")
                .intentAction("view")
                .intentData("contacts/joeblow")
                .shortcutId("j-blow")
                .build();
        mContact2 = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("Mike Johnston")
                .intentAction("view")
                .intentData("contacts/mikeJ")
                .shortcutId("mo-jo")
                .build();
    }

    private SuggestionData makeApp(String name) {
        return new SuggestionData.Builder(APP_COMPONENT)
                .title(name)
                .intentAction("view")
                .intentData("apps/" + name)
                .shortcutId("shorcut_" + name)
                .build();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mRepo.deleteRepository();
    }
    
    public void testHasHistory() {
        assertFalse(mRepo.hasHistory());
        SessionStats stats = new SessionStats("foo", mApp1);
        mRepo.reportStats(stats);
        assertTrue(mRepo.hasHistory());
        mRepo.clearHistory();
        assertFalse(mRepo.hasHistory());
    }

    public void testNoMatch() {
        SuggestionData clicked = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("bob smith")
                .intentAction("action")
                .intentData("data")
                .build();
        SessionStats stats = new SessionStats(
                "bob smith",
                clicked);

        mRepo.reportStats(stats);
        MoreAsserts.assertEmpty(mRepo.getShortcutsForQuery("joe", NOW));
    }

    public void testFullPackingUnpacking() {
        SuggestionData clicked = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .format("<i>%s</i>")
                .title("title")
                .description("description")
                .icon1("icon1")
                .icon2("icon2")
                .intentAction("action")
                .intentData("data")
                .intentQuery("query")
                .intentExtraData("extradata")
                .intentComponentName("componentname")
                .shortcutId("idofshortcut")
                .build();

        mRepo.reportStats(new SessionStats("q", clicked), NOW);

        assertContentsInOrder(mRepo.getShortcutsForQuery("q", NOW), clicked);
        assertContentsInOrder(mRepo.getShortcutsForQuery("", NOW), clicked);
    }

    public void testSpinnerWhileRefreshing() {
        SuggestionData clicked = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .format("<i>%s</i>")
                .title("title")
                .description("description")
                .icon1("icon1")
                .icon2("icon2")
                .intentAction("action")
                .intentData("data")
                .intentQuery("query")
                .intentExtraData("extradata")
                .intentComponentName("componentname")
                .shortcutId("idofshortcut")
                .spinnerWhileRefreshing(true)
                .build();

        mRepo.reportStats(new SessionStats("q", clicked), NOW);

        SuggestionData expected = clicked.buildUpon()
                .icon2(String.valueOf(com.android.internal.R.drawable.search_spinner))
                .build();

        assertContentsInOrder(mRepo.getShortcutsForQuery("q", NOW), expected);
    }

    public void testPrefixesMatch() {
        MoreAsserts.assertEmpty(mRepo.getShortcutsForQuery("bob", NOW));

        SuggestionData clicked = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("bob smith the third")
                .intentAction("action")
                .intentData("intentdata")
                .build();
        SessionStats stats = new SessionStats("bob smith", clicked);

        mRepo.reportStats(stats, NOW);

        assertContentsInOrder("bob smith",
                mRepo.getShortcutsForQuery("bob smith", NOW),
                clicked);
        assertContentsInOrder("bob s",
                mRepo.getShortcutsForQuery("bob s", NOW),
                clicked);
        assertContentsInOrder("b",
                mRepo.getShortcutsForQuery("b", NOW),
                clicked);
    }

    public void testMatchesOneAndNotOthers() {
        SuggestionData bob = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("bob smith the third")
                .intentAction("action")
                .intentData("intentdata/bob")
                .build();

        mRepo.reportStats(new SessionStats("bob", bob), NOW);

        SuggestionData george = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("george jones")
                .intentAction("action")
                .intentData("intentdata/george")
                .build();

        mRepo.reportStats(new SessionStats("geor", george), NOW);

        assertContentsInOrder("b for bob",
                mRepo.getShortcutsForQuery("b", NOW),
                bob);
        assertContentsInOrder("g for george",
                mRepo.getShortcutsForQuery("g", NOW),
                george);
    }

    public void testDifferentPrefixesMatchSameEntity() {
        SuggestionData clicked = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("bob smith the third")
                .intentAction("action")
                .intentData("intentdata")
                .build();

        mRepo.reportStats(new SessionStats("bob", clicked));
        mRepo.reportStats(new SessionStats("smith", clicked));
        assertContentsInOrder("b",
                mRepo.getShortcutsForQuery("b", NOW),
                clicked);
        assertContentsInOrder("s",
                mRepo.getShortcutsForQuery("s", NOW),
                clicked);        
    }

    public void testMoreClicksWins() {
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("app", mApp2), NOW);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);


        assertContentsInOrder("expected app1 to beat app2 since it has more hits",
                mRepo.getShortcutsForQuery("app", NOW),
                mApp1, mApp2);

        mRepo.reportStats(new SessionStats("app", mApp2), NOW);
        mRepo.reportStats(new SessionStats("app", mApp2), NOW);

        assertContentsInOrder(
                "query 'app': expecting app2 to beat app1 since it has more hits",
                mRepo.getShortcutsForQuery("app", NOW),
                mApp2, mApp1);
        assertContentsInOrder(
                "query 'a': expecting app2 to beat app1 since it has more hits",
                mRepo.getShortcutsForQuery("app", NOW),
                mApp2, mApp1);
    }

    /**
     * similar to {@link #testMoreClicksWins()} but clicks are reported with prefixes of the
     * original query.  we want to make sure a match on query 'a' updates the stats for the
     * entry it matched against, 'app'.
     */
    public void testPrefixMatchUpdatesSameEntry() {
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("app", mApp2), NOW);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);

        assertContentsInOrder("expected app1 to beat app2 since it has more hits",
                mRepo.getShortcutsForQuery("app", NOW),
                mApp1, mApp2);
    }

    private static final long DAY_MILLIS = 86400000L; // just ask the google
    private static final long HOUR_MILLIS = 3600000L;

    public void testMoreRecentlyClickedWins() {
        SuggestionData app3 = new SuggestionData.Builder(APP_COMPONENT)
                .title("third application")
                .intentAction("action/app3")
                .intentData("intentdata")
                .build();

        mRepo.reportStats(new SessionStats("app", mApp1), NOW - DAY_MILLIS*2);
        mRepo.reportStats(new SessionStats("app", mApp2), NOW);
        mRepo.reportStats(new SessionStats("app", app3), NOW - DAY_MILLIS*4);

        assertContentsInOrder("expecting more recently clicked app to rank higher",
                mRepo.getShortcutsForQuery("app", NOW),
                mApp2, mApp1, app3);
    }

    public void testRecencyOverridesClicks() {

        // 5 clicks, most recent half way through age limit
        long halfWindow = ShortcutRepository.MAX_STAT_AGE_MILLIS / 2;
        mRepo.reportStats(new SessionStats("app", mApp1), NOW - halfWindow);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW - halfWindow);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW - halfWindow);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW - halfWindow);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW - halfWindow);

        // 3 clicks, the most recent very recent
        mRepo.reportStats(new SessionStats("app", mApp2), NOW - HOUR_MILLIS);
        mRepo.reportStats(new SessionStats("app", mApp2), NOW - HOUR_MILLIS);
        mRepo.reportStats(new SessionStats("app", mApp2), NOW - HOUR_MILLIS);

        assertContentsInOrder("expecting 3 recent clicks to beat 5 clicks long ago",
                mRepo.getShortcutsForQuery("app", NOW),
                mApp2, mApp1);
    }

    public void testEntryOlderThanAgeLimitFiltered() {
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);

        long pastWindow = ShortcutRepository.MAX_STAT_AGE_MILLIS + 1000;
        mRepo.reportStats(new SessionStats("app", mApp2), NOW - pastWindow);

        assertContentsInOrder("expecting app2 not clicked on recently enough to be filtered",
                mRepo.getShortcutsForQuery("app", NOW),
                mApp1);
    }

    public void testZeroQueryResults_MoreClicksWins() {
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("foo", mApp2), NOW);

        assertContentsInOrder(
                mRepo.getShortcutsForQuery("", NOW),
                mApp1, mApp2);

        mRepo.reportStats(new SessionStats("foo", mApp2), NOW);
        mRepo.reportStats(new SessionStats("foo", mApp2), NOW);

        assertContentsInOrder(
                mRepo.getShortcutsForQuery("", NOW),
                mApp2, mApp1);
    }

    public void testZeroQueryResults_DifferentQueryhitsCreditSameShortcut() {
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("foo", mApp2), NOW);
        mRepo.reportStats(new SessionStats("bar", mApp2), NOW);

        assertContentsInOrder("hits for 'foo' and 'bar' on app2 should have combined to rank it " +
                "ahead of app1, which only has one hit.",
                mRepo.getShortcutsForQuery("", NOW),
                mApp2, mApp1);

        mRepo.reportStats(new SessionStats("z", mApp1), NOW);
        mRepo.reportStats(new SessionStats("w", mApp1), NOW);

        assertContentsInOrder(
                mRepo.getShortcutsForQuery("", NOW),
                mApp1, mApp2);  
    }

    public void testZeroQueryResults_zeroQueryHitCounts() {
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("", mApp2), NOW);
        mRepo.reportStats(new SessionStats("", mApp2), NOW);

        assertContentsInOrder("hits for '' on app2 should have combined to rank it " +
                "ahead of app1, which only has one hit.",
                mRepo.getShortcutsForQuery("", NOW),
                mApp2, mApp1);  

        mRepo.reportStats(new SessionStats("", mApp1), NOW);
        mRepo.reportStats(new SessionStats("", mApp1), NOW);

        assertContentsInOrder("zero query hits for app1 should have made it higher than app2.",
                mRepo.getShortcutsForQuery("", NOW),
                mApp1, mApp2);

        assertContentsInOrder("query for 'a' should only match app1.",
                mRepo.getShortcutsForQuery("a", NOW),
                mApp1);
    }

    public void testRefreshShortcut() {

        final SuggestionData app1 = new SuggestionData.Builder(APP_COMPONENT)
                .format("format")
                .title("app1")
                .description("cool app")
                .shortcutId("app1_id")
                .build();

        mRepo.reportStats(new SessionStats("app", app1));

        final SuggestionData updated = app1.buildUpon()
                .format("format (updated)")
                .title("app1 (updated)")
                .build();

        mRepo.refreshShortcut(APP_COMPONENT, "app1_id", updated);

        assertContentsInOrder("expected updated properties in match",
                mRepo.getShortcutsForQuery("app", NOW),
                updated);
    }

    public void testRefreshShortcutChangedIntent() {

        final SuggestionData app1 = new SuggestionData.Builder(APP_COMPONENT)
                .intentData("data")
                .format("format")
                .title("app1")
                .description("cool app")
                .shortcutId("app1_id")
                .build();

        mRepo.reportStats(new SessionStats("app", app1));

        final SuggestionData updated = app1.buildUpon()
                .intentData("data-updated")
                .format("format (updated)")
                .title("app1 (updated)")
                .build();

        mRepo.refreshShortcut(APP_COMPONENT, "app1_id", updated);

        assertContentsInOrder("expected updated properties in match",
                mRepo.getShortcutsForQuery("app", NOW),
                updated);
    }

    public void testInvalidateShortcut() {
        final SuggestionData app1 = new SuggestionData.Builder(APP_COMPONENT)
                .title("app1")
                .description("cool app")
                .shortcutId("app1_id")
                .build();

        mRepo.reportStats(new SessionStats("app", app1));

        // passing null should remove the shortcut
        mRepo.refreshShortcut(APP_COMPONENT, "app1_id", null);

        MoreAsserts.assertEmpty("should be no matches since shortcut is invalid.",
                mRepo.getShortcutsForQuery("app", NOW));
    }

    public void testInvalidateShortcut_sameIdDifferentSources() {
        final String sameid = "same_id";
        final SuggestionData app = new SuggestionData.Builder(APP_COMPONENT)
                .title("app1")
                .description("cool app")
                .shortcutId(sameid)
                .build();
        mRepo.reportStats(new SessionStats("app", app), NOW);

        final SuggestionData contact = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("joe blow")
                .description("a good pal")
                .shortcutId(sameid)
                .build();
        mRepo.reportStats(new SessionStats("joe", contact), NOW);

        mRepo.refreshShortcut(APP_COMPONENT, sameid, null);
        MoreAsserts.assertEmpty("app should not be there.",
                mRepo.getShortcutsForQuery("app", NOW));
        assertContentsInOrder("contact with same shortcut id should still be there.",
                mRepo.getShortcutsForQuery("joe", NOW), contact);
    }
    
    public void testNeverMakeShortcut() {
        final SuggestionData contact = new SuggestionData.Builder(CONTACTS_COMPONENT)
                .title("unshortcuttable contact")
                .description("you didn't want to call them again anyway")
                .shortcutId(SearchManager.SUGGEST_NEVER_MAKE_SHORTCUT)
                .build();
        mRepo.reportStats(new SessionStats("unshortcuttable", contact), NOW);
        MoreAsserts.assertEmpty("never-shortcutted suggestion should not be there.",
                mRepo.getShortcutsForQuery("unshortcuttable", NOW));
    }

    public void testCountResetAfterShortcutDeleted() {
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);

        mRepo.reportStats(new SessionStats("app", mApp2), NOW);
        mRepo.reportStats(new SessionStats("app", mApp2), NOW);

        // app1 wins 4 - 2
        assertContentsInOrder(mRepo.getShortcutsForQuery("app", NOW), mApp1, mApp2);

        // reset to 1
        mRepo.refreshShortcut(APP_COMPONENT, mApp1.getShortcutId(), null);
        mRepo.reportStats(new SessionStats("app", mApp1), NOW);

        // app2 wins 2 - 1
        assertContentsInOrder("expecting app1's click count to reset after being invalidated.",
                mRepo.getShortcutsForQuery("app", NOW), mApp2, mApp1);
    }

    public void testShortcutsLimitedCount() {

        for (int i = 1; i <= 20; i++) {
            mRepo.reportStats(new SessionStats("a", makeApp("app" + i)), NOW);
        }

        assertEquals("number of shortcuts should be limited.",
                12, mRepo.getShortcutsForQuery("", NOW).size());

    }



    //
    // SOURCE RANKING TESTS BELOW
    //

    public void testSourceRanking_moreClicksWins() {

        // click on an app, impression for both apps and contacts
        mRepo.reportStats(
                new SessionStats("a",
                        mApp1,
                        Lists.newArrayList(APP_COMPONENT, CONTACTS_COMPONENT)), NOW);

        assertContentsInOrder("expecting apps to rank ahead of contacts (more clicks)",
                mRepo.getSourceRanking(0, 0),
                APP_COMPONENT, CONTACTS_COMPONENT);

        // 2 clicks on a contact, impression for both apps and contacts
        mRepo.reportStats(
                new SessionStats("a",
                        mContact1,
                        Lists.newArrayList(APP_COMPONENT, CONTACTS_COMPONENT)), NOW);
        mRepo.reportStats(
                new SessionStats("a",
                        mContact1,
                        Lists.newArrayList(APP_COMPONENT, CONTACTS_COMPONENT)), NOW);

        assertContentsInOrder("expecting contacts to rank ahead of apps (more clicks)",
                mRepo.getSourceRanking(0, 0),
                CONTACTS_COMPONENT, APP_COMPONENT);
    }

    public void testSourceRanking_higherCTRWins() {

        // app: 1 click in 2 impressions
        sourceImpression(APP_COMPONENT, true);
        sourceImpression(APP_COMPONENT, false);

        // contacts: 2 clicks in 5 impressions
        sourceImpression(CONTACTS_COMPONENT, true);
        sourceImpression(CONTACTS_COMPONENT, true);
        sourceImpression(CONTACTS_COMPONENT, false);
        sourceImpression(CONTACTS_COMPONENT, false);
        sourceImpression(CONTACTS_COMPONENT, false);

        assertContentsInOrder(
                "apps (1 click / 2 impressions) should beat contacts (2 clicks / 5 impressions)",
                mRepo.getSourceRanking(0, 0),
                APP_COMPONENT, CONTACTS_COMPONENT);

        // contacts: up to 4 clicks in 7 impressions
        sourceImpression(CONTACTS_COMPONENT, true);
        sourceImpression(CONTACTS_COMPONENT, true);

        assertContentsInOrder(
                "contacts (4 click / 7 impressions) should beat apps (1 clicks / 2 impressions)",
                mRepo.getSourceRanking(0, 0),
                CONTACTS_COMPONENT, APP_COMPONENT);
    }

    public void testOldSourceStatsDontCount() {
        // apps were popular back in the day
        final long toOld = ShortcutRepository.MAX_SOURCE_EVENT_AGE_MILLIS + 1;
        sourceImpression(APP_COMPONENT, true, NOW - toOld);
        sourceImpression(APP_COMPONENT, true, NOW - toOld);
        sourceImpression(APP_COMPONENT, true, NOW - toOld);
        sourceImpression(APP_COMPONENT, true, NOW - toOld);

        // more recently apps has bombed
        sourceImpression(APP_COMPONENT, false, NOW);
        sourceImpression(APP_COMPONENT, false, NOW);

        // and apps is 1/2
        sourceImpression(CONTACTS_COMPONENT, true, NOW);
        sourceImpression(CONTACTS_COMPONENT, false, NOW);

        assertContentsInOrder(
                "old clicks for apps shouldn't count.",
                mRepo.getSourceRanking(0, 0),
                CONTACTS_COMPONENT, APP_COMPONENT);
    }


    public void testSourceRanking_filterSourcesWithInsufficientData() {
        sourceImpressions(APP_COMPONENT, 1, 5);
        sourceImpressions(CONTACTS_COMPONENT, 1, 2);
        sourceImpressions(BOOKMARKS_COMPONENT, 9, 10);
        sourceImpressions(HISTORY_COMPONENT, 4, 4);
        sourceImpressions(MUSIC_COMPONENT, 0, 1);
        sourceImpressions(MARKET_COMPONENT, 4, 8);
        
        assertContentsInOrder(
                "ordering should only include sources with at least 5 impressions.",
                mRepo.getSourceRanking(5, 0),
                BOOKMARKS_COMPONENT, MARKET_COMPONENT, APP_COMPONENT);

        assertContentsInOrder(
                "ordering should only include sources with at least 2 clicks.",
                mRepo.getSourceRanking(0, 2),
                HISTORY_COMPONENT, BOOKMARKS_COMPONENT, MARKET_COMPONENT);

        assertContentsInOrder(
                "ordering should only include sources with at least 5 impressions and 3 clicks.",
                mRepo.getSourceRanking(5, 3),
                BOOKMARKS_COMPONENT, MARKET_COMPONENT);
    }

    protected void sourceImpressions(ComponentName source, int clicks, int impressions) {
        if (clicks > impressions) throw new IllegalArgumentException("ya moran!");

        for (int i = 0; i < impressions; i++, clicks--) {
            sourceImpression(source, clicks > 0);
        }
    }

    /**
     * Simulate an impression, and optionally a click, on a source.
     *
     * @param source The name of the source.
     * @param click Whether to register a click in addition to the impression.
     */
    protected void sourceImpression(ComponentName source, boolean click) {
        sourceImpression(source, click, NOW);
    }

    /**
     * Simulate an impression, and optionally a click, on a source.
     *
     * @param source The name of the source.
     * @param click Whether to register a click in addition to the impression.
     */
    protected void sourceImpression(ComponentName source, boolean click, long now) {
        SuggestionData suggestionClicked = !click ?
                null :
                new SuggestionData.Builder(source)
                    .intentAction("view")
                    .intentData("data/id")
                    .shortcutId("shortcutid")
                    .build();

        mRepo.reportStats(
                new SessionStats("a",
                        suggestionClicked,
                        Lists.newArrayList(source)), now);
    }


    static void assertContentsInOrder(Iterable<?> actual, Object... expected) {
        assertContentsInOrder(null, actual, expected);
    }

    /**
     * an implementation of {@link MoreAsserts#assertContentsInOrder(String, Iterable, Object[])}
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
