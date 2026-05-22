package dn.demedallo.admin.xml;

public final class XmlEscapes {

    private XmlEscapes() {}

    public static String text(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
