package cn.earthsky.raybattlepass.network;

public class PassPacketHeader {
    public int protocolVersion;
    public String messageType;
    public String requestId;
    public String seasonId;
    public String playerIdHash;
    public long timestamp;
    public int sequence;
    public int totalSequence;
    public boolean compressed;
    public String checksum;
}
