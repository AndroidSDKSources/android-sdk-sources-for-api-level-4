package com.android.globalsearch;

import android.test.AndroidTestCase;

/**
 * Tests GLOBAL_SEARCH_CONTROL protects launching {@link GlobalSearch}.
 */
public class LaunchSuggestionPermissionTest extends AndroidTestCase {

    public void testGlobalSearchRequires_LAUNCH_SUGGESTIONS() {
        assertActivityRequiresPermission(
                "com.android.globalsearch",
                "com.android.globalsearch.GlobalSearch",
                android.Manifest.permission.GLOBAL_SEARCH_CONTROL);
    }
}
