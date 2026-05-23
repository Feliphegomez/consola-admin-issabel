package dn.demedallo.admin.branding;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Marca blanca: textos y logo desde {@code branding.xml} (externo o recurso por defecto).
 */
public final class BrandingSupport {

    public static final String PROPERTY_BRANDING_FILE = "dn.demedallo.admin.branding.file";
    private static final String DEFAULT_RESOURCE = "/dn/demedallo/admin/branding-default.xml";
    private static final String EXTERNAL_FILENAME = "branding.xml";
    private static final String APP_DIR_NAME = "demedallo-admin-console";
    private static final String DEFAULT_LOGO_FILENAME = "logo.png";

    /** Ampersand que no inicia entidad XML conocida (evita fallo del parser en textos de empresa/tagline). */
    private static final Pattern BARE_AMPERSAND =
            Pattern.compile("&(?!(?:amp|lt|gt|apos|quot);|#[0-9]+;|#x[0-9a-fA-F]+;)");

    public record Branding(String company, String title, String tagline, String logoUrl) {
        public Branding {
            company = company == null ? "" : company;
            title = title == null ? "" : title;
            tagline = tagline == null ? "" : tagline;
            logoUrl = logoUrl == null || logoUrl.isBlank() ? null : logoUrl;
        }
    }

    private static volatile Branding cached;

    private BrandingSupport() {
    }

    public static Branding get() {
        if (cached == null) {
            synchronized (BrandingSupport.class) {
                if (cached == null) {
                    cached = load();
                }
            }
        }
        return cached;
    }

