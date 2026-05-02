package com.pvpindex.battles.battle;

import com.pvpindex.battles.battle.type.ParticipantResult;
import java.util.UUID;

public class BattleParticipant {
    private final UUID uuid;
    private final String minecraftUsername;
    private String team;
    private ParticipantResult result = ParticipantResult.UNKNOWN;
    private int kills;
    private int deaths;
    private double damageDealt;
    private double damageTaken;
    private double healingDone;
    private Integer eloBefore;
    private Integer eloAfter;
    private Integer eloChange;

    public BattleParticipant(UUID uuid, String minecraftUsername, String team) {
        this.uuid = uuid;
        this.minecraftUsername = minecraftUsername;
        this.team = team;
    }

    public UUID getUuid() { return uuid; }
    public String getMinecraftUsername() { return minecraftUsername; }
    public String getTeam() { return team; }
    public ParticipantResult getResult() { return result; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public double getDamageDealt() { return damageDealt; }
    public double getDamageTaken() { return damageTaken; }
    public double getHealingDone() { return healingDone; }
    public Integer getEloBefore() { return eloBefore; }
    public Integer getEloAfter() { return eloAfter; }
    public Integer getEloChange() { return eloChange; }

    public void setTeam(String team) { this.team = team; }
    public void setResult(ParticipantResult result) { this.result = result; }
    public void addKill() { this.kills++; }
    public void addDeath() { this.deaths++; }
    public void addDamageDealt(double amount) { this.damageDealt += amount; }
    public void addDamageTaken(double amount) { this.damageTaken += amount; }
    public void addHealingDone(double amount) { this.healingDone += amount; }

    public void setElo(Integer before, Integer after) {
        this.eloBefore = before;
        this.eloAfter = after;
        if (before != null && after != null) {
            this.eloChange = after - before;
        }
    }
}
