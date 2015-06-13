package org.apache.harmony.regex.tests.java.util.regex;

import dalvik.annotation.TestTargetClass;
import dalvik.annotation.TestTargets;
import dalvik.annotation.TestTargetNew;
import dalvik.annotation.TestLevel;

import junit.framework.TestCase;
import java.util.regex.*;

@TestTargetClass(java.util.regex.Pattern.class)
/**
 * TODO Type description
 * 
 */
public class SplitTest extends TestCase {
    @TestTargets({
        @TestTargetNew(
            level = TestLevel.PARTIAL_COMPLETE,
            notes = "Verifies the basic functionality of split(java.lang.CharSequence) & compile(java.lang.String)methods.",
            method = "split",
            args = {java.lang.CharSequence.class}
        ),
        @TestTargetNew(
            level = TestLevel.PARTIAL_COMPLETE,
            notes = "Verifies the basic functionality of split(java.lang.CharSequence) & compile(java.lang.String)methods.",
            method = "compile",
            args = {java.lang.String.class}
        )
    })          
    public void testSimple() {
        Pattern p = Pattern.compile("/");
        String[] results = p.split("have/you/done/it/right");
        String[] expected = new String[] { "have", "you", "done", "it", "right" };
        assertEquals(expected.length, results.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(results[i], expected[i]);
        }
    }

    @TestTargetNew(
        level = TestLevel.PARTIAL_COMPLETE,
        notes = "Verifies the functionality of split(java.lang.CharSequence). Test uses not empty pattern.",
        method = "split",
        args = {java.lang.CharSequence.class, int.class}
    )          
    public void testSplit1() throws PatternSyntaxException {
        Pattern p = Pattern.compile(" ");

        String input = "poodle zoo";
        String tokens[];

        tokens = p.split(input, 1);
        assertEquals(1, tokens.length);
        assertTrue(tokens[0].equals(input));
        tokens = p.split(input, 2);
        assertEquals(2, tokens.length);
        assertEquals("poodle", tokens[0]);
        assertEquals("zoo", tokens[1]);
        tokens = p.split(input, 5);
        assertEquals(2, tokens.length);
        assertEquals("poodle", tokens[0]);
        assertEquals("zoo", tokens[1]);
        tokens = p.split(input, -2);
        assertEquals(2, tokens.length);
        assertEquals("poodle", tokens[0]);
        assertEquals("zoo", tokens[1]);
        tokens = p.split(input, 0);
        assertEquals(2, tokens.length);
        assertEquals("poodle", tokens[0]);
        assertEquals("zoo", tokens[1]);
        tokens = p.split(input);
        assertEquals(2, tokens.length);
        assertEquals("poodle", tokens[0]);
        assertEquals("zoo", tokens[1]);

        p = Pattern.compile("d");

        tokens = p.split(input, 1);
        assertEquals(1, tokens.length);
        assertTrue(tokens[0].equals(input));
        tokens = p.split(input, 2);
        assertEquals(2, tokens.length);
        assertEquals("poo", tokens[0]);
        assertEquals("le zoo", tokens[1]);
        tokens = p.split(input, 5);
        assertEquals(2, tokens.length);
        assertEquals("poo", tokens[0]);
        assertEquals("le zoo", tokens[1]);
        tokens = p.split(input, -2);
        assertEquals(2, tokens.length);
        assertEquals("poo", tokens[0]);
        assertEquals("le zoo", tokens[1]);
        tokens = p.split(input, 0);
        assertEquals(2, tokens.length);
        assertEquals("poo", tokens[0]);
        assertEquals("le zoo", tokens[1]);
        tokens = p.split(input);
        assertEquals(2, tokens.length);
        assertEquals("poo", tokens[0]);
        assertEquals("le zoo", tokens[1]);

        p = Pattern.compile("o");

        tokens = p.split(input, 1);
        assertEquals(1, tokens.length);
        assertTrue(tokens[0].equals(input));
        tokens = p.split(input, 2);
        assertEquals(2, tokens.length);
        assertEquals("p", tokens[0]);
        assertEquals("odle zoo", tokens[1]);
        tokens = p.split(input, 5);
        assertEquals(5, tokens.length);
        assertEquals("p", tokens[0]);
        assertTrue(tokens[1].equals(""));
        assertEquals("dle z", tokens[2]);
        assertTrue(tokens[3].equals(""));
        assertTrue(tokens[4].equals(""));
        tokens = p.split(input, -2);
        assertEquals(5, tokens.length);
        assertEquals("p", tokens[0]);
        assertTrue(tokens[1].equals(""));
        assertEquals("dle z", tokens[2]);
        assertTrue(tokens[3].equals(""));
        assertTrue(tokens[4].equals(""));
        tokens = p.split(input, 0);
        assertEquals(3, tokens.length);
        assertEquals("p", tokens[0]);
        assertTrue(tokens[1].equals(""));
        assertEquals("dle z", tokens[2]);
        tokens = p.split(input);
        assertEquals(3, tokens.length);
        assertEquals("p", tokens[0]);
        assertTrue(tokens[1].equals(""));
        assertEquals("dle z", tokens[2]);
    }

    @TestTargetNew(
        level = TestLevel.PARTIAL_COMPLETE,
        notes = "Verifies the functionality of split(java.lang.CharSequence). Test uses empty pattern.",
        method = "split",
        args = {java.lang.CharSequence.class, int.class}
    )          
    public void testSplit2() {
        Pattern p = Pattern.compile("");
        String s[];
        s = p.split("a", -1);
        assertEquals(3, s.length);
        assertEquals("", s[0]);
        assertEquals("a", s[1]);
        assertEquals("", s[2]);

        s = p.split("", -1);
        assertEquals(1, s.length);
        assertEquals("", s[0]);

        s = p.split("abcd", -1);
        assertEquals(6, s.length);
        assertEquals("", s[0]);
        assertEquals("a", s[1]);
        assertEquals("b", s[2]);
        assertEquals("c", s[3]);
        assertEquals("d", s[4]);
        assertEquals("", s[5]);
        
        // Regression test for Android
        assertEquals("GOOG,23,500".split("|").length, 12);
    }


    @TestTargets({
        @TestTargetNew(
            level = TestLevel.PARTIAL_COMPLETE,
            notes = "Verifies the functionality of split(java.lang.CharSequence) & compile(java.lang.String, int) methods. Test uses empty pattern and supplementary chars.",
            method = "split",
            args = {java.lang.CharSequence.class, int.class}
        ),
        @TestTargetNew(
            level = TestLevel.PARTIAL_COMPLETE,
            notes = "Verifies the functionality of split(java.lang.CharSequence) & compile(java.lang.String, int) methods. Test uses empty pattern and supplementary chars.",
            method = "compile",
            args = {java.lang.String.class}
        )
    })          
    public void testSplitSupplementaryWithEmptyString() {
        
        /*
         * See http://www.unicode.org/reports/tr18/#Supplementary_Characters
         * We have to treat text as code points not code units.
         */
        Pattern p = Pattern.compile("");
        String s[];
        s = p.split("a\ud869\uded6b", -1);
        assertEquals(5, s.length);
        assertEquals("", s[0]);
        assertEquals("a", s[1]);
        assertEquals("\ud869\uded6", s[2]);
        assertEquals("b", s[3]);                
        assertEquals("", s[4]);        
    }
}
