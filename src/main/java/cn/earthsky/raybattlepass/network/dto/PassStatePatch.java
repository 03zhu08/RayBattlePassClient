package cn.earthsky.raybattlepass.network.dto;

public class PassStatePatch {
    public String patchType;
    public int level;
    public int exp;
    public int expToNextLevel;
    public String paidTier;
    public int claimableCount;
    public int overflowCount;

    public PassStatePatch() {}

    public PassStatePatch(String patchType) {
        this.patchType = patchType;
    }
}
