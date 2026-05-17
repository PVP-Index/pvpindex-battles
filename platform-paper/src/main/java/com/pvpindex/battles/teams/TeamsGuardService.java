package com.pvpindex.battles.teams;

import com.skyblockexp.teamsapi.api.TeamsAPI;
import com.skyblockexp.teamsapi.api.TeamsService;
import com.skyblockexp.teamsapi.model.Team;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional guard that prevents players on the same team from battling each
 * other.
 *
 * <p>Requires <a href="https://modrinth.com/plugin/teams-api">TeamsAPI</a>
 * to be installed on the server. If the plugin is absent or no team provider
 * is registered, the guard is transparently skipped so that challenges work
 * exactly as before.
 *
 * <p>Enabled by setting {@code teams_guard.block_same_team: true} in
 * {@code config.yml} (disabled by default).
 */
public final class TeamsGuardService {

    private final boolean enabled;
    private final Logger logger;

    public TeamsGuardService(boolean enabled, Logger logger) {
        this.enabled = enabled;
        this.logger = logger;
    }

    /**
     * Returns {@code true} when the two players share the same team AND
     * the guard is enabled AND a TeamsAPI provider is available.
     *
     * <p>Any unexpected error (missing class, provider exception, …) is caught
     * and the method returns {@code false} (fail-open) so that a misconfigured
     * or absent TeamsAPI never blocks legitimate challenges.
     *
     * @param playerA UUID of the first player
     * @param playerB UUID of the second player
     * @return {@code true} if the challenge should be blocked
     */
    public boolean isSameTeam(UUID playerA, UUID playerB) {
        if (!enabled) return false;
        try {
            if (!TeamsAPI.isAvailable()) return false;
            TeamsService service = TeamsAPI.getService();
            Optional<Team> teamA = service.getPlayerTeam(playerA);
            if (teamA.isEmpty()) return false;
            Optional<Team> teamB = service.getPlayerTeam(playerB);
            if (teamB.isEmpty()) return false;
            return teamA.get().getId().equals(teamB.get().getId());
        } catch (NoClassDefFoundError | Exception e) {
            logger.fine("[TeamsGuard] Could not query TeamsAPI: " + e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
