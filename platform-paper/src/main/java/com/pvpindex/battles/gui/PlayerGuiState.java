package com.pvpindex.battles.gui;

/**
 * Per-player GUI state tracking. Determines behaviour when a mode item is
 * clicked in the battle GUI.
 */
public final class PlayerGuiState {

	public enum Mode {
		QUEUE,
		CHALLENGE
	}

	private Mode mode;
	private String challengeTarget;
	private int page;

	public PlayerGuiState(Mode mode, String challengeTarget, int page) {
		this.mode = mode;
		this.challengeTarget = challengeTarget;
		this.page = page;
	}

	public Mode mode() { return mode; }
	public void setMode(Mode mode) { this.mode = mode; }

	public String challengeTarget() { return challengeTarget; }
	public void setChallengeTarget(String target) { this.challengeTarget = target; }

	public int page() { return page; }
	public void setPage(int page) { this.page = page; }
}
