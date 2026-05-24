package dn.demedallo.admin.model.pbx;

/** Input forms for PBX create/update (no delete). */
public final class PbxForms {

    private PbxForms() {
    }

    public record TimeGroupForm(Integer id, String description) {
    }

    public record TimeGroupDetailForm(Integer id, int timeGroupId, String timeRule, String name) {
    }

    public record TimeConditionForm(Integer id, String displayName, int timeGroupId,
                                    String trueGoto, String falseGoto, String deptName) {
    }

    public record QueueForm(boolean create, String extension, String description,
                            String destination, String maxWait, String callbackId) {
    }

    public record ExtensionForm(boolean create, String extension, String name, String tech,
                                String dial, String voicemail) {
    }

    public record TrunkForm(boolean create, int trunkId, String name, String tech, String channelId,
                            String outCid, String disabled, String provider) {
    }

    public record InboundRouteForm(boolean create, String did, String cidNum, String destination,
                                   String description, String mohClass) {
    }

    public record OutboundRouteForm(boolean create, Integer routeId, String name, String outCid,
                                    String emergencyRoute, Integer timeGroupId,
                                    String patternsText, String trunkIdsText) {
    }

    public record IvrDetailForm(Integer id, String name, String description, Integer announcement,
                                String directDial, Integer timeoutTime, String timeoutDestination,
                                String invalidDestination) {
    }

    public record IvrEntryForm(boolean create, int ivrId, String selection, String originalSelection,
                               String dest, int ivrRet) {
    }

    public record MohForm(boolean create, String category, String type, int random,
                          String application, String format) {
    }

    public record AnnouncementForm(Integer id, String description, Integer recordingId,
                                   String postDest, String repeatMsg, int returnIvr) {
    }
}
