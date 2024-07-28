package webreader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static webreader.Util.*;

class WebReader {

    final static List<String> failedSimpleUrlCalls = list();
    static boolean DISABLE_READ_CACHE = false, DISABLE_WRITE_CACHE = false, DISABLE_MEMORY_CACHES = true;
    static BooleanMutable READ_CACHE_ENABLED = () -> !DISABLE_READ_CACHE;     //these are purely for readability
    static BooleanMutable WRITE_CACHE_ENABLED = () -> !DISABLE_WRITE_CACHE;
    static BooleanMutable MEMORY_CACHES_ENABLED = () -> !DISABLE_MEMORY_CACHES;
    static boolean TEXT_CACHE_ENABLED = true;

    static void setCaches(String root, String slash) {
        WEBCACHE = root;
        SLASH = slash;
        PHANTOM_TEMP = WEBCACHE + "phantomtemp" + slash + "output.txt";
        GOOGLE_TEMP = WEBCACHE + "googletemp" + slash + "output.txt";
        CACHE_FOLDER = WEBCACHE + "cached" + slash;
        CACHE_FOLDER_PHANTOM = WEBCACHE + "cached_phantom" + slash;
        CACHE_FOLDER_GOOGLE = WEBCACHE + "cached_google" + slash;
        CACHE_FOLDER_CURL = WEBCACHE + "cached" + slash + "curl" + slash;
        CACHE_FOLDER_HEADLESS = WEBCACHE + "cached" + slash + "headless" + slash;
        MAPPING_FILE_CURL = CACHE_FOLDER + "mapping_curl.txt";
        MAPPING_FILE_GOOGLE = CACHE_FOLDER + "mapping_google.txt";
        MAPPING_FILE_SIMPLE_READ = CACHE_FOLDER + "simple_mapping.txt";
        REDIRECTION_FILE_CURL = CACHE_FOLDER + "redirection_curl.txt";
        REDIRECTION_FILE_SIMPLE_READ = CACHE_FOLDER + "redirection_simple.txt";
        REDIRECTION_FILE_GOOGLE = CACHE_FOLDER + "redirection_google.txt";
        TEXT_MAPPING_FILE = CACHE_FOLDER + "text_mapping.txt";
        loadCaches();
    }

    static String WEBCACHE, SLASH, PHANTOM_TEMP, GOOGLE_TEMP, CACHE_FOLDER, CACHE_FOLDER_PHANTOM, CACHE_FOLDER_GOOGLE, CACHE_FOLDER_CURL, CACHE_FOLDER_HEADLESS, MAPPING_FILE_CURL, MAPPING_FILE_GOOGLE, MAPPING_FILE_SIMPLE_READ, MAPPING_FILE_HEADLESS, REDIRECTION_FILE_CURL, REDIRECTION_FILE_SIMPLE_READ, REDIRECTION_FILE_GOOGLE, TEXT_MAPPING_FILE;
    static Map<String, String> MEMORY_URL_FILE_CURL = null;
    static Map<String, String> MEMORY_URL_FILE_GOOGLE = null;
    static Map<String, String> MEMORY_URL_FILE_SIMPLE_READ = null;
    static Map<String, String> MEMORY_REDIRECTION_SIMPLE = null;
    static Map<String, String> MEMORY_REDIRECTION_CURL = null;
    static Map<String, String> MEMORY_REDIRECTION_GOOGLE = null;
    static boolean OUTPUT_THROTTLES = true, OUTPUT_CACHE_LOCATIONS = false;
    static Map<String, Multi3<Document, String, String>> MEMORY_CACHE_SIMPLE = new HashMap<>(), MEMORY_CACHE_CURL = new HashMap<>();
    static String PHANTOM_JAVASCRIPT = "phantom.js", PHANTOM_JAVASCRIPT_DELAY = "phantomDelay.js";
    static String PHANTOM_JAVASCRIPT_SCROLL = "phantomScroll.js", PHANTOM_JAVASCRIPT_CLICK_WAIT = "phantomClick.js";
    static String PHANTOM_EXECUTABLE = "phantomjs";
    static long THROTTLE_LAST_NULL_HOST = 0L;
    static Map<String, Long> THROTTLE_LAST_MAP = new HashMap<>();
    static long THROTTLE_VALUE = 1000L;

