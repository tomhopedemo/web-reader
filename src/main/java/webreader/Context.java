package webreader;

import java.util.List;
import java.util.regex.Matcher;

import static webreader.Util.*;

public class Context {
    String ref;
    String notes;
    List<String> statuses;
    Interpret interpret;

    Context(String ref, String notes, List<String> statuses) {
        this.ref = ref;
        this.notes = notes;
        this.interpret = new Interpret(ref, notes);
        this.statuses = statuses;
    }


    boolean downloadCurl() {
        return interpret.contains("_CURL_");
    }

    boolean downloadPhantom() {
        return interpret.contains("_PHANTOM_", "_YPHANTOM_");
    }

    boolean quickGoogle(){
        return interpret.contains("_QUICK_");
    }

    boolean downloadScroll() {
        return interpret.contains("_SCROLL_");
    }

    String downloadClickWait() {
        return interpret.normal("_CLICKWAIT");
    }

    static class Interpret {
        String ref;
        String notes;

        Interpret(String ref, String notes) {
            this.ref = ref;
            this.notes = notes;
        }

        String normal(String field) {
            Matcher matcher = matcher(field + "=(\\S+)", notes);
            return matcher.find() ? matcher.group(1) : null;
        }

        boolean contains(String... notelist) {
            for (String tag : notelist) {
                if (notes.contains(tag)) return true;
            }
            return false;
        }
    }
}