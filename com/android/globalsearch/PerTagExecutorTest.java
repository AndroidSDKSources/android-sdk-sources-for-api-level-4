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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link PerTagExecutor}.
 */
public class PerTagExecutorTest extends TestCase {

    private ExecutorService mExecutor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mExecutor = new ThreadPoolExecutor(4, 4,
                100, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

    public void testLimit() throws Exception {
        PerTagExecutor tagExecutor = new PerTagExecutor(mExecutor, 2);

        TRunnable a1 = new TRunnable();
        TRunnable a2 = new TRunnable();
        TRunnable a3 = new TRunnable();

        TRunnable b1 = new TRunnable();

        assertFalse(tagExecutor.execute("a", a1));
        assertFalse(tagExecutor.execute("a", a2));
        assertTrue(tagExecutor.execute("a", a3));
        assertFalse(tagExecutor.execute("b", b1));

        mExecutor.shutdown();
        mExecutor.awaitTermination(1, TimeUnit.SECONDS);

        assertTrue(a1.hasRun());
        assertTrue(a2.hasRun());
        assertFalse(a3.hasRun());
        assertTrue(b1.hasRun());
    }

    public void testPendingRuns() throws Exception {
        PerTagExecutor tagExecutor = new PerTagExecutor(mExecutor, 2);

        TRunnable a1 = new TRunnable();
        TRunnable a2 = new TRunnable();
        TRunnable a3 = new TRunnable();

        tagExecutor.execute("a", a1);
        tagExecutor.execute("a", a2);
        tagExecutor.execute("a", a3);

        // let a1 finish, should trigger a3 to run
        synchronized (a1) {
            a1.notify();
        }

        mExecutor.shutdown();
        mExecutor.awaitTermination(1, TimeUnit.SECONDS);

        assertTrue(a1.hasRun());
        assertTrue(a2.hasRun());
        assertTrue(a3.hasRun());
    }

    public void testPendingRuns_intermediateDropped() throws Exception {
        PerTagExecutor tagExecutor = new PerTagExecutor(mExecutor, 2);

        TRunnable a1 = new TRunnable();
        TRunnable a2 = new TRunnable();
        TRunnable a3 = new TRunnable();
        TRunnable a4 = new TRunnable();

        tagExecutor.execute("a", a1);
        tagExecutor.execute("a", a2);
        tagExecutor.execute("a", a3);
        tagExecutor.execute("a", a4);

        // let a1 finish, should trigger a3 to run
        synchronized (a1) {
            a1.notify();
        }

        mExecutor.shutdown();
        mExecutor.awaitTermination(1, TimeUnit.SECONDS);

        assertTrue(a1.hasRun());
        assertTrue(a2.hasRun());
        assertFalse("pending a3 should have been dropped when a4 was executed.", a3.hasRun());
        assertTrue(a4.hasRun());
    }

    /**
     * A runnable that knows when it has been run, and waits until notified to finish.
     */
    private static class TRunnable implements Runnable {
        boolean mRun = false;

        public synchronized void run() {
            mRun = true;
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean hasRun() {
            return mRun;
        }
    }
}
