package ru.quietclinic.contracts;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Node;

/** DOCX как ZIP с OOXML: переменные заменяются в w:t, остальная разметка сохраняется. */
final class DocxTemplate {
  private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-z][a-zA-Z0-9_.-]{0,79})}}");
  private static final int MAX_ENTRIES = 1000;
  private static final int MAX_XML_BYTES = 25 * 1024 * 1024;
  private static final int MAX_UNCOMPRESSED_BYTES = 50 * 1024 * 1024;

  private DocxTemplate() {}

  static Set<String> placeholders(byte[] docx) {
    return transform(docx, Map.of(), false);
  }

  static byte[] render(byte[] docx, Map<String, String> values) {
    try {
      var output = new ByteArrayOutputStream();
      transform(docx, values, true, output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw conflict("INVALID_DOCX", "Cannot read the DOCX template");
    }
  }

  private static Set<String> transform(byte[] docx, Map<String, String> values, boolean render) {
    try {
      return transform(docx, values, render, new ByteArrayOutputStream());
    } catch (IOException ex) {
      throw conflict("INVALID_DOCX", "Cannot read the DOCX template");
    }
  }

  private static Set<String> transform(
      byte[] docx, Map<String, String> values, boolean render, ByteArrayOutputStream output)
      throws IOException {
    if (docx == null || docx.length < 4 || docx[0] != 'P' || docx[1] != 'K') {
      throw conflict("INVALID_DOCX", "Only a valid DOCX file is accepted");
    }
    var found = new LinkedHashSet<String>();
    var hasDocument = false;
    var entries = 0;
    var uncompressedBytes = 0L;
    try (var input = new ZipInputStream(new ByteArrayInputStream(docx));
        var zip = new ZipOutputStream(output)) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        if (++entries > MAX_ENTRIES) throw conflict("INVALID_DOCX", "DOCX has too many entries");
        var name = entry.getName();
        var bytes = input.readNBytes(MAX_XML_BYTES + 1);
        if (bytes.length > MAX_XML_BYTES) throw conflict("INVALID_DOCX", "DOCX entry is too large");
        uncompressedBytes += bytes.length;
        if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
          throw conflict("INVALID_DOCX", "DOCX uncompressed content is too large");
        }
        if ("word/document.xml".equals(name)) hasDocument = true;
        if (isTextPart(name)) {
          var processed = processXml(bytes, values, render, found);
          bytes = processed;
        }
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
      }
    }
    if (!hasDocument) throw conflict("INVALID_DOCX", "DOCX main document is missing");
    return found;
  }

  private static boolean isTextPart(String name) {
    return "word/document.xml".equals(name)
        || "word/footnotes.xml".equals(name)
        || "word/endnotes.xml".equals(name)
        || "word/comments.xml".equals(name)
        || (name.startsWith("word/header") && name.endsWith(".xml"))
        || (name.startsWith("word/footer") && name.endsWith(".xml"));
  }

  private static byte[] processXml(
      byte[] bytes, Map<String, String> values, boolean render, Set<String> found) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
      var paragraphs = document.getElementsByTagNameNS("*", "p");
      for (var index = 0; index < paragraphs.getLength(); index++) {
        processParagraph(paragraphs.item(index), values, render, found);
      }
      var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      var output = new ByteArrayOutputStream();
      transformer.transform(new DOMSource(document), new StreamResult(output));
      return output.toByteArray();
    } catch (Exception ex) {
      if (ex instanceof IllegalStateException illegal) throw illegal;
      throw conflict("INVALID_DOCX", "Cannot process DOCX markup");
    }
  }

  private static void processParagraph(
      Node paragraph, Map<String, String> values, boolean render, Set<String> found) {
    var textNodes = new ArrayList<Node>();
    collectText(paragraph, textNodes);
    if (textNodes.isEmpty()) return;
    var combined = new StringBuilder();
    for (var node : textNodes) combined.append(node.getTextContent());
    var matcher = VARIABLE.matcher(combined.toString());
    var replacements = new ArrayList<Replacement>();
    while (matcher.find()) {
      var key = matcher.group(1);
      found.add(key);
      if (render) {
        var value = values.get(key);
        if (value == null) throw new IllegalStateException("Missing template value: " + key);
        replacements.add(new Replacement(matcher.start(), matcher.end(), value));
      }
    }
    for (var index = replacements.size() - 1; index >= 0; index--) {
      replace(textNodes, replacements.get(index));
    }
  }

  private static void collectText(Node node, ArrayList<Node> result) {
    if ("t".equals(node.getLocalName())) {
      result.add(node);
      return;
    }
    var children = node.getChildNodes();
    for (var index = 0; index < children.getLength(); index++)
      collectText(children.item(index), result);
  }

  /** Замена справа налево: не сдвигает offsets следующих переменных и сохраняет остальные run-ы. */
  private static void replace(ArrayList<Node> nodes, Replacement replacement) {
    var starts = new int[nodes.size()];
    var offset = 0;
    for (var index = 0; index < nodes.size(); index++) {
      starts[index] = offset;
      offset += nodes.get(index).getTextContent().length();
    }
    for (var index = 0; index < nodes.size(); index++) {
      var node = nodes.get(index);
      var text = node.getTextContent();
      var start = starts[index];
      var end = start + text.length();
      if (end <= replacement.start || start >= replacement.end) continue;
      var prefix = text.substring(0, Math.max(0, replacement.start - start));
      var suffix = text.substring(Math.min(text.length(), replacement.end - start));
      var value = start <= replacement.start ? replacement.value : "";
      node.setTextContent(prefix + value + suffix);
    }
  }

  private record Replacement(int start, int end, String value) {}
}
