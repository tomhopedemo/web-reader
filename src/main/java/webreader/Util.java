package webreader;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Util {

    public static String substringRemoveLast(StringBuilder sb, Integer numToRemove) {
        if (sb.length() == 0) {
            return "";
        } else {
            if (numToRemove >= sb.length()) return "";
            return sb.substring(0, sb.length() - numToRemove);
        }
    }

    public static boolean empty(String string) {
        return string == null || string.trim().length() == 0;
    }

    public static <E> ArrayList<E> list(E... objects) {
        ArrayList<E> objectsList = new ArrayList<>();
        for (E object : objects) {
            objectsList.add(object);
        }
        return objectsList;
    }

    public static <T, S> T safeA(Multi<T, S> multi) {
        if (multi == null) return null;
        return multi.a;
    }

    public static <T> String out(Collection<T> collection, String separator) {
        StringBuilder sb = new StringBuilder();
        for (T object : collection) {
            if (object != null) {
                String str = object.toString();
                if (str != null) {
                    sb.append(str);
                }
            }
            sb.append(separator);
        }
        return Util.substringRemoveLast(sb, separator.length());
    }

    public static void sout(String... strings) {
        System.out.println(string(strings));
    }

    public static <T> String string(T[] collection) {
        if (collection == null) return null;
        return out(Arrays.asList(collection), " ");
    }

    public static Matcher matcher(String regex, String text) {
        return Pattern.compile(regex).matcher(text);
    }

    public static <A, B, C> A safeA(Multi3<A, B, C> multi) {
        if (multi == null) return null;
        return multi.a;
    }

    public static <A, B, C> B safeB(Multi3<A, B, C> multi) {
        if (multi == null) return null;
        return multi.b;
    }

    public static final List<String> FILE_LOCKS_REQUESTED_BY_READER = list();
    public static final List<String> FILE_LOCKS_REQUESTED_BY_WRITER = list();

    public static void write(String path, String valueToWrite) {
        synchronized (path) {
            File file = new File(path);
            if (file.exists()) {
                file.delete();
            }
            if (file.getParentFile() != null) {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
            }
            PrintWriter out = null;
            try {
                out = new PrintWriter(path);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            out.append(valueToWrite);
            out.close();
        }
    }

    public static void appendSafe(String path, String valueToWrite) {
        synchronized (path) {
            try {
                FILE_LOCKS_REQUESTED_BY_WRITER.add(path);
                while (FILE_LOCKS_REQUESTED_BY_READER.contains(path)) {
                    Thread.sleep(200);
                }
                append(path, valueToWrite);
            } catch (Exception ignored) {
            } finally {
                FILE_LOCKS_REQUESTED_BY_WRITER.remove(path);
            }
        }
    }

    public static void append(String path, String valueToWrite, String delimiter) {
        synchronized (path) {
            if (Util.read(path).size() == 0) {
                appendInternal(path, valueToWrite);
            } else {
                appendInternal(path, delimiter + valueToWrite);
            }
        }
    }

    public static void append(String path, String valueToWrite) {
        append(path, valueToWrite, "\n");
    }

    public static void appendInternal(String path, String valueToWrite) {
        PrintWriter out = null;
        try {
            File file = new File(path);
            if (file.getParentFile() != null) {
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
            }
            out = new PrintWriter(new FileOutputStream(file, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
        out.append(valueToWrite);
        out.close();
    }

    public static List<String> read(String file) {
        List<String> list = list();
        Scanner scanner;
        try {
            File source = new File(file);
            if (!source.exists()) return list();
            scanner = new Scanner(source);
            while (scanner.hasNextLine()) {
                list.add(scanner.nextLine());
            }
            scanner.close();
            if (list.size() == 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "Cp1252"));
                Stream<String> lines = reader.lines();
                if (lines != null) {
                    list = lines.collect(Collectors.toList());
                }
            }
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static String readString(String file) {
        StringBuilder sb = new StringBuilder();
        Scanner scanner;
        try {
            File source = new File(file);
            if (!source.exists()) return null;
            scanner = new Scanner(source);
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine() + "\n");
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (sb.length() > 0) {
            return sb.substring(0, sb.length() - 1);
        } else {
            return null;
        }
    }

    public static Map<String, String> readMapSafe(String file, String delimiter) {
        synchronized (file) {
            Map<String, String> map = null;
            try {
                FILE_LOCKS_REQUESTED_BY_READER.add(file);
                while (FILE_LOCKS_REQUESTED_BY_WRITER.contains(file)) {
                    Thread.sleep(200);
                }
                map = readMap(file, delimiter);
            } catch (Exception ignored) {
            } finally {
                FILE_LOCKS_REQUESTED_BY_READER.remove(file);
            }
            return map;
        }
    }

    public static Map<String, String> readMap(String file, String delimiter) {
        Map<String, String> map = new HashMap<>();
        for (String string : read(file)) {
            if (string.trim().equals("")) continue;
            String[] split = string.split(delimiter, -1);
            if (split.length > 1) {
                map.put(split[0], split[1]);
            }
        }
        return map;
    }

    public static Map<String, String> charmap;

    static {
        charmap = new HashMap<>();
        charmap.put("è", "È");
        charmap.put("é", "É");
        charmap.put("ê", "Ê");
        charmap.put("ç", "Ç");
        charmap.put("ö", "Ö");
        charmap.put("ë", "Ë");
        charmap.put("ï", "Ï");
        charmap.put("ü", "Ü");
        charmap.put("ä", "Ä");
        charmap.put("ż", "Ż");
        charmap.put("ł", "Ł");
        charmap.put("á", "Á");
        charmap.put("ð", "Ð");
        charmap.put("í", "Í");
        charmap.put("ó", "Ó");
        charmap.put("ú", "Ú");
        charmap.put("ý", "Ý");
        charmap.put("Þ", "þ");
        charmap.put("æ", "Æ");
        charmap.put("ô", "Ô");
    }

    public enum EnvType {LOCAL, SERVER}

    public static class Multi<A, B> implements Serializable {
        public A a;
        public B b;

        public Multi(A a, B b) {
            this.a = a;
            this.b = b;
        }

        public static <T, S> T safeA(Multi<T, S> multi) {
            if (multi == null) return null;
            return multi.a;
        }

        public static <T, S> S safeB(Multi<T, S> multi) {
            if (multi == null) return null;
            return multi.b;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Multi<?, ?> multi = (Multi<?, ?>) o;
            if (!Objects.equals(a, multi.a)) return false;
            return Objects.equals(b, multi.b);
        }

        public int hashCode() {
            int result = a != null ? a.hashCode() : 0;
            result = 31 * result + (b != null ? b.hashCode() : 0);
            return result;
        }
    }

    public static class Multi3<A, B, C> implements Serializable {
        public A a;
        public B b;
        public C c;

        public Multi3(A a, B b, C c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Multi3<?, ?, ?> multi_3 = (Multi3<?, ?, ?>) o;
            return Objects.equals(a, multi_3.a) && Objects.equals(b, multi_3.b) && Objects.equals(c, multi_3.c);
        }

        public int hashCode() {
            return Objects.hash(a, b, c);
        }
    }
}