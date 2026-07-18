package ru.example.vkchatauction;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuctionSerializationTest {

    private Auction createAuction(double startPrice, double buyItNow) {
        return new Auction(
                UUID.randomUUID(), UUID.randomUUID(), "Seller",
                new ItemStack(Material.DIAMOND),
                startPrice, buyItNow, 3600000
        );
    }

    @Test
    void auctionStatusInitialState() {
        Auction auction = createAuction(100, 0);
        assertEquals(Auction.Status.ACTIVE, auction.getStatus());
    }

    @Test
    void auctionStatusTransitions() {
        Auction auction = createAuction(50, 0);
        assertEquals(Auction.Status.ACTIVE, auction.getStatus());
        auction.setStatus(Auction.Status.COMPLETED);
        assertEquals(Auction.Status.COMPLETED, auction.getStatus());
        auction.setStatus(Auction.Status.CANCELLED);
        assertEquals(Auction.Status.CANCELLED, auction.getStatus());
    }

    @Test
    void auctionIsExpiredAfterDuration() {
        Auction auction = new Auction(
                UUID.randomUUID(), UUID.randomUUID(), "Seller",
                new ItemStack(Material.STONE),
                10, 0, -1000
        );
        assertTrue(auction.isExpired());
    }

    @Test
    void activeAuctionIsNotExpired() {
        Auction auction = createAuction(10, 0);
        assertFalse(auction.isExpired());
    }

    @Test
    void minimumBidStartsAtStartPrice() {
        Auction auction = createAuction(100, 0);
        assertEquals(100, auction.getMinimumBid(), 0.001);
    }

    @Test
    void minimumBidIncreasesAfterBid() {
        Auction auction = createAuction(100, 0);
        auction.placeBid(UUID.randomUUID(), "Bidder", 200);
        assertTrue(auction.getMinimumBid() > auction.getCurrentBid());
    }

    @Test
    void buyItNowFlag() {
        Auction withBin = createAuction(50, 200);
        Auction withoutBin = createAuction(50, 0);
        assertTrue(withBin.hasBuyItNow());
        assertFalse(withoutBin.hasBuyItNow());
    }

    @Test
    void bidHistoryTracksBids() {
        Auction auction = createAuction(100, 0);
        auction.placeBid(UUID.randomUUID(), "Bidder1", 150);
        auction.placeBid(UUID.randomUUID(), "Bidder2", 200);
        assertEquals(2, auction.getBidHistory().size());
        assertEquals(200, auction.getBidHistory().get(1).amount, 0.001);
    }

    @Test
    void highestBidderAfterBid() {
        Auction auction = createAuction(50, 0);
        UUID bidder = UUID.randomUUID();
        auction.placeBid(bidder, "Player", 75);
        assertEquals(bidder, auction.getHighestBidder());
        assertEquals("Player", auction.getHighestBidderName());
    }

    @Test
    void bidHistoryLimitedTo20() {
        Auction auction = createAuction(10, 0);
        for (int i = 0; i < 25; i++) {
            auction.placeBid(UUID.randomUUID(), "Bidder" + i, 10 + i);
        }
        assertTrue(auction.getBidHistory().size() <= 20);
    }

    @Test
    void noBidsInitially() {
        Auction auction = createAuction(100, 0);
        assertFalse(auction.hasBids());
        assertNull(auction.getHighestBidder());
        assertEquals(0, auction.getCurrentBid(), 0.001);
    }

    @Test
    void getTimeLeftMs() {
        Auction auction = createAuction(10, 0);
        assertTrue(auction.getTimeLeftMs() > 0);
    }
}