    /**
     * Barra superior: logo + empresa / título / subtítulo. Estilos: {@code .branding-bar}, etc.
     */
    public static Region createHeaderBar() {
        Branding b = get();
        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 14, 8, 14));
        bar.getStyleClass().add("branding-bar");

        if (b.logoUrl != null) {
            ImageView logo = new ImageView();
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
            logo.setFitHeight(52);
            logo.setFitWidth(200);
            try {
                Image img = new Image(b.logoUrl, true);
                logo.setImage(img);
                img.errorProperty().addListener((o, oldE, err) -> {
                    if (Boolean.TRUE.equals(err)) {
                        logo.setImage(null);
                    }
                });
            } catch (RuntimeException ignored) {
                logo.setImage(null);
            }
            logo.imageProperty().addListener((o, oldI, n) -> {
                logo.setManaged(n != null);
                logo.setVisible(n != null);
            });
            bar.getChildren().add(logo);
        }

        Label lCompany = new Label(b.company.isEmpty() ? " " : b.company);
        lCompany.getStyleClass().add("branding-company");
        lCompany.setWrapText(true);

        Label lTitle = new Label(b.title.isEmpty() ? " " : b.title);
        lTitle.getStyleClass().add("branding-title");
        lTitle.setWrapText(true);

        Label lTag = new Label(b.tagline.isEmpty() ? " " : b.tagline);
        lTag.getStyleClass().add("branding-tagline");
        lTag.setWrapText(true);

        VBox texts = new VBox(2, lCompany, lTitle, lTag);
        texts.setFillWidth(true);
        HBox.setHgrow(texts, Priority.ALWAYS);
        bar.getChildren().add(texts);
        return bar;
    }

    private static Path userDir() {
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    private static Path appDataDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, APP_DIR_NAME).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), "." + APP_DIR_NAME).toAbsolutePath().normalize();
    }

    private static Branding load() {
        ensureDefaultBrandingFilesIfMissing();
        Path xmlFile = findBrandingXmlFile();
        Path logoBaseEmbedded = userDir();
        if (xmlFile != null) {
            try {
                byte[] raw = Files.readAllBytes(xmlFile);
                if (isBinaryImageOrZip(raw)) {
                    return parseBrandingFromEmbeddedResource(logoBaseEmbedded);
                }
                String text = decodeXmlFileText(raw);
                if (!looksLikeXml(text)) {
                    return parseBrandingFromEmbeddedResource(logoBaseEmbedded);
                }
                String normalized = fixBareAmpersands(text);
                Path logoBase = xmlFile.getParent().toAbsolutePath().normalize();
                return parseBrandingDocument(
                        new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8)), logoBase);
            } catch (Exception e) {
                return parseBrandingFromEmbeddedResource(logoBaseEmbedded);
            }
        }
        return parseBrandingFromEmbeddedResource(logoBaseEmbedded);
    }

    /**
     * Creates {@code branding.xml} + {@code logo.png} in the app data directory if missing.
     * Does not overwrite user-provided files.
     */
    private static void ensureDefaultBrandingFilesIfMissing() {
        // If user explicitly points to a branding file, do not auto-create anything.
        String prop = System.getProperty(PROPERTY_BRANDING_FILE);
        if (prop != null && !prop.isBlank()) {
            return;
        }
        Path dir = appDataDir();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            return;
        }

        Path xml = dir.resolve(EXTERNAL_FILENAME);
        if (!Files.isRegularFile(xml)) {
            try (InputStream in = BrandingSupport.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                if (in != null) {
                    Path tmp = dir.resolve(EXTERNAL_FILENAME + ".tmp");
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp, xml, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (Exception ignored) {
            }
        }

        Path logo = dir.resolve(DEFAULT_LOGO_FILENAME);
        if (!Files.isRegularFile(logo)) {
            // Prefer a packaged default logo if present, otherwise generate a simple placeholder.
            boolean copied = false;
            try (InputStream in = BrandingSupport.class.getResourceAsStream("/dn/demedallo/admin/" + DEFAULT_LOGO_FILENAME)) {
                if (in != null) {
                    Path tmp = dir.resolve(DEFAULT_LOGO_FILENAME + ".tmp");
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp, logo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    copied = true;
                }
            } catch (Exception ignored) {
            }
            if (!copied) {
                try {
                    byte[] png = generateDefaultLogoPngBytes();
                    if (png != null && png.length > 0) {
                        Path tmp = dir.resolve(DEFAULT_LOGO_FILENAME + ".tmp");
                        Files.write(tmp, png);
                        Files.move(tmp, logo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static byte[] generateDefaultLogoPngBytes() {
        int w = 560;
        int h = 170;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);

            // Accent circle
            g.setColor(new Color(29, 78, 216));
            g.fillOval(18, 30, 96, 96);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 40));
            g.drawString("dM", 33, 95);

            // Text
            g.setColor(new Color(15, 23, 42));
            g.setFont(new Font("Segoe UI", Font.BOLD, 44));
            g.drawString("deMedallo.com", 130, 92);
            g.setColor(new Color(100, 116, 139));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            g.drawString("Consola CallCenter PBX", 132, 124);

            ByteArrayOutputStream out = new ByteArrayOutputStream(16_384);
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            g.dispose();
        }
    }

    private static Branding parseBrandingFromEmbeddedResource(Path logoBaseForRelativeLogo) {
        try (InputStream in = BrandingSupport.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) {
                return defaults();
            }
            return parseBrandingDocument(in, logoBaseForRelativeLogo);
        } catch (Exception e) {
            return defaults();
        }
    }

    private static Branding parseBrandingDocument(InputStream in, Path logoBase) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        DocumentBuilder db = f.newDocumentBuilder();
        db.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) {
                // no stderr desde el parser
            }

            @Override
            public void error(SAXParseException exception) {
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        Document doc = db.parse(new InputSource(in));
        Element root = doc.getDocumentElement();
        if (root == null) {
            return defaults();
        }
        String company = textChild(root, "company");
        String title = textChild(root, "title");
        String tagline = textChild(root, "tagline");
        String logoPath = attrLogo(root);
        String logoUrl = resolveLogoUrl(logoPath, logoBase);
        if (logoUrl == null) {
            logoUrl = autoDetectLogoUrl(logoBase);
        }
        return new Branding(
                company.isBlank() ? "Su empresa" : company,
                title.isBlank() ? "Consola administración" : title,
                tagline,
                logoUrl);
    }

    private static boolean isBinaryImageOrZip(byte[] raw) {
        if (raw.length < 4) {
            return false;
        }
        if (raw[0] == (byte) 0x89 && raw[1] == 'P' && raw[2] == 'N' && raw[3] == 'G') {
            return true;
        }
        if ((raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xD8) {
            return true;
        }
        if (raw[0] == 'G' && raw[1] == 'I' && raw[2] == 'F') {
            return true;
        }
        if (raw[0] == 'P' && raw[1] == 'K') {
            return true;
        }
        return false;
    }

    private static String decodeXmlFileText(byte[] raw) {
        if (raw.length == 0) {
            return "";
        }
        if (raw.length >= 2) {
            int b0 = raw[0] & 0xFF;
            int b1 = raw[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) {
                return new String(raw, StandardCharsets.UTF_16LE);
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return new String(raw, StandardCharsets.UTF_16BE);
            }
        }
        if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
            return new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8);
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static boolean looksLikeXml(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String t = s.stripLeading();
        return t.startsWith("<?xml") || t.startsWith("<branding");
    }

    private static String fixBareAmpersands(String xml) {
        return BARE_AMPERSAND.matcher(xml).replaceAll("&amp;");
    }

    /** Ruta del XML externo, o {@code null} si se usa el recurso embebido. */
    private static Path findBrandingXmlFile() {
        String prop = System.getProperty(PROPERTY_BRANDING_FILE);
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop.trim()).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path appData = appDataDir();
        Path appDataXml = appData.resolve(EXTERNAL_FILENAME);
        if (Files.isRegularFile(appDataXml)) {
            return appDataXml.toAbsolutePath().normalize();
        }
        Path external = cwd.resolve(EXTERNAL_FILENAME);
        if (Files.isRegularFile(external)) {
            return external.toAbsolutePath().normalize();
        }
        return null;
    }

    private static String autoDetectLogoUrl(Path logoBaseDir) {
        if (logoBaseDir == null) {
            return null;
        }
        for (String fn : List.of("logo.png", "logo.jpg", "logo.jpeg", "logo.webp", "logo.gif", "logo.bmp")) {
            Path p = logoBaseDir.resolve(fn).normalize();
            if (Files.isRegularFile(p)) {
                return p.toUri().toString();
            }
        }
        return null;
    }

    private static String resolveLogoUrl(String pathAttr, Path logoBaseDir) {
        if (pathAttr == null || pathAttr.isBlank()) {
            return null;
        }
        String p = pathAttr.trim();
        if (p.startsWith("classpath:")) {
            String cp = p.substring("classpath:".length()).replaceFirst("^/+", "");
            var url = BrandingSupport.class.getResource("/" + cp);
            return url == null ? null : url.toExternalForm();
        }
        Path logo = Path.of(p);
        if (logo.isAbsolute()) {
            if (Files.isRegularFile(logo)) {
                return logo.toUri().toString();
            }
            return null;
        }
        Path resolved = logoBaseDir.resolve(p).normalize();
        if (Files.isRegularFile(resolved)) {
            return resolved.toUri().toString();
        }
        return null;
    }

    private static String attrLogo(Element root) {
        var nl = root.getElementsByTagName("logo");
        if (nl.getLength() == 0) {
            return null;
        }
        Element logo = (Element) nl.item(0);
        return logo.getAttribute("path");
    }

    private static String textChild(Element parent, String tag) {
        var nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return "";
        }
        String t = nl.item(0).getTextContent();
        return t == null ? "" : t.trim();
    }

    private static Branding defaults() {
        return new Branding("Su empresa S.A.", "Consola administración", "ECCP / Issabel", null);
    }
}
