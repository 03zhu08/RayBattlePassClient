package cn.earthsky.raybattlepass.network.dto;

public class ClaimResult {
    public boolean success;
    public String rewardId;
    public String errorCode;
    public String errorMessage;
    public PassStatePatch statePatch;
}
