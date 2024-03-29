package com.worksap.nlp.sudachi.diff.analyze

import com.worksap.nlp.sudachi.diff.SudachiAdditionalSettings
import com.worksap.nlp.sudachi.diff.SudachiRuntimeConfig
import com.worksap.nlp.sudachi.diff.existingPath
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodySubscribers
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.*

class SudachiResolver(root: Path) {
    private val downloader = SudachiDownloader(root)

    fun config(
        jar: String?,
        config: String?,
        additionalSettings: SudachiAdditionalSettings? = null,
        mode: String = "C"
    ): SudachiRuntimeConfig {
        val cpath = resolveJar(jar)
        return SudachiRuntimeConfig(cpath, config?.existingPath(), addSettings2 = additionalSettings, mode = mode)
    }

    private fun resolveJar(jar: String?): List<Path> {
        if (jar == null) {
            throw IllegalArgumentException("--jar parameter was not passed")
        }

        val pjar = Path(jar)

        if (pjar.exists()) {
            return resolveInDirectoryClasspath(pjar)
        }

        if (jar.startsWith("v")) {
            return downloader.resolveSudachiByVersion(jar.substring(1))
        }

        throw IllegalArgumentException("$jar was invalid parameter, it is neither a version (should start with v like v0.6.2) nor a path to a file")
    }

    private fun resolveInDirectoryClasspath(pjar: Path): List<Path> {
        val manifest = JarFile(pjar.toFile()).manifest
        val result = ArrayList<Path>()
        result.add(pjar)
        val classPath = manifest.mainAttributes.getValue(Attributes.Name.CLASS_PATH)
        if (classPath != null) {
            val entries = classPath.toString().split(' ')
            val parent = pjar.parent
            entries.forEach { resolveClasspathEntry(parent, it).forEach(result::add) }
        }
        return result
    }

    private fun resolveClasspathEntry(parent: Path, name: String): Sequence<Path> {
        val child = parent.resolve(name)

        if (child.exists()) {
            return sequenceOf(child)
        }

        val m = DEPENDENCY_REGEX.matchEntire(name) ?: return emptySequence()
        val depName = m.groups[1]!!
        val depVersion = m.groups[2]!!

        return downloader.tryToResolveJar(depName.value, depVersion.value)
    }

    companion object {
        private val DEPENDENCY_REGEX = Regex("""(.+)-([\d.]+(?:-SNAPSHOT)?)\.jar""")
    }
}

class NodesIterator(private val list: NodeList) : Iterator<Node> {
    private var index = 0
    private var total = list.length
    override fun hasNext(): Boolean = index < total
    override fun next(): Node = list.item(index++)
}

class CacheLocation(root: Path, private val downloader: SudachiDownloader, val coords: MavenCoordinates) {
    val directory = root.resolve("jars").resolve(coords.organization).resolve("${coords.name}-${coords.version}")
    val pom = directory.resolve("${coords.name}-${coords.version}.pom")
    val jar = directory.resolve("${coords.name}-${coords.version}.jar")

    fun dependencies(): List<MavenCoordinates> {
        val doc = pom.inputStream().use { stream -> downloader.xmlParser.parse(stream) }
        val deps = doc.getElementsByTagName("dependency")
        return NodesIterator(deps).asSequence().flatMap { depNode ->
            var name = ""
            var org = ""
            var version = ""
            var scope = "compile"
            var required = true

            NodesIterator(depNode.childNodes).forEach {
                when (it.nodeName) {
                    "groupId" -> org = it.textContent
                    "artifactId" -> name = it.textContent
                    "version" -> version = it.textContent
                    "scope" -> scope = it.textContent
                    "optional" -> required = it.textContent != "true"
                }
            }

            required = when (scope) {
                "runtime" -> required
                "compile" -> required
                else -> false
            }

            if (required) {
                sequenceOf(MavenCoordinates(org, name, version))
            } else {
                emptySequence()
            }
        }.toList()
    }

