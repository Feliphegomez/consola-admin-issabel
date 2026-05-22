package dn.demedallo.admin.service;

import dn.demedallo.admin.model.ActiveCallRow;
import dn.demedallo.admin.model.AgentMonitorRow;
import dn.demedallo.admin.model.QueueMonitorRow;

import java.util.List;

public final class MonitorSnapshot {

    private final List<AgentMonitorRow> agents;
    private final List<QueueMonitorRow> queues;
    private final List<ActiveCallRow> activeCalls;

    public MonitorSnapshot(List<AgentMonitorRow> agents, List<QueueMonitorRow> queues,
                           List<ActiveCallRow> activeCalls) {
        this.agents = agents;
        this.queues = queues;
        this.activeCalls = activeCalls;
    }

    public List<AgentMonitorRow> agents() {
        return agents;
    }

    public List<QueueMonitorRow> queues() {
        return queues;
    }

    public List<ActiveCallRow> activeCalls() {
        return activeCalls;
    }
}
