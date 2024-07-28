package webreader;

import static webreader.Util.EnvType.LOCAL;

class BaseDirs {
    final Util.EnvType type;

    BaseDirs(Util.EnvType type) {
        this.type = type;
    }

    String getRoot() {
        return "/usr/local/";
    }

    String getPhantomDir() {
        if (LOCAL.equals(type)) {
            return getRoot() + "phantomjs" + "/" + "bin" + "/";
        } else {
            return "/usr/lib/phantomjs/bin/";
        }
    }

    String getGoogleScript(Context ctx) {
        String root = LOCAL.equals(type) ? getRoot() : "/usr/lib/";
        return root + "googlechrome/" + (ctx.quickGoogle() ? "headlessQuick.sh" : "headless.sh");
    }
}