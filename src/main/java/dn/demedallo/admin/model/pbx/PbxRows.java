package dn.demedallo.admin.model.pbx;

/** Row models for Issabel/FreePBX {@code asterisk} database listings. */
public final class PbxRows {

    private PbxRows() {
    }

    public record TimeGroupRow(int id, String description, int slotCount) {
    }

    public record TimeGroupDetailRow(int id, int timeGroupId, String timeRule, String name) {
    }

    public record TimeConditionRow(int id, String displayName, int timeGroupId,
                                   String timeGroupLabel, String trueGoto, String falseGoto,
                                   String deptName) {
    }

    public record QueueRow(String extension, String description, String destination,
                           String maxWait, String callbackId) {
    }

    public record ExtensionRow(String extension, String name, String tech, String dial,
                               String voicemail) {
    }

    public record TrunkRow(int trunkId, String name, String tech, String channelId,
                           String outCid, String disabled, String provider) {
    }

    public record InboundRouteRow(String did, String cidNum, String destination,
                                  String description, String mohClass) {
    }

    public record OutboundRouteRow(int routeId, String name, String outCid,
                                   String emergencyRoute, int timeGroupId, String timeGroupLabel,
                                   String patterns, String trunks) {
    }

    public record IvrDetailRow(int id, String name, String description, int announcement,
                               String directDial, int timeoutTime, String timeoutDestination,
                               String invalidDestination, int entryCount) {
    }

    public record IvrEntryRow(int ivrId, String selection, String dest, int ivrRet) {
    }

    public record MohRow(String category, String type, int random, String application, String format) {
    }

    public record AnnouncementRow(int id, String description, int recordingId,
                                  String recordingLabel, String postDest, String repeatMsg,
                                  int returnIvr) {
    }

    public record RecordingRow(int id, String displayName, String filename) {
    }
}
