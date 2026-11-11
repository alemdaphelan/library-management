package com.huit.library.modules.digital.service;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class DigitalFileFormatValidator {
    private static final int MAX_METADATA_BYTES = 2 * 1024 * 1024;
    private static final String EPUB_MEDIA_TYPE = "application/epub+zip";
    private static final String EPUB_CONTAINER_NS =
            "urn:oasis:names:tc:opendocument:xmlns:container";
    private static final String EPUB_PACKAGE_NS = "http://www.idpf.org/2007/opf";
    private static final String OFFICE_RELATIONSHIP_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String STRICT_OFFICE_RELATIONSHIP_NS =
            "http://purl.oclc.org/ooxml/officeDocument/relationships";
    private static final String PRESENTATION_NS =
            "http://schemas.openxmlformats.org/presentationml/2006/main";
    private static final String STRICT_PRESENTATION_NS =
            "http://purl.oclc.org/ooxml/presentationml/main";
    private static final String PRESENTATION_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml";

    private DigitalFileFormatValidator() {}

    static void validate(Path file, String extension) {
        boolean valid;
        try {
            valid =
                    switch (extension) {
                        case "pdf" -> isPdf(file);
                        case "epub" -> isEpub(file);
                        case "pptx" -> isPptx(file);
                        case "mp3" -> isMp3(file);
                        default -> false;
                    };
        } catch (IOException | OpenXML4JException | SAXException | RuntimeException exception) {
            throw unsupported(extension, exception);
        }
        if (!valid) {
            throw unsupported(extension, null);
        }
    }

    private static boolean isPdf(Path file) throws IOException {
        try (RandomAccessReadBufferedFile input = new RandomAccessReadBufferedFile(file.toFile())) {
            byte[] header = new byte[5];
            if (input.read(header) != header.length
                    || !"%PDF-".equals(new String(header, StandardCharsets.US_ASCII))) {
                return false;
            }
            input.seek(0);
            PDFParser parser =
                    new PDFParser(input, "", null, null, IOUtils.createTempFileOnlyStreamCache());
            try (PDDocument document = parser.parse(false)) {
                return document.getNumberOfPages() > 0
                        && document.getPage(0).getCOSObject() != null;
            } catch (InvalidPasswordException lockedPdf) {
                // The parser found an encrypted PDF that the reader can unlock with its password.
                return true;
            }
        }
    }

    private static boolean isEpub(Path file) throws IOException, SAXException {
        try (ZipFile archive = new ZipFile(file.toFile())) {
            ZipEntry mimetype = archive.getEntry("mimetype");
            if (mimetype == null || mimetype.getMethod() != ZipEntry.STORED) {
                return false;
            }
            try (InputStream input = archive.getInputStream(mimetype)) {
                byte[] content = input.readNBytes(EPUB_MEDIA_TYPE.length() + 1);
                if (!EPUB_MEDIA_TYPE.equals(new String(content, StandardCharsets.US_ASCII))) {
                    return false;
                }
            }
            Element container = readEntry(archive, "META-INF/container.xml");
            if (!hasName(container, EPUB_CONTAINER_NS, "container")) {
                return false;
            }
            Element rootfile =
                    child(
                            child(container, EPUB_CONTAINER_NS, "rootfiles"),
                            EPUB_CONTAINER_NS,
                            "rootfile");
            if (rootfile == null
                    || !"application/oebps-package+xml"
                            .equals(rootfile.getAttribute("media-type"))) {
                return false;
            }
            Element publication = readEntry(archive, rootfile.getAttribute("full-path"));
            return hasName(publication, EPUB_PACKAGE_NS, "package")
                    && child(publication, EPUB_PACKAGE_NS, "manifest") != null
                    && child(
                                    child(publication, EPUB_PACKAGE_NS, "spine"),
                                    EPUB_PACKAGE_NS,
                                    "itemref")
                            != null;
        }
    }

    private static boolean isPptx(Path file) throws IOException, OpenXML4JException, SAXException {
        try (ZipSecureFile zip = new ZipSecureFile(file.toFile())) {
            // OPCPackage parses these entries itself, so check their inflated sizes before opening
            // it.
            for (String name : new String[] {"[Content_Types].xml", "_rels/.rels"}) {
                var entry = zip.getEntry(name);
                if (entry == null || entry.isDirectory()) {
                    return false;
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    readMetadata(input);
                }
            }
        }
        try (OPCPackage archive = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            for (PackageRelationship relationship : archive.getRelationships()) {
                String type = relationship.getRelationshipType();
                if (relationship.getTargetMode() != TargetMode.INTERNAL
                        || !(type.equals(OFFICE_RELATIONSHIP_NS + "/officeDocument")
                                || type.equals(
                                        STRICT_OFFICE_RELATIONSHIP_NS + "/officeDocument"))) {
                    continue;
                }
                PackagePart presentation = archive.getPart(relationship);
                if (presentation == null
                        || !PRESENTATION_CONTENT_TYPE.equals(presentation.getContentType())) {
                    return false;
                }
                try (InputStream input = presentation.getInputStream()) {
                    Element root = readXml(input);
                    return hasName(root, PRESENTATION_NS, "presentation")
                            || hasName(root, STRICT_PRESENTATION_NS, "presentation");
                }
            }
            return false;
        }
    }

    private static boolean isMp3(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] header = input.readNBytes(2);
            return header.length == 2
                    && ((header[0] == 'I' && header[1] == 'D')
                            || ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0));
        }
    }

    private static Element readEntry(ZipFile archive, String name)
            throws IOException, SAXException {
        ZipEntry entry = archive.getEntry(name);
        if (entry == null || entry.isDirectory()) {
            return null;
        }
        try (InputStream input = archive.getInputStream(entry)) {
            return readXml(input);
        }
    }

    private static Element readXml(InputStream input) throws IOException, SAXException {
        byte[] bytes = readMetadata(input);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultNSInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        } catch (ParserConfigurationException exception) {
            throw new IllegalStateException("Cannot configure document metadata parser", exception);
        }
    }

    private static byte[] readMetadata(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_METADATA_BYTES + 1);
        if (bytes.length > MAX_METADATA_BYTES) {
            throw new IOException("Document metadata exceeds size limit");
        }
        return bytes;
    }

    private static Element child(Element parent, String namespace, String localName) {
        if (parent != null) {
            for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element element && hasName(element, namespace, localName)) {
                    return element;
                }
            }
        }
        return null;
    }

    private static boolean hasName(Element element, String namespace, String localName) {
        return element != null
                && namespace.equals(element.getNamespaceURI())
                && localName.equals(element.getLocalName());
    }

    private static ResponseStatusException unsupported(String extension, Throwable cause) {
        return new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Nội dung tệp không khớp với định dạng " + extension.toUpperCase(Locale.ROOT),
                cause);
    }
}