    static class Advanced {
        static Multi3<Document, String, String> readParseFromNetworkCurl(String url) {
            Multi3<Document, String, String> multi = readParseCurl(url, false);
            return multi == null ? new Util.Multi3<>(null, null, null) : multi;
        }
    }

    static String writeCache(String url, String read, String redirection) {
        if (WRITE_CACHE_ENABLED.check()) {
            String fileName = WebReader.CACHE_FOLDER + getTimestamp() + ".html";
            write(fileName, read);
            if (OUTPUT_CACHE_LOCATIONS) sout(url + "\n" + fileName);
            MEMORY_URL_FILE_SIMPLE_READ.put(url, fileName);
            MEMORY_REDIRECTION_SIMPLE.put(url, redirection);
            appendSafe(MAPPING_FILE_SIMPLE_READ, url + DELIMITER + fileName);
            appendSafe(REDIRECTION_FILE_SIMPLE_READ, url + DELIMITER + redirection);
            return fileName;
        }
        return null;
    }

    static Util.Multi3<String, String, String> read(String url) {     // --------------- SIMPLE READ and NO PARSING -----------------
        synchronized (url) {
            try {
                if (READ_CACHE_ENABLED.check()) {
                    String cachedLocation = cachedLocation(url);
                    String redirection = cachedRedirection(url);
                    if (cachedLocation != null && redirection != null) {
                        if (OUTPUT_CACHE_LOCATIONS) sout(url + "\n" + cachedLocation);
                        String read = Util.readString(cachedLocation);
                        return new Util.Multi3<>(read, redirection, cachedLocation);
                    }
                }
                List<String> redirectedUrls = list();
                String read = readInternal(url, false, null, redirectedUrls);
                String redirection = redirectedUrls.size() == 0 ? url : redirectedUrls.get(redirectedUrls.size() - 1);
                String fileLocation = writeCache(url, read, redirection);
                if (read == null) return new Util.Multi3<>(null, null, null);
                return new Util.Multi3<>(read, redirection, fileLocation);
            } catch (Exception e) {
                return new Util.Multi3<>(null, null, null);
            }
        }
    }

    static String readInternal(String url, boolean preserveNewLines, Charset charset, List<String> redirectedUrls) throws IOException {
        String host;
        try {
            String url_clean = WebUtils.convertSpecialCharacters(url);
            host = new URI(url_clean).getHost();
        } catch (URISyntaxException e) {
            host = "SYNCHRONIZED_NULL";
        }
        synchronized (host) {
            applyThrottle(host);
            return readInternalPostThrottle(url, preserveNewLines, charset, redirectedUrls);
        }
    }

