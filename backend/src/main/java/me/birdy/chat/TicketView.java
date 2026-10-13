package me.birdy.chat;

import java.time.Instant;

public record TicketView(String ticket, Instant expiresAt) {
}
