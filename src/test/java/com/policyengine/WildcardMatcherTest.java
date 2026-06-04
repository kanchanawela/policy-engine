package com.policyengine;

import com.policyengine.wildcard.WildcardMatcher;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class WildcardMatcherTest {

    private boolean match(String glob, String input) {
        return WildcardMatcher.matches(WildcardMatcher.toPattern(glob), input);
    }

    @Test
    void exactMatch() {
        assertTrue(match("user:alice", "user:alice"));
        assertFalse(match("user:alice", "user:bob"));
    }

    @Test
    void starMatchesAnything() {
        assertTrue(match("*", "anything"));
        assertTrue(match("*", "user:alice"));
        assertTrue(match("*", ""));
    }

    @Test
    void prefixGlob() {
        assertTrue(match("group:finance-*", "group:finance-london"));
        assertTrue(match("group:finance-*", "group:finance-"));
        assertFalse(match("group:finance-*", "group:financelondon"));   // no separator after prefix
        assertFalse(match("group:finance-*", "group:ops"));
    }

    @Test
    void midGlob() {
        assertTrue(match("arn:data:*/report", "arn:data:finance/report"));
        assertTrue(match("arn:data:*/report", "arn:data:x/report"));
        assertFalse(match("arn:data:*/report", "arn:data:x/other"));
    }

    @Test
    void questionMarkMatchesSingleChar() {
        assertTrue(match("user:ali?e", "user:alice"));
        assertTrue(match("user:ali?e", "user:aliXe"));
        assertFalse(match("user:ali?e", "user:alicee")); // too long
        assertFalse(match("user:ali?e", "user:alie"));   // too short
    }

    @Test
    void regexMetaCharsAreEscaped() {
        assertTrue(match("user:alice.doe", "user:alice.doe"));
        assertFalse(match("user:alice.doe", "user:aliceXdoe")); // dot is literal, not regex .
        assertTrue(match("arn:s3:bucket/key+1", "arn:s3:bucket/key+1")); // + is literal
    }

    @Test
    void nullInputReturnsFalse() {
        Pattern p = WildcardMatcher.toPattern("*");
        assertFalse(WildcardMatcher.matches(p, null));
    }

    @Test
    void specificityScores() {
        assertEquals(0,   WildcardMatcher.specificityScore("*"));
        assertEquals(100, WildcardMatcher.specificityScore("user:alice"));
        assertEquals(60,  WildcardMatcher.specificityScore("group:finance-*"));
        assertEquals(50,  WildcardMatcher.specificityScore("arn:*:bucket"));
        assertEquals(40,  WildcardMatcher.specificityScore("user:alic?"));
    }
}
