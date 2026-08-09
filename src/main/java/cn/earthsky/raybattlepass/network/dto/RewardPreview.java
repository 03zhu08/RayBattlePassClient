package cn.earthsky.raybattlepass.network.dto;

import java.util.List;

public class RewardPreview {
    public String rewardId;
    public String displayName;
    public String description;
    public String iconAssetId;
    public String previewAssetId;
    public String track;
    public int level;
    public String itemType;
    public int amount;
    public String rarity;
    public List<String> clientTags;
    public boolean claimable;
    public boolean claimed;
}
