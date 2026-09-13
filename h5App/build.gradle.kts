import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

plugins {
    // Import KMM plugin
    kotlin("multiplatform")
}

kotlin {
    // Build JS output for h5App
    js(IR) {
        // Build output supports browser
        browser {
            webpackTask {
                // Final output executable JS filename
                outputFileName = "h5App.js"
            }

            commonWebpackConfig {
                // Do not export global objects, only export necessary entry methods
                output?.library = null
            }
        }
        // Package render code and h5App code together and execute directly
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                // Import web render, for specific business should use actual version, for example:
                implementation("com.tencent.kuikly-open.core-render-web:base:${Version.getKuiklyVersion()}")
                implementation("com.tencent.kuikly-open.core-render-web:h5:${Version.getKuiklyVersion()}")
            }
        }
    }
}

// Business project path name
val businessPathName = "shared"

fun distributionsDir() = Paths.get(project.buildDir.absolutePath, "distributions")

fun productionExecutableDir() =
    Paths.get(project.buildDir.absolutePath, "dist", "js", "productionExecutable")

fun prepareDistributionFiles() {
    val sourceDir = productionExecutableDir().toFile()
    val destDir = distributionsDir().toFile()
    if (!sourceDir.exists()) {
        throw GradleException("H5 production distribution not found: ${sourceDir.absolutePath}")
    }
    project.copy {
        from(sourceDir)
        into(destDir)
    }
}

/**
 * Copy locally built unified JS result to h5App's build/distributions/page directory
 */
fun copyLocalJSBundle() {
    // Output target path
    val destDir = Paths.get(
        distributionsDir().toString(),
        "page"
    ).toFile()
    if (!destDir.exists()) {
        // Create directory if it doesn't exist
        destDir.mkdirs()
    } else {
        // Remove original files if directory exists
        destDir.deleteRecursively()
    }

    // Input target path, in shared/outputs/kuikly/js/release/local/nativevue2.zip
    val sourceDir = Paths.get(project.buildDir.absolutePath, "distributions", "kotlin2js").toFile()

    // File to be decompressed
    val zipFile = Paths.get(
        project.rootDir.absolutePath,
        businessPathName,
        "build", "outputs", "kuikly", "js", "release", "local", "nativevue2.zip"
    ).toFile()
    // Compressed file directory
    val zipDir = Paths.get(project.buildDir.absolutePath, "distributions/kotlin2js").toFile()
    if (!zipDir.exists()) {
        zipDir.mkdirs()
    } else {
        zipDir.deleteRecursively()
    }
    // Decompress
    project.copy {
        from(zipTree(zipFile))
        into(zipDir)
    }
    // Copy files
    project.copy {
        // Copy js files from business build result
        from(sourceDir) {
            include("nativevue2.js")
        }
        into(destDir)
    }
    // Remove redundant decompressed directory kotlin2js
    delete(sourceDir)

}

/**
 * Copy business built page JS result to h5App's build/distributions/page directory
 */
fun copySplitJSBundle() {
    // Output target path
    val destDir = Paths.get(
        project.buildDir.absolutePath,
        "distributions", "page"
    ).toFile()
    if (!destDir.exists()) {
        // Directory does not exist, create it
        destDir.mkdirs()
    } else {
        // Remove original files if directory exists
        destDir.deleteRecursively()
    }
    // Input target path, in shared/outputs/kuikly/js/release/split/page
    val sourceDir = Paths.get(
        project.rootDir.absolutePath,
        businessPathName,
        "build", "outputs", "kuikly", "js", "release", "split", "page"
    ).toFile()

    // Copy files
    project.copy {
        // Copy js files from business build result
        from(sourceDir) {
            include("*.js")
        }
        into(destDir)
    }
}

/**
 * Apply two bundled-level patches to dist/h5App.js that the Kotlin/JS compiler
 * currently emits in a way that breaks Kuikly-web's `nativevue2.js` ↔ `h5App.js`
 * interaction. Both patches were discovered while running H5 on web; see
 * `.workbuddy/memory/2026-09-13.md` for the root-cause analysis.
 *
 *   P1. `,yt(),t})` → `,window.__kuiklyMain__=yt,t})`
 *       Defers `fun main()` to the developer; `index.html` calls it after both
 *       bundles finish loading, so nativevue2's bridge registers before main runs.
 *
 *   P2. UMD copy loop `for(var i in e)("object"==typeof exports?exports:t)[i]=e[i]`
 *       → `for(var i in e)if(i!=="com")(...)`
 *       Prevents h5App.js from overwriting `window.com.tencent.kuikly.core.nvi`
 *       (which nativevue2.js set up to receive `registerCallNative`).
 *
 * Also appends a third `<script>` to index.html that invokes __kuiklyMain__().
 *
 * Idempotent: missing markers are detected and the task logs but does not throw.
 */
