package cn.earthsky.raybattlepass.network.dto;

import java.util.List;

public class ClaimBatchResult {
    public boolean success;
    public List<String> claimedRewardIds;
    public List<String> failedRewardIds;
    public String errorCode;
    public String errorMessage;
    public PassStatePatch statePatch;
}
