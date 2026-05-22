package dn.demedallo.admin.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a byte stream into top-level ECCP XML documents ({@code response} or {@code event}).
 * Same framing idea as the PHP ECCP client (one root element per packet).
 */
public final class EccpPacketFramer {

    /** Xerces escribe "[Fatal Error]..." en stderr en cada prefijo incompleto; al parsear en bucle hay que silenciarlo. */
    private static final Object PARSE_STDERR_LOCK = new Object();

    private final StringBuilder buffer = new StringBuilder();
    private final DocumentBuilder docBuilder;

    public EccpPacketFramer() throws ParserConfigurationException {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        f.setIgnoringComments(true);
        f.setCoalescing(true);
        DocumentBuilder db = f.newDocumentBuilder();
        db.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
            }

            @Override
            public void error(SAXParseException exception) {
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        this.docBuilder = db;
    }

    public void appendUtf8(byte[] chunk, int len) {
        buffer.append(new String(chunk, 0, len, StandardCharsets.UTF_8));
    }

    /** Clears buffered bytes after a dropped connection or transport reset. */
    public void clear() {
        buffer.setLength(0);
    }

    /**
     * @return complete XML documents removed from the internal buffer
     */
    public List<String> drainCompletePackets() throws IOException, SAXException {
        List<String> out = new ArrayList<>();
        int safety = 0;
        while (tryExtractOne(out)) {
            if (++safety > 256) {
                throw new IOException("too many ECCP packets in one drain");
            }
        }
        return out;
    }

    private boolean tryExtractOne(List<String> out) throws IOException, SAXException {
        skipLeadingIgnorable();
        if (buffer.isEmpty()) {
            return false;
        }
        int start = 0;
        while (start < buffer.length() && Character.isWhitespace(buffer.charAt(start))) {
            start++;
        }
        if (start > 0) {
            buffer.delete(0, start);
        }
        if (buffer.isEmpty()) {
            return false;
        }
        if (buffer.charAt(0) != '<') {
            throw new IOException("ECCP framing: expected '<', got: " + buffer.charAt(0));
        }

        int cut = findFirstCompleteTopLevelDocumentEnd();
        if (cut < 0) {
            return false;
        }
        String packet = buffer.substring(0, cut);
        buffer.delete(0, cut);
        out.add(packet);
        return true;
    }

    private void skipLeadingIgnorable() {
        while (true) {
            int ws = 0;
            while (ws < buffer.length() && Character.isWhitespace(buffer.charAt(ws))) {
                ws++;
            }
            if (ws > 0) {
                buffer.delete(0, ws);
            }
            if (buffer.length() >= 5 && buffer.substring(0, 5).equals("<?xml")) {
                int end = buffer.indexOf("?>");
                if (end < 0) {
                    return;
                }
                buffer.delete(0, end + 2);
                continue;
            }
            return;
        }
    }

    /**
     * Smallest prefix that parses as a single document whose root is {@code response} or {@code event}.
     */
    private int findFirstCompleteTopLevelDocumentEnd() throws IOException {
        String s = buffer.toString();
        int max = s.length();
        for (int end = 1; end <= max; end++) {
            String prefix = s.substring(0, end);
            if (!prefix.endsWith(">")) {
                continue;
            }
            try {
                Document d = parsePrefixQuietly(prefix);
                Element root = d.getDocumentElement();
                if (root == null) {
                    continue;
                }
                String tag = root.getNodeName();
                if (!"response".equals(tag) && !"event".equals(tag)) {
                    continue;
                }
                return end;
            } catch (SAXException ignored) {
                // incomplete or invalid prefix; extend
            }
        }
        return -1;
    }

    private Document parsePrefixQuietly(String prefix) throws IOException, SAXException {
        byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
        synchronized (PARSE_STDERR_LOCK) {
            PrintStream saved = System.err;
            try (PrintStream devNull = new PrintStream(OutputStream.nullOutputStream())) {
                System.setErr(devNull);
                return docBuilder.parse(new ByteArrayInputStream(bytes));
            } finally {
                System.setErr(saved);
            }
        }
    }
}
