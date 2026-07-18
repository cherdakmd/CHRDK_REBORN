package ru.example.vkchat.util;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VKChatBridgeTest {

    @Mock
    Player player;

    @Test
    void hasPass_returnsTrue_whenPlayerHasPermission() {
        when(player.hasPermission("vkchat.pass")).thenReturn(true);
        assertTrue(VKChatBridge.hasPass(player));
    }

    @Test
    void hasPass_returnsFalse_whenPlayerIsNull() {
        assertFalse(VKChatBridge.hasPass(null));
    }

    @Test
    void hasPass_returnsFalse_whenPlayerHasNoPermission() {
        when(player.hasPermission("vkchat.pass")).thenReturn(false);
        assertFalse(VKChatBridge.hasPass(player));
    }

    @Test
    void hasVkOrPass_returnsFalse_whenPlayerIsNull() {
        assertFalse(VKChatBridge.hasVkOrPass(null));
    }

    @Test
    void getLocalReputation_returnsDefault_whenNoPdcData() {
        var pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(player.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.getOrDefault(any(), eq(org.bukkit.persistence.PersistentDataType.INTEGER), eq(0)))
                .thenReturn(0);
        assertEquals(0, VKChatBridge.getLocalReputation(player));
    }

    @Test
    void getLinkedVkId_returnsMinusOne_whenPlayerIsNull() {
        assertEquals(-1, VKChatBridge.getLinkedVkId((Player) null));
    }
}
