package dn.demedallo.admin.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QueueStatusSnapshot {
    public int totalCalls;
    public int onQueue;
    public int success;
    public int onHold;
    public int abandoned;
    public int pending;
    public int placing;
    public int ringing;
    public final Map<String, Integer> agentStatusCounts = new HashMap<>();
    public final List<ActiveCallRow> activeCalls = new ArrayList<>();
}
