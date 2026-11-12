package com.conversor.docxtxt;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extrai o texto de um arquivo .docx.
 * Um .docx e um ZIP; o texto fica em word/document.xml.
 */
public final class DocxReader {

    private DocxReader() {}

    public static String extractText(InputStream in) throws Exception {
        if (in == null) {
            throw new IOException("Não foi possível abrir o arquivo.");
        }
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return parse(zis);
                }
            }
        }
        throw new IOException("INVALID");
    }

    private static String parse(InputStream is) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        p.setInput(is, null);

        StringBuilder sb = new StringBuilder();
        boolean inText = false;
        boolean inTabs = false;
        int tableDepth = 0;

        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                switch (n) {
                    case "t":
                        inText = true;
                        break;
                    case "tabs":
                        inTabs = true;
                        break;
                    case "tab":
                        if (!inTabs) sb.append('\t');
                        break;
                    case "br":
                    case "cr":
                        sb.append('\n');
                        break;
                    case "noBreakHyphen":
                        sb.append('-');
                        break;
                    case "tbl":
                        tableDepth++;
                        break;
                    default:
                        break;
                }
            } else if (ev == XmlPullParser.TEXT) {
                if (inText) sb.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG) {
                String n = p.getName();
                switch (n) {
                    case "t":
                        inText = false;
                        break;
                    case "tabs":
                        inTabs = false;
                        break;
                    case "p":
                        if (tableDepth > 0) {
                            sb.append(' ');
                        } else {
                            sb.append('\n');
                        }
                        break;
                    case "tc":
                        trimEnd(sb, " ");
                        sb.append('\t');
                        break;
                    case "tr":
                        trimEnd(sb, "\t ");
                        sb.append('\n');
                        break;
                    case "tbl":
                        if (tableDepth > 0) tableDepth--;
                        break;
                    default:
                        break;
                }
            }
            ev = p.next();
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? "" : result + "\n";
    }

    private static void trimEnd(StringBuilder sb, String chars) {
        int len = sb.length();
        while (len > 0 && chars.indexOf(sb.charAt(len - 1)) >= 0) {
            len--;
        }
        sb.setLength(len);
    }
}
