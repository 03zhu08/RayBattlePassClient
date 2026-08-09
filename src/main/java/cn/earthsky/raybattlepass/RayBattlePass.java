package cn.earthsky.raybattlepass;

import cn.earthsky.raybattlepass.network.PassPacketHandler;
import cn.earthsky.raybattlepass.network.dto.PassSnapshot;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = RayBattlePass.MOD_ID, version = RayBattlePass.VERSION,
     clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.13)")
public class RayBattlePass {

    public static final String MOD_ID = "raybattlepass";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MOD_ID)
    public static RayBattlePass instance;

    private PassPacketHandler packetHandler;
    private PassSnapshot cachedSnapshot;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        packetHandler = new PassPacketHandler();
        packetHandler.registerChannels();
    }

    public PassPacketHandler getPacketHandler() {
        return packetHandler;
    }

    public PassSnapshot getCachedSnapshot() {
        return cachedSnapshot;
    }

    public void updateSnapshot(PassSnapshot snapshot) {
        this.cachedSnapshot = snapshot;
    }
}
