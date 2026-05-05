package com.pvpindex.database;

import com.pvpindex.database.repository.BattleRepository;
import com.pvpindex.database.repository.PartyRepository;
import com.pvpindex.database.repository.PlayerRepository;
import com.pvpindex.database.repository.StatsRepository;

public interface DatabaseProvider {

    void connect() throws Exception;

    void disconnect();

    boolean isConnected();

    PlayerRepository playerRepository();

    BattleRepository battleRepository();

    StatsRepository statsRepository();

    PartyRepository partyRepository();

    String type();
}