fun patchH5AppForWebBridges() {
    val h5AppPath = Paths.get(distributionsDir().toString(), "h5App.js").toFile()
    if (!h5AppPath.exists()) {
        println("patchH5AppForWebBridges: skipped, h5App.js not found")
        return
    }
    val src = h5AppPath.readText(StandardCharsets.UTF_8)
    var updated = src

    // P1: defer main to window.__kuiklyMain__
    if (updated.contains(",yt(),t})")) {
        updated = updated.replace(",yt(),t})", ",window.__kuiklyMain__=yt,t})")
        println("patchH5AppForWebBridges: P1 (defer main) applied")
    } else if (updated.contains("window.__kuiklyMain__=yt")) {
        println("patchH5AppForWebBridges: P1 already applied, skipped")
    } else {
        println("patchH5AppForWebBridges: P1 marker not found — webpack output format may have changed")
    }

    // P2: skip writing to window.com (preserve nativevue2's bridge exports)
    val p2Old = """for(var i in e)("object"==typeof exports?exports:t)[i]=e[i]"""
    val p2New = """for(var i in e)if(i!=="com")("object"==typeof exports?exports:t)[i]=e[i]"""
    if (updated.contains(p2Old)) {
        updated = updated.replace(p2Old, p2New)
        println("patchH5AppForWebBridges: P2 (skip window.com in UMD loop) applied")
    } else if (updated.contains(p2New)) {
        println("patchH5AppForWebBridges: P2 already applied, skipped")
    } else {
        println("patchH5AppForWebBridges: P2 marker not found — webpack output format may have changed")
    }

    if (updated !== src) {
        h5AppPath.writeText(updated, StandardCharsets.UTF_8)
    }

    // Idempotent index.html bootstrap: strip any existing patch artifacts, then re-inject fresh.
    // This handles re-running the task without producing duplicate </body></html> blocks.
    val htmlPath = Paths.get(distributionsDir().toString(), "index.html").toFile()
    if (!htmlPath.exists()) {
        println("patchH5AppForWebBridges: index.html not found, skipped")
        return
    }
    var html = htmlPath.readText(StandardCharsets.UTF_8)

    // Strip any previous bootstrap script block (between the bridge comment marker and </script>)
    val bootstrapRe = Regex("""<script>\s*\n\s*// bridge \(nativevue2\.js\)[\s\S]*?</script>\s*""")
    html = html.replace(bootstrapRe, "")

    // Strip any duplicate </body></html> tail that may have leaked from earlier runs (keep only ONE).
    // Strategy: keep the first </body></html> and remove additional ones.
    val firstBodyMatch = Regex("""</body>""").find(html)
    if (firstBodyMatch != null) {
        val firstBodyEnd = firstBodyMatch.range.last + 1
        // From firstBodyEnd, replace any subsequent </body></html> with the literal text passed below
        val tail = html.substring(firstBodyEnd)
        val cleanedTail = tail.replace(Regex("""(</body>\s*</html>\s*)+"""), "")
        html = html.substring(0, firstBodyEnd) + cleanedTail
    }

    // Find the h5App.js script tag and rewrite the tail after it
    val closingScripts = Regex("""(<script\s+src="h5App\.js"[^>]*></script>)""")
    val match = closingScripts.find(html)
    if (match != null) {
        val scriptTag = match.groupValues[1]
        val injected = """$scriptTag
<script>
  // bridge (nativevue2.js) must be set up before h5App's deferred main() runs
  if (typeof window.__kuiklyMain__ === 'function') {
    window.__kuiklyMain__();
  } else {
    console.error('window.__kuiklyMain__ is not a function — bridge / bundle mismatch');
  }
</script>
</body>
</html>"""
        // Replace from h5App.js script tag through end of file, including any orphan </body></html>
        // the kotlin-generated HTML already had after the script tag.
        val newHtml = html.substring(0, match.range.first) + injected
        htmlPath.writeText(newHtml, StandardCharsets.UTF_8)
        println("patchH5AppForWebBridges: index.html injected with __kuiklyMain__() bootstrap")
    } else {
        println("patchH5AppForWebBridges: index.html has unexpected </script> layout, manual edit needed")
    }
}

/**
 * Generate unified build page html file
 */
fun generateLocalHtml() {
    // File path to be processed
    val filePath = Paths.get(
        distributionsDir().toString(),
        "index.html"
    )
    val fileContent = Files.readString(filePath)
    // Placeholder to be replaced
    val placeText = Regex("http://127\\.0\\.0\\.1:8083/nativevue2\\.js[^\"']*")
    // Replace development environment JSBundle link with production environment link
    val updatedContent = fileContent.replace(placeText, "page/nativevue2.js")
    // Write new file content
    Files.writeString(filePath, updatedContent, StandardCharsets.UTF_8)
    // Write success
    println("generate local html file success.")
}

