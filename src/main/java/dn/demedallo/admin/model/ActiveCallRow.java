package dn.demedallo.admin.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class ActiveCallRow {

    private final StringProperty queue = new SimpleStringProperty();
    private final StringProperty campaign = new SimpleStringProperty();
    private final StringProperty number = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final StringProperty callId = new SimpleStringProperty();
    private final StringProperty trunk = new SimpleStringProperty();

    public StringProperty queueProperty() {
        return queue;
    }

    public void setQueue(String v) {
        queue.set(v);
    }

    public StringProperty campaignProperty() {
        return campaign;
    }

    public void setCampaign(String v) {
        campaign.set(v);
    }

    public StringProperty numberProperty() {
        return number;
    }

    public void setNumber(String v) {
        number.set(v);
    }

    public StringProperty statusProperty() {
        return status;
    }

    public void setStatus(String v) {
        status.set(v);
    }

    public StringProperty typeProperty() {
        return type;
    }

    public void setType(String v) {
        type.set(v);
    }

    public StringProperty callIdProperty() {
        return callId;
    }

    public void setCallId(String v) {
        callId.set(v);
    }

    public StringProperty trunkProperty() {
        return trunk;
    }

    public void setTrunk(String v) {
        trunk.set(v);
    }
}
