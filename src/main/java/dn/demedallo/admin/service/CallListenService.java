package dn.demedallo.admin.service;

import dn.demedallo.admin.ami.AdminMonitorOriginate;
import dn.demedallo.admin.ami.AmiClient;
import dn.demedallo.admin.util.AdminMonitorSettings;
import dn.demedallo.admin.util.AppLogFile;
import dn.demedallo.admin.util.SpyDialUtil;
import dn.demedallo.admin.util.SpyTargetUtil;

/**
 * Starts supervisor call listening via Issabel AMI (feature code dialplan or ChanSpy).
 */
public final class CallListenService {

    private static final int AMI_TIMEOUT_MS = 15000;

    private final AdminMonitorSettings settings;

    public CallListenService(AdminMonitorSettings settings) {
        this.settings = settings;
    }

    public String manualDialHint(String spyExtension) {
        String dial = SpyDialUtil.buildDialExtension(settings.spyPrefix, spyExtension);
        return "Marque desde su extensión " + settings.supervisorExtension + ": " + dial;
    }

    public void startListen(String spyExtension, String agentLabel, String agentChannel) throws ListenException {
        String hint = manualDialHint(spyExtension == null ? "" : spyExtension);

        if (spyExtension == null || spyExtension.isBlank()) {
            throw new ListenException(ListenException.Reason.NO_SPY_TARGET,
                    "No hay extensión del agente para escuchar.", hint);
        }
        if (settings.supervisorExtension == null || settings.supervisorExtension.isBlank()) {
            throw new ListenException(ListenException.Reason.NO_SUPERVISOR_EXT,
                    "Configure su extensión de supervisor en el login.", hint);
        }
        if (!settings.amiEnabled) {
            throw new ListenException(ListenException.Reason.AMI_DISABLED,
                    "Active «Escucha automática vía AMI» en el login e indique usuario/clave de manager.conf.",
                    hint);
        }
        if (settings.amiUser == null || settings.amiUser.isBlank()
                || settings.amiSecret == null || settings.amiSecret.isBlank()) {
            throw new ListenException(ListenException.Reason.AMI_NOT_CONFIGURED,
                    "Configure usuario y clave AMI en el login (permiso originate en manager.conf).",
                    hint);
        }

        String supervisorTech = resolveSupervisorTech(agentChannel);
        String supervisorChannel = SpyTargetUtil.channelName(supervisorTech, settings.supervisorExtension);
        String tech = resolveAgentTech(agentChannel);
        String dialExtension = SpyDialUtil.buildDialExtension(settings.spyPrefix, spyExtension);

        AdminMonitorOriginate orig = new AdminMonitorOriginate();
        orig.callerIdNum = settings.supervisorExtension.trim();
        orig.context = settings.dialContext == null ? "from-internal" : settings.dialContext;

        if (AdminMonitorSettings.MODE_CHANSPY.equalsIgnoreCase(settings.listenMode)) {
            orig.mode = AdminMonitorOriginate.MODE_CHANSPY;
            orig.chanSpyData = tech + "/" + spyExtension.trim() + "-,qW";
        } else {
            orig.mode = AdminMonitorOriginate.MODE_DIALPLAN;
            orig.extension = dialExtension;
        }

        AppLogFile.appendLine("[listen] agent=" + agentLabel + " dial=" + dialExtension
                + " supervisor=" + supervisorChannel + " mode=" + orig.mode);

        try (AmiClient ami = new AmiClient()) {
            ami.connect(settings.amiHost, settings.amiPort, AMI_TIMEOUT_MS,
                    settings.amiUser, settings.amiSecret);
            ami.originateListen(supervisorChannel, orig);
        } catch (Exception ex) {
            AppLogFile.appendLine("[listen] AMI error: " + ex.getMessage());
            throw new ListenException(ListenException.Reason.AMI_ERROR,
                    ex.getMessage(), hint, ex);
        }
    }

    private String resolveSupervisorTech(String agentChannel) {
        String fromAgent = SpyTargetUtil.techFromChannel(agentChannel);
        if (!fromAgent.isBlank()) {
            return fromAgent;
        }
        return settings.channelTech == null || settings.channelTech.isBlank()
                ? "PJSIP" : settings.channelTech.trim();
    }

    private String resolveAgentTech(String agentChannel) {
        String fromAgent = SpyTargetUtil.techFromChannel(agentChannel);
        if (!fromAgent.isBlank()) {
            return fromAgent;
        }
        return settings.channelTech == null || settings.channelTech.isBlank()
                ? "PJSIP" : settings.channelTech.trim();
    }
}
