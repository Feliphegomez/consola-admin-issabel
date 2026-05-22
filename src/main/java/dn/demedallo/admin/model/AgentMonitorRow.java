package dn.demedallo.admin.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class AgentMonitorRow {

    private final StringProperty agentNumber = new SimpleStringProperty();
    private final StringProperty agentName = new SimpleStringProperty();
    private final StringProperty statusLabel = new SimpleStringProperty();
    private final StringProperty statusCode = new SimpleStringProperty();
    private final StringProperty extension = new SimpleStringProperty();
    private final StringProperty channel = new SimpleStringProperty();
    private final StringProperty queues = new SimpleStringProperty();
    private final StringProperty callsToday = new SimpleStringProperty();
    private final StringProperty talkTimeToday = new SimpleStringProperty();
    private final StringProperty callsBreakdown = new SimpleStringProperty();
    private final StringProperty loginTime = new SimpleStringProperty();
    private final StringProperty lastSession = new SimpleStringProperty();
    private final StringProperty phoneNumber = new SimpleStringProperty();
    private final StringProperty activeQueue = new SimpleStringProperty();
    private final StringProperty callType = new SimpleStringProperty();
    private final StringProperty callId = new SimpleStringProperty();
    private final StringProperty callStatus = new SimpleStringProperty();
    private final StringProperty trunk = new SimpleStringProperty();
    private final StringProperty pauseInfo = new SimpleStringProperty();
    private final StringProperty pauseSince = new SimpleStringProperty();

    /** Agent extension used for ChanSpy / SPAGE (not shown in table). */
    private String spyExtension = "";
    private boolean listenAvailable;

    public String getAgentNumber() {
        return agentNumber.get();
    }

    public StringProperty agentNumberProperty() {
        return agentNumber;
    }

    public void setAgentNumber(String v) {
        agentNumber.set(v);
    }

    public String getAgentName() {
        return agentName.get();
    }

    public StringProperty agentNameProperty() {
        return agentName;
    }

    public void setAgentName(String v) {
        agentName.set(v);
    }

    public String getStatusLabel() {
        return statusLabel.get();
    }

    public StringProperty statusLabelProperty() {
        return statusLabel;
    }

    public void setStatusLabel(String v) {
        statusLabel.set(v);
    }

    public String getStatusCode() {
        return statusCode.get();
    }

    public void setStatusCode(String v) {
        statusCode.set(v);
    }

    public StringProperty extensionProperty() {
        return extension;
    }

    public void setExtension(String v) {
        extension.set(v);
    }

    public StringProperty channelProperty() {
        return channel;
    }

    public void setChannel(String v) {
        channel.set(v);
    }

    public String getQueues() {
        return queues.get();
    }

    public StringProperty queuesProperty() {
        return queues;
    }

    public void setQueues(String v) {
        queues.set(v);
    }

    public StringProperty callsTodayProperty() {
        return callsToday;
    }

    public void setCallsToday(String v) {
        callsToday.set(v);
    }

    public StringProperty talkTimeTodayProperty() {
        return talkTimeToday;
    }

    public void setTalkTimeToday(String v) {
        talkTimeToday.set(v);
    }

    public StringProperty callsBreakdownProperty() {
        return callsBreakdown;
    }

    public void setCallsBreakdown(String v) {
        callsBreakdown.set(v);
    }

    public StringProperty loginTimeProperty() {
        return loginTime;
    }

    public void setLoginTime(String v) {
        loginTime.set(v);
    }

    public StringProperty lastSessionProperty() {
        return lastSession;
    }

    public void setLastSession(String v) {
        lastSession.set(v);
    }

    public StringProperty phoneNumberProperty() {
        return phoneNumber;
    }

    public void setPhoneNumber(String v) {
        phoneNumber.set(v);
    }

    public StringProperty activeQueueProperty() {
        return activeQueue;
    }

    public void setActiveQueue(String v) {
        activeQueue.set(v);
    }

    public StringProperty callTypeProperty() {
        return callType;
    }

    public void setCallType(String v) {
        callType.set(v);
    }

    public StringProperty callIdProperty() {
        return callId;
    }

    public void setCallId(String v) {
        callId.set(v);
    }

    public StringProperty callStatusProperty() {
        return callStatus;
    }

    public void setCallStatus(String v) {
        callStatus.set(v);
    }

    public StringProperty trunkProperty() {
        return trunk;
    }

    public void setTrunk(String v) {
        trunk.set(v);
    }

    public StringProperty pauseInfoProperty() {
        return pauseInfo;
    }

    public void setPauseInfo(String v) {
        pauseInfo.set(v);
    }

    public StringProperty pauseSinceProperty() {
        return pauseSince;
    }

    public void setPauseSince(String v) {
        pauseSince.set(v);
    }

    public String getSpyExtension() {
        return spyExtension;
    }

    public void setSpyExtension(String spyExtension) {
        this.spyExtension = spyExtension == null ? "" : spyExtension;
    }

    public boolean isListenAvailable() {
        return listenAvailable;
    }

    public void setListenAvailable(boolean listenAvailable) {
        this.listenAvailable = listenAvailable;
    }
}
