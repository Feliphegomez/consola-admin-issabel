package dn.demedallo.admin.ami;

public final class AdminMonitorOriginate {

    public static final String MODE_DIALPLAN = "DIALPLAN";
    /** @deprecated use {@link #MODE_DIALPLAN} */
    public static final String MODE_SPAGE = MODE_DIALPLAN;
    public static final String MODE_CHANSPY = "CHANSPY";

    public String mode = MODE_DIALPLAN;
    public String callerIdNum = "";
    public String context = "from-internal";
    public String extension = "";
    public String chanSpyData = "";
}
