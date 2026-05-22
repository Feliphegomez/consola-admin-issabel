package dn.demedallo.admin.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Md5Util {

    private Md5Util() {}

    public static String md5Hex(String input) {
        try {
            MessageDigest d = MessageDigest.getInstance("MD5");
            byte[] h = d.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean looksLikeMd5Hex32(String s) {
        return s != null && s.length() == 32 && s.matches("^[0-9a-fA-F]{32}$");
    }
}
