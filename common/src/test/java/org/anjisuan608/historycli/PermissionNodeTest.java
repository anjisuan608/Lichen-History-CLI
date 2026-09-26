package org.anjisuan608.historycli;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionNodeTest {

    @Test
    void actionMapsToMatchingNode() {
        assertEquals(PermissionNode.LIST, PermissionNode.forAction(HistoryParser.Action.LIST));
        assertEquals(PermissionNode.LIST, PermissionNode.forAction(HistoryParser.Action.LIST_EXPLICIT));
        assertEquals(PermissionNode.LIST, PermissionNode.forAction(HistoryParser.Action.EMPTY));
        assertEquals(PermissionNode.LIST, PermissionNode.forAction(HistoryParser.Action.HELP));
        assertEquals(PermissionNode.CLEAR, PermissionNode.forAction(HistoryParser.Action.CLEAR));
        assertEquals(PermissionNode.DELETE, PermissionNode.forAction(HistoryParser.Action.DELETE));
        assertEquals(PermissionNode.RELOAD, PermissionNode.forAction(HistoryParser.Action.RELOAD));
        assertEquals(PermissionNode.EXECUTE, PermissionNode.forAction(HistoryParser.Action.EXECUTE));
        assertEquals(PermissionNode.LIST, PermissionNode.forAction(null));
    }

    @Test
    void wildcardGrantsEverything() {
        Set<String> granted = Set.of(PermissionNode.ALL);
        for (HistoryParser.Action action : HistoryParser.Action.values()) {
            assertTrue(PermissionNode.isGranted(granted::contains, action), "historycli.* 应放行 " + action);
        }
    }

    @Test
    void exactNodeGrantsOnlyItsOwnAction() {
        Set<String> granted = Set.of(PermissionNode.CLEAR);
        assertTrue(PermissionNode.isGranted(granted::contains, HistoryParser.Action.CLEAR));
        assertFalse(PermissionNode.isGranted(granted::contains, HistoryParser.Action.WRITE));
        assertFalse(PermissionNode.isGranted(granted::contains, HistoryParser.Action.LIST));
        assertFalse(PermissionNode.isGranted(granted::contains, HistoryParser.Action.EXECUTE));
    }

    @Test
    void useGrantsReadOnlyListAccessOnly() {
        Set<String> granted = Set.of(PermissionNode.USE);
        assertTrue(PermissionNode.isGranted(granted::contains, HistoryParser.Action.LIST));
        assertTrue(PermissionNode.isGranted(granted::contains, HistoryParser.Action.LIST_EXPLICIT));
        assertTrue(PermissionNode.isGranted(granted::contains, HistoryParser.Action.HELP));
        assertTrue(PermissionNode.isGranted(granted::contains, HistoryParser.Action.EMPTY));
        assertFalse(PermissionNode.isGranted(granted::contains, HistoryParser.Action.CLEAR));
        assertFalse(PermissionNode.isGranted(granted::contains, HistoryParser.Action.DELETE));
        assertFalse(PermissionNode.isGranted(granted::contains, HistoryParser.Action.RELOAD));
        assertFalse(PermissionNode.isGranted(granted::contains, HistoryParser.Action.EXECUTE));
    }

    @Test
    void nothingGrantedMeansNoAccess() {
        Set<String> granted = new HashSet<>();
        for (HistoryParser.Action action : HistoryParser.Action.values()) {
            assertFalse(PermissionNode.isGranted(granted::contains, action));
        }
        assertFalse(PermissionNode.isGranted(null, HistoryParser.Action.LIST));
    }
}
