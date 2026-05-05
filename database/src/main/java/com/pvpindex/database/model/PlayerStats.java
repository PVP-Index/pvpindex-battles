package com.pvpindex.database.model;

import java.util.UUID;

public record PlayerStats(UUID uuid, String modeId, int wins, int losses,
                           int kills, int deaths, int streak, int bestStreak, int elo) {}