    private fun downloadHandler(uri: URI, downloadTo: Path) = HttpResponse.BodyHandler { info ->
        System.err.println("downloading $uri to $downloadTo")
        if (info.statusCode() == 200) {
            BodySubscribers.ofFile(downloadTo, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        } else {
            throw IllegalStateException("failed to download $uri, status code ${info.statusCode()}")
        }
    }

    fun maybeDownloadReleaseArtifacts(client: HttpClient) {
        directory.createDirectories()
        val futures = sequence {
            if (pom.notExists()) {
                val uri = URI(coords.releaseUrl("pom"))
                val req = HttpRequest.newBuilder(uri).GET().build()
                yield(client.sendAsync(req, downloadHandler(uri, pom)))
            }
            if (jar.notExists()) {
                val uri = URI(coords.releaseUrl("jar"))
                val req = HttpRequest.newBuilder(uri).GET().build()
                yield(client.sendAsync(req, downloadHandler(uri, jar)))
            }
        }.toList()

        if (futures.isNotEmpty()) {
            CompletableFuture.allOf(*futures.toTypedArray()).get(5, TimeUnit.MINUTES)
        }
    }

    fun maybeDownloadLatestSnapshot(client: HttpClient) {
        val snapshotVersion = resolveSnapshotVersion(client)

        System.err.println("Using ${coords.organization}:${coords.name} with snapshot version $snapshotVersion")

        val localVersionFile = directory.resolve("${coords.name}-${coords.version}.ACTUAL")
        val localVersion = if (localVersionFile.exists()) {
            localVersionFile.readText()
        } else { "" }

        if (snapshotVersion != localVersion || pom.notExists() || jar.notExists()) {
            directory.createDirectories()
            val futures = sequence {
                if (true) {
                    val uri = URI(coords.snapshotUrl(snapshotVersion, "pom"))
                    val req = HttpRequest.newBuilder(uri).GET().build()
                    yield(client.sendAsync(req, downloadHandler(uri, pom)))
                }

                if (true) {
                    val uri = URI(coords.snapshotUrl(snapshotVersion, "jar"))
                    val req = HttpRequest.newBuilder(uri).GET().build()
                    yield(client.sendAsync(req, downloadHandler(uri, jar)))
                }
            }.toList()

            if (futures.isNotEmpty()) {
                CompletableFuture.allOf(*futures.toTypedArray()).get(5, TimeUnit.MINUTES)
                localVersionFile.writeText(snapshotVersion)
            }
        }
    }

    private fun resolveSnapshotVersion(client: HttpClient): String {
        val url = URI(coords.snapshotMetadataUrl(true))
        val req = HttpRequest.newBuilder(url).GET().header("User-Agent", "SudachiTools/0.1").build()
        val response = client.send(req) { BodySubscribers.ofByteArray() }
        if (response.statusCode() != 200) {
            throw IllegalStateException("failed to download $url, status code ${response.statusCode()}")
        }
        val doc = downloader.xmlParser.parse(ByteArrayInputStream(response.body()))
        val versionElem = doc.getElementsByTagName("snapshotVersion")

        for (n1 in NodesIterator(versionElem)) {
            for (n2 in NodesIterator(n1.childNodes)) {
                if (n2.nodeName == "value") {
                    return n2.textContent
                }
            }
        }

        throw IllegalStateException("failed to get snapshot version from maven-metadata.xml")
    }
}

data class MavenCoordinates(
    val organization: String,
    val name: String,
    val version: String
) {
    fun isSnapshot(): Boolean = version.endsWith("-SNAPSHOT")

    fun releaseUrl(extension: String): String {
        val orgPath = organization.replace('.', '/')
        return "https://repo1.maven.org/maven2/$orgPath/$name/$version/$name-$version.$extension"
    }

    fun snapshotUrl(actualVersion: String, extension: String): String {
        val orgPath = organization.replace('.', '/')
        return "https://oss.sonatype.org/content/repositories/snapshots/$orgPath/$name/$version/$name-$actualVersion.$extension"
    }

    fun snapshotMetadataUrl(versioned: Boolean): String {
        val orgPath = organization.replace('.', '/')
        return if (versioned) {
            "https://oss.sonatype.org/content/repositories/snapshots/$orgPath/$name/$version/maven-metadata.xml"
        } else {
            "https://oss.sonatype.org/content/repositories/snapshots/$orgPath/$name/maven-metadata.xml"
        }
    }
}

class SudachiDownloader(private val root: Path) {
    private val client by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
    }
    internal val xmlParser = run {
        val f = DocumentBuilderFactory.newInstance()
        f.isIgnoringComments = true
        f.isExpandEntityReferences = false
        f.isValidating = false
        f.isNamespaceAware = false
        f.isXIncludeAware = false
        f.newDocumentBuilder()
    }

    fun resolveSudachiByVersion(version: String): List<Path> {
        return tryToResolveJar("sudachi", version).toList()
    }

    fun tryToResolveJar(name: String, version: String): Sequence<Path> {
        val org = when (name) {
            "sudachi" -> "com.worksap.nlp"
            "jdartsclone" -> "com.worksap.nlp"
            "javax.json" -> "org.glassfish"
            else -> throw IllegalArgumentException("unknown artifact name: $name")
        }
        return tryToResolveJar(MavenCoordinates(org, name, version))
    }

    private fun tryToResolveJar(coords: MavenCoordinates): Sequence<Path> {
        val cached = CacheLocation(root, this, coords)

        refresh(cached)

        return sequence {
            if (cached.jar.exists()) {
                yield(cached.jar)
            }

            for (dep in cached.dependencies()) {
                yieldAll(tryToResolveJar(dep))
            }
        }
    }

    private fun refresh(loc: CacheLocation) {
        if (loc.coords.isSnapshot()) {
            loc.maybeDownloadLatestSnapshot(client)
        } else {
            loc.maybeDownloadReleaseArtifacts(client)
        }
    }
}
