package dn.demedallo.admin.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class QueueMonitorRow {

    private final StringProperty queue = new SimpleStringProperty();
    private final StringProperty typeLabel = new SimpleStringProperty();
    private final StringProperty campaignName = new SimpleStringProperty();
    private final StringProperty statusLabel = new SimpleStringProperty();
    private final StringProperty callsToday = new SimpleStringProperty();
    private final StringProperty waiting = new SimpleStringProperty();
    private final StringProperty agentsSummary = new SimpleStringProperty();
    private final StringProperty callStates = new SimpleStringProperty();

    public String getQueue() {
        return queue.get();
    }

    public StringProperty queueProperty() {
        return queue;
    }

    public void setQueue(String v) {
        queue.set(v);
    }

    public String getTypeLabel() {
        return typeLabel.get();
    }

    public StringProperty typeLabelProperty() {
        return typeLabel;
    }

    public void setTypeLabel(String v) {
        typeLabel.set(v);
    }

    public String getCampaignName() {
        return campaignName.get();
    }

    public StringProperty campaignNameProperty() {
        return campaignName;
    }

    public void setCampaignName(String v) {
        campaignName.set(v);
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

    public String getCallsToday() {
        return callsToday.get();
    }

    public StringProperty callsTodayProperty() {
        return callsToday;
    }

    public void setCallsToday(String v) {
        callsToday.set(v);
    }

    public String getWaiting() {
        return waiting.get();
    }

    public StringProperty waitingProperty() {
        return waiting;
    }

    public void setWaiting(String v) {
        waiting.set(v);
    }

    public String getAgentsSummary() {
        return agentsSummary.get();
    }

    public StringProperty agentsSummaryProperty() {
        return agentsSummary;
    }

    public void setAgentsSummary(String v) {
        agentsSummary.set(v);
    }

    public String getCallStates() {
        return callStates.get();
    }

    public StringProperty callStatesProperty() {
        return callStates;
    }

    public void setCallStates(String v) {
        callStates.set(v);
    }
}
