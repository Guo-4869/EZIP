// Downloads build toolchain (Android SDK pieces) + Java libraries.
// Node.js is used because the shell has no outbound network in this environment.
const https = require("https");
const fs = require("fs");
const path = require("path");

const TC = path.join(process.env.USERPROFILE, ".ezip-toolchain");
const DL = path.join(TC, "downloads");
const LIBS = path.join(process.argv[2] || ".", "libs");

const targets = [
  { url: "https://dl.google.com/android/repository/build-tools_r36.1_windows.zip", dest: path.join(DL, "build-tools_r36.1_windows.zip") },
  { url: "https://dl.google.com/android/repository/platform-34-ext7_r03.zip", dest: path.join(DL, "platform-34-ext7_r03.zip") },
  { url: "https://repo1.maven.org/maven2/net/lingala/zip4j/zip4j/2.11.5/zip4j-2.11.5.jar", dest: path.join(LIBS, "zip4j-2.11.5.jar") },
  { url: "https://repo1.maven.org/maven2/net/lingala/zip4j/zip4j/2.11.5/zip4j-2.11.5-sources.jar", dest: path.join(DL, "zip4j-2.11.5-sources.jar") },
  { url: "https://repo1.maven.org/maven2/com/github/junrar/junrar/7.5.5/junrar-7.5.5.jar", dest: path.join(LIBS, "junrar-7.5.5.jar") },
  { url: "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.26.2/commons-compress-1.26.2.jar", dest: path.join(LIBS, "commons-compress-1.26.2.jar") },
  { url: "https://repo1.maven.org/maven2/commons-io/commons-io/2.16.1/commons-io-2.16.1.jar", dest: path.join(LIBS, "commons-io-2.16.1.jar") },
  { url: "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar", dest: path.join(LIBS, "slf4j-api-1.7.36.jar") },
  { url: "https://repo1.maven.org/maven2/org/slf4j/slf4j-nop/1.7.36/slf4j-nop-1.7.36.jar", dest: path.join(LIBS, "slf4j-nop-1.7.36.jar") },
  { url: "https://repo1.maven.org/maven2/org/tukaani/xz/1.9/xz-1.9.jar", dest: path.join(LIBS, "xz-1.9.jar") },
  { url: "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar", dest: path.join(LIBS, "commons-lang3-3.14.0.jar") },
];

function download(url, dest, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (fs.existsSync(dest) && fs.statSync(dest).size > 1024) {
      console.log("SKIP (exists) " + path.basename(dest));
      return resolve();
    }
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    https.get(url, (res) => {
      if ([301, 302, 307, 308].includes(res.statusCode)) {
        res.resume();
        if (redirects > 6) return reject(new Error("too many redirects"));
        return resolve(download(res.headers.location, dest, redirects + 1));
      }
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error("HTTP " + res.statusCode + " for " + url));
      }
      const total = parseInt(res.headers["content-length"] || "0", 10);
      let done = 0;
      const tmp = dest + ".part";
      const out = fs.createWriteStream(tmp);
      res.on("data", (c) => {
        done += c.length;
        if (total && Math.floor((done / total) * 100) % 10 === 0) {
          process.stdout.write(`\r${path.basename(dest)} ${(done / 1048576).toFixed(1)}/${(total / 1048576).toFixed(1)} MB`);
        }
      });
      res.pipe(out);
      out.on("finish", () => {
        out.close(() => {
          fs.renameSync(tmp, dest);
          console.log(`\rDONE ${path.basename(dest)} ${(done / 1048576).toFixed(1)} MB`);
          resolve();
        });
      });
      out.on("error", reject);
    }).on("error", reject);
  });
}

(async () => {
  for (const t of targets) {
    await download(t.url, t.dest);
  }
  console.log("ALL DOWNLOADS COMPLETE");
})().catch((e) => { console.error("FAILED: " + e.message); process.exit(1); });