/**
 * Generate page build html file
 */
fun generateSplitHtml() {
    // File path to be processed
    val htmlFilePath = Paths.get(
        distributionsDir().toString(),
        "index.html"
    )
    val fileContent = Files.readString(htmlFilePath)
    // Placeholder to be replaced
    val placeText = Regex("http://127\\.0\\.0\\.1:8083/nativevue2\\.js[^\"']*")
    // Need to read all js files in page, get file names, then modify business js in index.html to corresponding
    val pagePath = Paths.get(
        distributionsDir().toString(),
        "page"
    )
    val pageDir = file(pagePath)
    if (pageDir.exists()) {
        // File names, and change new html file name to page name
        val files = pageDir.listFiles()
        files?.forEach { file ->
            if (file.isFile) {
                val fileName = file.name
                // Replace development environment JSBundle link with production environment link
                val updatedContent = fileContent.replace(placeText, "page/$fileName")
                // File path to be written
                val filePath = Paths.get(
                    distributionsDir().toString(),
                    "${file.nameWithoutExtension}.html"
                )
                // Write new file content
                Files.writeString(filePath, updatedContent, StandardCharsets.UTF_8)
            }
        }
        // Remove index.html
        htmlFilePath.toFile().delete()
        // Write success
        println("generate local html file success.")
    } else {
        // Write failure
        println("generate local html file failure, no such files.")
    }
}

/**
 * Copy business assets resources to h5App's build/distributions/page directory
 */
fun copyAssetsResource() {
    // Source target path
    val sourceDir = Paths.get(
        project.rootDir.absolutePath,
        businessPathName,
        "build",
        "outputs",
        "kuikly",
        "assets"
    )

    if (sourceDir.toFile().exists()) {
        // If directory does not exist, do not process
        // Output target path, in h5App
        val destDir = Paths.get(
            project.rootDir.absolutePath,
            "h5App",
            "build",
            "distributions",
            "assets"
        )

        // Copy files
        project.copy {
            // Copy assets resources from business build result to publish directory
            from(sourceDir)
            into(destDir)
        }
    } else {
        print("dest directory not exist")
    }
}

/**
 * Copy assets resources to webpack dev server static directory
 */
fun copyAssetsFileToWebpackDevServer() {
    // Source target path
    val sourceDir = Paths.get(
        project.rootDir.absolutePath,
        businessPathName,
        "src",
        "commonMain",
        "assets"
    )

    if (sourceDir.toFile().exists()) {
        // If directory does not exist, do not process
        // Output target path, in h5App
        val destDir = Paths.get(
            project.rootDir.absolutePath,
            "h5App",
            "build", "processedResources", "js", "main", "assets"
        )

        // Copy files
        project.copy {
            // Copy assets resources from business build result to deServer directory
            from(sourceDir)
            into(destDir)
        }
    } else {
        print("dest directory not exist")
    }
}

project.afterEvaluate {
    // At this point, project configuration is complete,
    // register build h5 page release version related build methods here

    // Register Release unified packaging processing task
    tasks.register("publishLocalJSBundle") {
        group = "kuikly"

        // First execute h5App build task and business bundle packaging.
        dependsOn("jsBrowserDistribution")
        dependsOn(":$businessPathName:packLocalJSBundleRelease")
        doLast {
            prepareDistributionFiles()
            // Then copy corresponding nativevue2.zip from business build result and copy nativevue2.js
            // to h5App's release directory
            copyLocalJSBundle()
            // Copy assets resources
            copyAssetsResource()
            // Finally modify html file page.js reference
            generateLocalHtml()
            // Patch h5App.js + index.html for nativevue2.js ↔ h5App.js web-bridge interaction
            patchH5AppForWebBridges()
        }
    }

    // Register Release page packaging processing task
    tasks.register("publishSplitJSBundle") {
        group = "kuikly"

        // First execute h5App build task and business bundle packaging.
        dependsOn("jsBrowserDistribution")
        dependsOn(":$businessPathName:packSplitJSBundleRelease")
        doLast {
            prepareDistributionFiles()
            // Then copy corresponding page js from business build result to h5App's release directory
            copySplitJSBundle()
            // Copy assets resources
            copyAssetsResource()
            // Finally modify html file page.js reference
            generateSplitHtml()
        }
    }

    // Copy assets resources to devServer directory when using webpack for development debugging
    tasks.register("copyAssetsToWebpackDevServer") {
        copyAssetsFileToWebpackDevServer()
    }
}
