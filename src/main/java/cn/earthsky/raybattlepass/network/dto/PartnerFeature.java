package cn.earthsky.raybattlepass.network.dto;

import java.util.List;

public class PartnerFeature {
    public List<Partner> partners;
    public List<PassSnapshot.RewardCard> partnerRewards;
    public List<PassSnapshot.StoryNode> storyNodes;

    public static class Partner {
        public String partnerId;
        public String portraitAssetId;
        public String skinAssetId;
    }
}
