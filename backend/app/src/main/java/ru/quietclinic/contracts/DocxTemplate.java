package ru.quietclinic.contracts;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
      ByteArrayOutputStream output = new ByteArrayOutputStream();
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
    Set<String> found = new LinkedHashSet<>();
    boolean hasDocument = false;
    int entries = 0;
    long uncompressedBytes = 0L;
    try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(docx));
        ZipOutputStream zip = new ZipOutputStream(output)) {
      ZipEntry entry;
      while ((entry = input.getNextEntry()) != null) {
        if (++entries > MAX_ENTRIES) throw conflict("INVALID_DOCX", "DOCX has too many entries");
        String name = entry.getName();
        byte[] bytes = input.readNBytes(MAX_XML_BYTES + 1);
        if (bytes.length > MAX_XML_BYTES) throw conflict("INVALID_DOCX", "DOCX entry is too large");
        uncompressedBytes += bytes.length;
        if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
          throw conflict("INVALID_DOCX", "DOCX uncompressed content is too large");
        }
        if ("word/document.xml".equals(name)) hasDocument = true;
        if (isTextPart(name)) {
          byte[] processed = processXml(bytes, values, render, found);
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
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
      NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
      for (int index = 0; index < paragraphs.getLength(); index++) {
        processParagraph(paragraphs.item(index), values, render, found);
      }
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      transformer.transform(new DOMSource(document), new StreamResult(output));
      return output.toByteArray();
    } catch (Exception ex) {
      if (ex instanceof IllegalStateException illegal) throw illegal;
      throw conflict("INVALID_DOCX", "Cannot process DOCX markup");
    }
  }

  private static void processParagraph(
      Node paragraph, Map<String, String> values, boolean render, Set<String> found) {
    List<Node> textNodes = new ArrayList<>();
    collectText(paragraph, textNodes);
    if (textNodes.isEmpty()) return;
    StringBuilder combined = new StringBuilder();
    for (Node node : textNodes) combined.append(node.getTextContent());
    Matcher matcher = VARIABLE.matcher(combined.toString());
    List<Replacement> replacements = new ArrayList<>();
    while (matcher.find()) {
      String key = matcher.group(1);
      found.add(key);
      if (render) {
        String value = values.get(key);
        if (value == null) throw new IllegalStateException("Missing template value: " + key);
        replacements.add(new Replacement(matcher.start(), matcher.end(), value));
      }
    }
    for (int index = replacements.size() - 1; index >= 0; index--) {
      replace(textNodes, replacements.get(index));
    }
  }

  private static void collectText(Node node, List<Node> result) {
    if ("t".equals(node.getLocalName())) {
      result.add(node);
      return;
    }
    NodeList children = node.getChildNodes();
    for (int index = 0; index < children.getLength(); index++)
      collectText(children.item(index), result);
  }

  /** Замена справа налево: не сдвигает offsets следующих переменных и сохраняет остальные run-ы. */
  private static void replace(List<Node> nodes, Replacement replacement) {
    int[] starts = new int[nodes.size()];
    int offset = 0;
    for (int index = 0; index < nodes.size(); index++) {
      starts[index] = offset;
      offset += nodes.get(index).getTextContent().length();
    }
    for (int index = 0; index < nodes.size(); index++) {
      Node node = nodes.get(index);
      String text = node.getTextContent();
      int start = starts[index];
      int end = start + text.length();
      if (end <= replacement.start || start >= replacement.end) continue;
      String prefix = text.substring(0, Math.max(0, replacement.start - start));
      String suffix = text.substring(Math.min(text.length(), replacement.end - start));
      String value = start <= replacement.start ? replacement.value : "";
      node.setTextContent(prefix + value + suffix);
    }
  }

  private record Replacement(int start, int end, String value) {}
}