    static String readInternalPostThrottle(String url, boolean preserveNewLines, Charset charset, List<String> redirectedUrls) throws IOException {
        URL obj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36");
        conn.setReadTimeout(10000);
        if (conn instanceof HttpsURLConnection && (url.contains("wienerlibrary") || url.contains("the-print-room"))) {
            ((HttpsURLConnection) conn).setHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            });
        }
        boolean redirect = false;
        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) redirect = true;
        }
        if (redirect) {
            String newUrl = conn.getHeaderField("Location");
            if (redirectedUrls == null) redirectedUrls = list();
            redirectedUrls.add(url);
            if (redirectedUrls.size() > 10) {
                sout("TOO MANY REDIRECTS : ");
                for (String redirected_url : redirectedUrls) {
                    sout(redirected_url);
                }
                return null;
            }
            return readInternalPostThrottle(newUrl, preserveNewLines, charset, redirectedUrls);
        } else {
            String read = read(conn, preserveNewLines, charset);
            if (read != null && read.contains("charset=iso-8859-1") && charset == null) {
                read = readInternalPostThrottle(url, preserveNewLines, StandardCharsets.ISO_8859_1, redirectedUrls);
            }
            return read;
        }
    }

    static Util.Multi3<Document, String, String> readParse(String url) {     // --------------- SIMPLE READ and PARSING  ----------
        if (url == null) return new Util.Multi3<>(null, null, null);
        synchronized (url) {
            try {
                String url_clean = WebUtils.convertSpecialCharacters(url);
                URI uri = null;
                try {
                    uri = new URI(url_clean);
                    url = uri.toString();
                } catch (URISyntaxException e) {
                    url = url_clean;
                }
                if (MEMORY_CACHES_ENABLED.check()) {
                    if (MEMORY_CACHE_SIMPLE.containsKey(url)) {
                        return MEMORY_CACHE_SIMPLE.get(url);
                    }
                }
                if (READ_CACHE_ENABLED.check()) {
                    String cached_location = cachedLocation(url);
                    String redirection = cachedRedirection(url);
                    if (cached_location != null && redirection != null) {
                        if (OUTPUT_CACHE_LOCATIONS) sout(url + "\n" + cached_location);
                        String html = Util.readString(cached_location);
                        if (html != null) {
                            Document parse = Jsoup.parse(html);
                            if (parse == null) return new Util.Multi3<>(null, null, null);
                            Util.Multi3<Document, String, String> parse_url = new Util.Multi3<>(parse, redirection, cached_location);
                            if (MEMORY_CACHES_ENABLED.check()) {
                                MEMORY_CACHE_SIMPLE.put(url, parse_url);
                            }
                            return parse_url;
                        }
                    }
                }
                Util.Multi3<Document, String, String> parse_url = simple(url);
                if (parse_url == null) return new Util.Multi3<>(null, null, null);
                if (MEMORY_CACHES_ENABLED.check()) {
                    MEMORY_CACHE_SIMPLE.put(url, parse_url);
                }
                String file_name__cache = WebReader.CACHE_FOLDER + getTimestamp() + ".html";
                String file_name__text = WebReader.CACHE_FOLDER + getTimestamp() + "_text.html";
                if (WRITE_CACHE_ENABLED.check() && TEXT_CACHE_ENABLED && parse_url.a != null) {
                    Util.write(file_name__text, parse_url.a.text());
                    Util.append(TEXT_MAPPING_FILE, url + DELIMITER + file_name__cache);
                }
                return parse_url;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    static void clearMemoryCaches() {
        MEMORY_CACHE_SIMPLE.clear();
    }

    static Multi3<Document, String, String> readParseCurl(String url, boolean useReadCacheDirectinvocation) {
        try {
            String url_clean = WebUtils.convertSpecialCharacters(url);
            URI uri;
            try {
                uri = new URI(url_clean);
                url = uri.toString();
            } catch (URISyntaxException e) {
                url = url_clean;
            }
            if (MEMORY_CACHES_ENABLED.check()) {
                if (MEMORY_CACHE_CURL.containsKey(url)) {
                    return MEMORY_CACHE_CURL.get(url);
                }
            }
            if (READ_CACHE_ENABLED.check() && useReadCacheDirectinvocation) {
                String cachedLocation = cachedLocationCurl(url);
                if (cachedLocation != null) {
                    String html = Util.readString(cachedLocation);
                    boolean nonstandardFormatLikely = new File(cachedLocation).exists() && new File(cachedLocation).length() > 0;
                    if (html != null || nonstandardFormatLikely) {
                        Document parse = html != null ? Jsoup.parse(html) : null;
                        Util.Multi3<Document, String, String> parseRedirection = new Util.Multi3<>(parse, url, cachedLocation);
                        if (MEMORY_CACHES_ENABLED.check()) {
                            MEMORY_CACHE_CURL.put(url, parseRedirection);
                        }
                        return parseRedirection;
                    }
                }
            }
            Util.Multi3<Document, String, String> parse = curl(url);
            if (parse == null || parse.a == null) return new Util.Multi3<>(null, null, null);
            if (MEMORY_CACHES_ENABLED.check()) {
                MEMORY_CACHE_CURL.put(url, parse);
            }
            return parse;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static String read(HttpURLConnection conn, boolean preserveNewLines, Charset charset) throws IOException {
        sout("downloading " + conn.getURL());
        BufferedReader in;
        if (charset == null) {
            in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(conn.getInputStream(), charset));
        }
        String inputLine;
        StringBuffer html = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            html.append(inputLine);
            if (preserveNewLines) html.append("\n");
        }
        in.close();
        if (preserveNewLines && html.length() > 0) {
            return html.substring(0, html.length() - 1);
        } else {
            return html.toString();
        }
    }

    static void applyThrottle(String host) {
        boolean nullHost = host == null;
        Long lastThrottleLocal = THROTTLE_LAST_MAP.get(host);
        if (nullHost) {
            lastThrottleLocal = THROTTLE_LAST_NULL_HOST;
        }
        if (lastThrottleLocal == null) lastThrottleLocal = 0L;
        long currentTime = System.currentTimeMillis();
        long difference = currentTime - lastThrottleLocal;
        if (OUTPUT_THROTTLES) {
            sout(host + ": Throttle identified, difference = " + difference);
        }
        if (nullHost) {
            THROTTLE_LAST_NULL_HOST = currentTime;
        } else {
            THROTTLE_LAST_MAP.put(host, currentTime);
        }
        if (difference < THROTTLE_VALUE) {
            if (OUTPUT_THROTTLES) {
                sout(host + ": Throttle Sleeping for difference " + difference);
            }
            try {
                Thread.sleep(difference);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized static String cachedRedirection(String url) {
        if (MEMORY_REDIRECTION_SIMPLE == null) {
            MEMORY_REDIRECTION_SIMPLE = Util.readMapSafe(REDIRECTION_FILE_SIMPLE_READ, DELIMITER);
        }
        return MEMORY_REDIRECTION_SIMPLE.get(url);
    }

    synchronized static String cachedRedirectionCurl(String url) {
        if (MEMORY_REDIRECTION_CURL == null) {
            MEMORY_REDIRECTION_CURL = Util.readMapSafe(REDIRECTION_FILE_CURL, DELIMITER);
        }
        return MEMORY_REDIRECTION_CURL.get(url);
    }

    synchronized static String cachedRedirectionGoogle(String url) {
        if (MEMORY_REDIRECTION_GOOGLE == null) {
            MEMORY_REDIRECTION_GOOGLE = Util.readMapSafe(REDIRECTION_FILE_GOOGLE, DELIMITER);
        }
        return MEMORY_REDIRECTION_GOOGLE.get(url);
    }

    synchronized static String cachedLocation(String url) {
        if (MEMORY_URL_FILE_SIMPLE_READ == null) {
            MEMORY_URL_FILE_SIMPLE_READ = Util.readMapSafe(MAPPING_FILE_SIMPLE_READ, DELIMITER);
        }
        return MEMORY_URL_FILE_SIMPLE_READ.get(url);
    }


    synchronized static String cachedLocationGoogle(String url) {
        if (MEMORY_URL_FILE_GOOGLE == null) {
            MEMORY_URL_FILE_GOOGLE = Util.readMapSafe(MAPPING_FILE_GOOGLE, DELIMITER);
        }
        return MEMORY_URL_FILE_GOOGLE.get(url);
    }


    synchronized static String cachedLocationCurl(String url) {
        if (MEMORY_URL_FILE_CURL == null) {
            MEMORY_URL_FILE_CURL = Util.readMapSafe(MAPPING_FILE_CURL, DELIMITER);
        }
        return MEMORY_URL_FILE_CURL.get(url);
    }

    static final String DELIMITER = "#ARCTA#";

    interface BooleanMutable {
        boolean check();
    }

    static void loadCaches() {
        cachedLocationCurl("");
        cachedLocationGoogle("");
        cachedLocation("");
        cachedRedirection("");
        cachedRedirectionGoogle("");
        cachedRedirectionCurl("");
    }

    protected static Util.Multi3<Document, String, String> simple(String url) {
        if (failedSimpleUrlCalls.contains(url)) return new Util.Multi3<>(null, null, null);
        Util.Multi3<String, String, String> readRedirect = read(url);
        String read = readRedirect.a;
        if (read == null || read.equals("")) {
            failedSimpleUrlCalls.add(url);
            return new Util.Multi3<>(null, null, null);
        }
        Document document = Jsoup.parse(read);
        if (document == null) {
            return new Util.Multi3<>(null, null, null);
        }
        return new Util.Multi3<>(document, readRedirect.b, readRedirect.c);
    }

    static Util.Multi3<Document, String, String> curl(String url) {
        String host;
        try {
            String url_clean = WebUtils.convertSpecialCharacters(url);
            host = new URI(url_clean).getHost();
        } catch (URISyntaxException e) {
            host = "SYNCHRONIZED_NULL";
        }
        applyThrottle(host);
        String fileName = CACHE_FOLDER_CURL + getTimestamp() + ".html";
        String[] command = {"curl", "-k", "-L", url, "--output", fileName};
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        try {
            Process start = pb.start();
            start.waitFor();
            if (OUTPUT_CACHE_LOCATIONS) sout(url + "\n" + fileName);
            if (WRITE_CACHE_ENABLED.check()) {
                appendSafe(MAPPING_FILE_CURL, url + DELIMITER + fileName);
            } //just the mapping file as we always write file for curl
            MEMORY_URL_FILE_CURL.put(url, fileName);
            List<String> readCurl = Util.read(fileName);
            if (readCurl == null) return null;
            StringBuilder sb = new StringBuilder();
            for (String s : readCurl) {
                sb.append(s + "\n");
            }
            String read = sb.length() > 0 ? sb.substring(0, sb.length() - 1) : "";
            Document document = Jsoup.parse(read);
            if (document == null) return new Util.Multi3<>(null, null, null);
            return new Util.Multi3<>(document, url, fileName);
        } catch (Exception e) {
            e.printStackTrace();
            return new Util.Multi3<>(null, null, null);
        }
    }

    static String getTimestamp() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String name = Thread.currentThread().getName();
        return "_" + System.currentTimeMillis() + "_" + name.charAt(name.length() - 1);
    }

    static Util.Multi<Document, String> phantomjs(String url, String phantomRoot, boolean delay) {
        String phantomJavascript = delay ? PHANTOM_JAVASCRIPT_DELAY : PHANTOM_JAVASCRIPT;
        return phantomjsInternal(url, phantomRoot + phantomJavascript, phantomRoot, null);
    }

    static Util.Multi<Document, String> googleChrome(String url, String googleHeadless, boolean readCache) {
        if (READ_CACHE_ENABLED.check() && readCache) {
            String cachedLocation = cachedLocationGoogle(url);
            if (cachedLocation != null) {
                String html = Util.readString(cachedLocation);
                if (!Util.empty(html)) {
                    Document parse = html != null ? Jsoup.parse(html) : null;
                    return new Util.Multi<>(parse, cachedLocation);
                }
            }
        }

        File file = new File(GOOGLE_TEMP);
        if (file.exists()) {
            file.delete();
        }
        write(GOOGLE_TEMP, "");
        sout("GOOGLE " + url);
        new DownloadRunnable(url, googleHeadless).run();
        sout("GOOGLE COMPLETE for " + url);
        String newFileName = CACHE_FOLDER_GOOGLE + "google" + "_" + getTimestamp() + ".txt";
        if (file.exists()) {
            try {
                java.nio.file.Files.copy(file.toPath(), new File(newFileName).toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (WRITE_CACHE_ENABLED.check()) {
            appendSafe(MAPPING_FILE_GOOGLE, url + DELIMITER + newFileName);
        }
        MEMORY_URL_FILE_GOOGLE.put(url, newFileName);
        sout(newFileName);
        String html = Util.readString(newFileName);
        if (html == null || html.length() == 0) return null;



        return new Util.Multi<>(Jsoup.parse(html), newFileName);
    }

    static Util.Multi<Document, String> phantomClickWait(String url, String phantomRoot, String selector) {
        return phantomjsInternal(url, phantomRoot + PHANTOM_JAVASCRIPT_CLICK_WAIT, phantomRoot, selector);
    }

    static Util.Multi<Document, String> phantomScroll(String url, String phantomRoot) {
        return phantomjsInternal(url, phantomRoot + PHANTOM_JAVASCRIPT_SCROLL, phantomRoot, null);
    }

    static Util.Multi<Document, String> phantomjsInternal(String url, String javascript, String phantomRoot, String arg3) {
        File file = new File(PHANTOM_TEMP);
        if (file.exists()) {
            file.delete();
        }
        Util.write(PHANTOM_TEMP, "");
        sout("PHANTOM " + url);
        new PhantomRunnable(url, javascript, phantomRoot, arg3).run();
        sout("PHANTOM COMPLETE for " + url);
        String new_file_name = CACHE_FOLDER_PHANTOM + "phantom" + "_" + getTimestamp() + ".txt";
        if (file.exists()) {
            try {
                java.nio.file.Files.copy(file.toPath(), new File(new_file_name).toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        sout(new_file_name);
        String html = Util.readString(new_file_name);
        if (html == null || html.length() == 0) return null;
        return new Util.Multi<>(Jsoup.parse(html), new_file_name);
    }

    static class PhantomRunnable implements Runnable {
        Process process = null;
        String url;
        String arg3;
        final String javascript;
        final String phantomRoot;

        PhantomRunnable(String url, String javascript, String phantomRoot, String arg3) {
            this.url = url;
            this.javascript = javascript;
            this.phantomRoot = phantomRoot;
            this.arg3 = arg3;
        }

        public void run() {
            try {
                ArrayList<String> list = list();
                String phantomExecutable = PHANTOM_EXECUTABLE;
                String javascriptlocal;
                if ("\\".equals(SLASH)) {
                    phantomExecutable = "\"" + phantomRoot + phantomExecutable + ".exe\"";
                    javascriptlocal = "\"" + javascript + "\"";
                } else {
                    phantomExecutable = phantomRoot + phantomExecutable;
                    javascriptlocal = javascript;
                }
                list.add(phantomExecutable);
                list.add(javascriptlocal);
                if ("\\".equals(SLASH)) {
                    list.add("\"" + url + "\"");
                } else {
                    list.add(url);
                    list.add(PHANTOM_TEMP);
                }
                if (!Util.empty(arg3)) {
                    list.add(arg3);
                }
                process = new ProcessBuilder(list)
                        .directory(new File("/Users/tom/phantomjs/bin"))
                        .inheritIO()
                        .start();
                process.waitFor(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
    }

    static class DownloadRunnable implements Runnable {
        Process process = null;
        String url;
        final String googleHeadless;

        DownloadRunnable(String url, String googleHeadless) {
            this.url = url;
            this.googleHeadless = googleHeadless;
        }

        public void run() {
            try {
                process = new ProcessBuilder(list(googleHeadless, url, GOOGLE_TEMP))
                        .inheritIO()
                        .start();
                process.waitFor(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
    }

    static class Download {
        static Util.Multi3<Document, String, String> document(Context ctx, String url, BaseDirs dirs, boolean delay, boolean useCache) {
            return document(ctx, url, null, dirs, delay, useCache);
        }

        static Document documentOnly(Context ctx, String url, BaseDirs dirs, boolean delay, boolean useCache) {
            return safeA(documentRedirect(ctx, url, dirs, delay, useCache));
        }

        static Util.Multi<Document, String> documentRedirect(Context ctx, String url, BaseDirs dirs, boolean delay, boolean useCache) {
            Util.Multi3<Document, String, String> multi = document(ctx, url, dirs, delay, useCache);
            return new Util.Multi<>(safeA(multi), safeB(multi));
        }

        static Util.Multi3<Document, String, String> document(Context ctx, String url, String clickElement, BaseDirs dirs, boolean delay, boolean useCache) {
            Util.Multi3<Document, String, String> multi;
            if (ctx.downloadCurl()) {
                multi = Advanced.readParseFromNetworkCurl(url);
                if (WebUtils.safeA(multi) == null) {
                    sout("CURL Download Failed for url " + url);
                }
            } else if (ctx.downloadPhantom()) {
                Util.Multi<Document, String> documentLocation = WebReader.phantomjs(url, dirs.getPhantomDir(), delay);
                multi = new Util.Multi3<>(Util.Multi.safeA(documentLocation), url, Util.Multi.safeB(documentLocation));
            } else {
                String googleScript = dirs.getGoogleScript(ctx);
                Util.Multi<Document, String> docLocation = googleChrome(url, googleScript, useCache);
                multi = new Util.Multi3<>(Util.Multi.safeA(docLocation), url, Util.Multi.safeB(docLocation));
            }
            return new Util.Multi3<>(WebUtils.safeA(multi), WebUtils.safeB(multi), WebUtils.safeC(multi));
        }

        static Util.Multi<Document, String> phantomjs(String link, Context ctx, BaseDirs dirs, boolean delay) {
            String clickWaitSelector = ctx.downloadClickWait();
            if (ctx.downloadScroll()) {
                return phantomScroll(link, dirs);
            } else if (!empty(clickWaitSelector)) {
                return phantomClickWait(link, dirs, clickWaitSelector);
            } else {
                return phantomjs(link, delay, dirs);
            }
        }

        static Util.Multi<Document, String> phantomjs(String link, boolean delay, BaseDirs dirs) {
            Util.Multi<Document, String> documentLocation = WebReader.phantomjs(link, dirs.getPhantomDir(), delay);
            return new Util.Multi<>(Util.Multi.safeA(documentLocation), Util.Multi.safeB(documentLocation));
        }

        static Util.Multi<Document, String> googlechrome(String link, BaseDirs dirs, Context ctx) {
            Util.Multi<Document, String> documentLocation = googleChrome(link, dirs.getGoogleScript(ctx), true);
            return new Util.Multi<>(Util.Multi.safeA(documentLocation), Util.Multi.safeB(documentLocation));
        }

        static Util.Multi<Document, String> phantomScroll(String link, BaseDirs dirs) {
            Util.Multi<Document, String> documentLocation = WebReader.phantomScroll(link, dirs.getPhantomDir());
            return new Util.Multi<>(Util.Multi.safeA(documentLocation), Util.Multi.safeB(documentLocation));
        }

        static Util.Multi<Document, String> phantomClickWait(String link, BaseDirs dirs, String selector) {
            Util.Multi<Document, String> documentLocation = WebReader.phantomClickWait(link, dirs.getPhantomDir(), selector);
            return new Util.Multi<>(Util.Multi.safeA(documentLocation), Util.Multi.safeB(documentLocation));
        }
    }

    static class UriExtension {
        static List<String> specialUrls = list();
        static Map<String, String> urlHost = new HashMap<>();
        static boolean indexHtmlCheck = false;

        static String toFullUrl(String baseUrl, String uri) {
            if (uri == null) return null;
            if (uri.equals("/")) return baseUrl;
            if (urlHost.get(baseUrl) == null) {
                try {
                    urlHost.put(baseUrl, new URI(WebUtils.convertSpecialCharacters(baseUrl)).getHost());
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
            uri = uri.trim();
            String uriToAdd = null;
            String host = urlHost.get(baseUrl);
            if (uri.startsWith("http:") || uri.startsWith("https:")) {
                uriToAdd = uri;
            } else if (uri.startsWith("//")) {
                uriToAdd = "http:" + uri;
            } else if (uri.startsWith("/")) {
                if (host.endsWith("/")) {
                    uri = uri.substring(Math.max(uri.length(), 1));
                }
                uriToAdd = host + uri;
                if (baseUrl.startsWith("https:")) {
                    if (uriToAdd.startsWith("www.")) {
                        uriToAdd = "https://" + uriToAdd;
                    } else {
                        uriToAdd = uriToAdd.replaceFirst("http:", "https:");
                    }
                }
                if (baseUrl.startsWith("http:")) {
                    if (uriToAdd.startsWith("www.")) {
                        uriToAdd = "http://" + uriToAdd;
                    } else {
                        uriToAdd = uriToAdd.replaceFirst("https:", "http:");
                    }
                }
            } else if (uri.startsWith("?")) {
                uriToAdd = baseUrl + uri;
            } else if (uri.startsWith("#/")) {
                if (baseUrl.contains("#/")) {
                    String[] split = baseUrl.split("#/");
                    uriToAdd = "";
                    for (int i = 0; i < split.length - 1; i++) {
                        uriToAdd = uriToAdd + "#/" + split[i];
                    }
                    uriToAdd = uriToAdd + uri;
                } else {
                    uriToAdd = baseUrl + uri;
                }
            } else if (!uri.startsWith("mailto:")) {
                if (baseUrl.endsWith(".html") && indexHtmlCheck) {
                    String[] split = baseUrl.split("/");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < split.length - 1; i++) {
                        sb.append(split[i] + "/");
                    }
                    sb.append(uri);
                    uriToAdd = sb.toString();
                } else if (baseUrl.endsWith(".html")) {
                    uriToAdd = host + (host.endsWith("/") ? "" : "/") + uri;
                } else {
                    String slash = baseUrl.endsWith("/") ? "" : "/";
                    for (String specialUrl : specialUrls) {
                        if (baseUrl.contains(specialUrl)) {
                            uriToAdd = specialUrl + slash + uri;
                        }
                    }
                    if (uriToAdd != null) {
                    } else if (baseUrl.contains("?")) {
                        String[] split = baseUrl.split("\\?")[0].split("/");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < split.length - 1; i++) {
                            sb.append(split[i] + "/");
                        }
                        sb.append(uri);
                        uriToAdd = sb.toString();
                    } else {
                        uriToAdd = baseUrl + slash + uri;
                    }
                }
            } else {
                uriToAdd = uri;
            }
            if (!uriToAdd.startsWith("http") && !uri.startsWith("mailto:")) {
                uriToAdd = "http://" + uriToAdd;
            }
            return uriToAdd;
        }
    }
}