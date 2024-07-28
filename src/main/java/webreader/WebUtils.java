package webreader;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static webreader.Util.list;

class WebUtils {
    static final List<String> HASH_SUFFIX_URLS = list();

    static String convertSpecialCharacters(String url) {
        if (url == null) return null;
        if (url.startsWith("?")) return url;
        String delimiter = "?";
        for (String hashSuffixUrl : HASH_SUFFIX_URLS) {
            if (url.contains(hashSuffixUrl)) {
                delimiter = "#";
                break;
            }
        }
        if (url.contains(delimiter)) {
            int i = url.indexOf(delimiter);
            return convertSpecialCharactersInternal(url.substring(0, i)) + convertSuffix(url.substring(i));
        } else {
            return convertSpecialCharactersInternal(url);
        }
    }

    static String convertSuffix(String suffix) {
        suffix = suffix.replaceAll("\u00A0", "&nbsp;").replaceAll("\\.", "%2E").replaceAll("\\|", "%7C");
        if (!suffix.contains("WScontent")) {
            suffix = suffix.replaceAll(":", "%3A");
        }
        return suffix.replaceAll(" ", "%20");
    }

    static String convertSpecialCharactersInternal(String url) {
        return url.replaceAll("\\|", "%7C").replaceAll("=", "%3D").replaceAll("\\{", "%7B").replaceAll("\\}", "%7D").replaceAll(",", "%2C").replaceAll("\\&", "%26").replaceAll(" ", "%20");
    }

    static String hostProperNoWww(String host) {
        String s = hostProper(host);
        return s != null ? s.replace("www.", "") : null;
    }

    static String hostProper(String host) {
        try {
            return new URI(host).getHost();
        } catch (Exception ignored) {
        }
        return null;
    }

    static Map<String, String> hrefText(Element element) {
        if (element == null) return null;
        Map<String, String> hrefText = new HashMap<>();
        Elements aElements = element.getElementsByTag("a");
        if (aElements != null) {
            for (Element aElement : aElements) {
                if (aElement.attr("href") != null) {
                    hrefText.put(aElement.attr("href"), aElement.text());
                }
            }
        }
        hrefText.entrySet().removeIf(e -> e.getKey().equals("") || e.getKey().equals("/") || e.getKey().equals("/#") || e.getKey().equals("#"));
        return hrefText;
    }

    static <A, B, C> A safeA(Util.Multi3<A, B, C> multi) {
        if (multi == null) return null;
        return multi.a;
    }

    static <A, B, C> B safeB(Util.Multi3<A, B, C> multi) {
        if (multi == null) return null;
        return multi.b;
    }

    static <A, B, C> C safeC(Util.Multi3<A, B, C> multi) {
        if (multi == null) return null;
        return multi.c;
    }
}