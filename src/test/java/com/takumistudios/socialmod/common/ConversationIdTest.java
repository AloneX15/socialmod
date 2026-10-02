package com.takumistudios.socialmod.common;

import com.takumistudios.socialmod.common.model.ConversationId;
import com.takumistudios.socialmod.common.model.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationIdTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void directIsSymmetric() {
        assertEquals(ConversationId.direct(A, B), ConversationId.direct(B, A));
        assertEquals(ConversationId.direct(A, B).key(), ConversationId.direct(B, A).key());
        assertEquals(B, ConversationId.direct(A, B).other(A));
        assertTrue(ConversationId.direct(A, B).involves(B));
    }

    @Test
    void roundTripThroughKey() {
        ConversationId direct = ConversationId.direct(A, B);
        assertEquals(direct, ConversationId.parse(direct.key()));
        ConversationId group = ConversationId.group("abcd1234", "General");
        assertEquals("g:abcd1234:general", group.key());
        assertEquals(group, ConversationId.parse(group.key()));
    }

    @Test
    void rejectsGarbage() {
        assertNull(ConversationId.parse("dm:not-a-uuid:x"));
        assertNull(ConversationId.parse("g:../../etc:general"));
        assertNull(ConversationId.parse("g:abcd1234:bad channel"));
        assertNull(ConversationId.parse("x".repeat(500)));
        assertNull(ConversationId.parse(null));
    }

    @Test
    void fileNameIsSafe() {
        assertFalse(ConversationId.direct(A, B).fileName().contains(":"));
    }

    @Test
    void rolesOrder() {
        assertTrue(Role.LEADER.outranks(Role.OFFICER));
        assertTrue(Role.OFFICER.atLeast(Role.MEMBER));
        assertFalse(Role.RECRUIT.atLeast(Role.MEMBER));
        assertEquals(Role.MEMBER, Role.RECRUIT.promoted());
        assertEquals(Role.OFFICER, Role.OFFICER.promoted(), "nadie llega a líder ascendiendo");
        assertEquals(Role.RECRUIT, Role.RECRUIT.demoted());
    }
}
