package dn.demedallo.admin.service;

/**
 * Call listen failure with optional manual dial fallback (SPAGE / ChanSpy).
 */
public final class ListenException extends Exception {

    public enum Reason {
        AMI_DISABLED,
        AMI_NOT_CONFIGURED,
        NO_SPY_TARGET,
        NO_SUPERVISOR_EXT,
        AMI_ERROR
    }

    private final Reason reason;
    private final String manualDialHint;

    public ListenException(Reason reason, String message, String manualDialHint) {
        super(message);
        this.reason = reason;
        this.manualDialHint = manualDialHint;
    }

    public ListenException(Reason reason, String message, String manualDialHint, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.manualDialHint = manualDialHint;
    }

    public Reason getReason() {
        return reason;
    }

    public String getManualDialHint() {
        return manualDialHint;
    }
}
