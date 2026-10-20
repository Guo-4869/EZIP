package com.ezip.app.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pulls readable text out of Office Open XML documents (docx / pptx / xlsx).
 * They are zip containers, so we read the XML parts and strip the markup - good
 * enough for a quick in-app preview without shipping a whole Office renderer.
 */
public final class Ooxml {

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private Ooxml() {
    }

    public static String extractText(File file, int maxChars) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".docx")) return docx(file, maxChars);
        if (name.endsWith(".pptx")) return pptx(file, maxChars);
        if (name.endsWith(".xlsx")) return xlsx(file, maxChars);
        throw new IOException("不支持的文档类型");
    }

    private static String docx(File file, int maxChars) throws IOException {
        String xml = readEntry(file, "word/document.xml", 12 * 1024 * 1024);
        if (xml == null) throw new IOException("文档内容为空");
        xml = xml.replace("</w:p>", "\n").replace("<w:tab/>", "\t").replace("<w:br/>", "\n");
        return cleanup(xml, maxChars);
    }

    private static String pptx(File file, int maxChars) throws IOException {
        List<String> slides = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String n = e.getName();
                if (n.matches("ppt/slides/slide[0-9]+\\.xml")) slides.add(n);
            }
            Collections.sort(slides, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return slideNumber(a) - slideNumber(b);
                }
            });
            StringBuilder sb = new StringBuilder();
            int index = 0;
            for (String slide : slides) {
                index++;
                String xml = readEntry(zip, slide, 8 * 1024 * 1024);
                if (xml == null) continue;
                xml = xml.replace("</a:p>", "\n");
                String text = cleanup(xml, maxChars);
                if (text.isEmpty()) continue;
                sb.append("— 第 ").append(index).append(" 页 —\n").append(text).append("\n\n");
                if (sb.length() > maxChars) break;
            }
            return sb.toString();
        }
    }

    private static String xlsx(File file, int maxChars) throws IOException {
        String xml = readEntry(file, "xl/sharedStrings.xml", 8 * 1024 * 1024);
        if (xml == null) return "（该表格没有共享字符串，暂不支持预览）";
        // one line per <si> element
        xml = xml.replace("</si>", "\n");
        return cleanup(xml, maxChars);
    }

    private static int slideNumber(String path) {
        Matcher m = Pattern.compile("slide([0-9]+)\\.xml").matcher(path);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String readEntry(File file, String entryName, int maxBytes) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            return readEntry(zip, entryName, maxBytes);
        }
    }

    private static String readEntry(ZipFile zip, String entryName, int maxBytes) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int read;
            int total = 0;
            while ((read = in.read(buf)) > 0) {
                int keep = Math.min(read, maxBytes - total);
                if (keep <= 0) break;
                out.write(buf, 0, keep);
                total += keep;
            }
            return out.toString("UTF-8");
        }
    }

    private static String cleanup(String xml, int maxChars) {
        String text = TAG.matcher(xml).replaceAll("");
        text = text.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
        // collapse runs of blank lines
        text = text.replace("\r", "");
        StringBuilder sb = new StringBuilder(text.length());
        int blank = 0;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blank++;
                if (blank > 1) continue;
            } else {
                blank = 0;
            }
            sb.append(trimmed).append('\n');
            if (sb.length() > maxChars) {
                sb.append("\n…（内容过长，已截断）");
                break;
            }
        }
        return sb.toString().trim();
    }
}
