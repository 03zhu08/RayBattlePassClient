package cn.earthsky.raybattlepass.network.dto;

import java.util.List;

public class EntitlementResult {
    public boolean success;
    public String paidTier;
    public int retroClaimableCount;
    public List<String> retroRewardIds;
    public String errorCode;
    public String errorMessage;
    public PassStatePatch statePatch;
}
