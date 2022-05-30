package org.eiennohito.sudachi.diff

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.io.path.notExists

data class SudachiRuntimeConfig(
    val sudachiJar: Path?,
    val javaxJsonJar: Path?,
    val jdartsCloneJar: Path?,
    val sudachiConfigFile: Path?,
    val addSettings: String = "{}"
)

private fun Path.absoluteUrl(): URL {
    if (notExists()) {
        throw FileSystemException(toFile(), reason = "file does not exist")
    }
    return absolute().toUri().toURL()
}

private fun resolve(first: Path?, base: Path, namePart: String): URL? {
    if (first != null) {
        return first.absoluteUrl()
    }
    val parentDir = base.parent
    val found = Files.find(parentDir, 1, { p, _ -> p.name.contains(namePart) }).findFirst()
    if (found.isEmpty) {
        return null
    }
    return found.get().absoluteUrl()
}

class SudachiAnalysisTaskRunner(private val config: SudachiRuntimeConfig) {
    private val jars = kotlin.run {
        val jars = ArrayList<URL>()
        if (config.sudachiJar == null) {
            config.javaxJsonJar?.let { jars.add(it.absoluteUrl()) }
            config.jdartsCloneJar?.let { jars.add(it.absoluteUrl()) }
        } else {
            jars.add(config.sudachiJar.absoluteUrl())
            resolve(config.jdartsCloneJar, config.sudachiJar, "jdartsclone")?.let { jars.add(it) }
            resolve(config.javaxJsonJar, config.sudachiJar, "javax.json")?.let { jars.add(it) }
        }
        jars.toArray(emptyArray<URL>())
    }

    // this classloader needs to load
    // 1. SudachiRuntime
    // 2. Everything from sudachi jars
    // before parent classloaders
    private val classloader = object : URLClassLoader("SudachiLoader", jars, this.javaClass.classLoader) {
        override fun loadClass(name: String?, resolve: Boolean): Class<*> {
            if (name == null) {
                throw java.lang.NullPointerException()
            }
            val loaded = findLoadedClass(name)
            if (loaded != null) {
                return loaded
            }
            try {
                if (name.startsWith("org.eiennohito.sudachi.diff.iface.")) {
                    val path = name.replace(".", "/") + ".class"
                    val resource = parent.getResource(path)!!
                    val bytes = resource.readBytes()
                    return defineClass(name, bytes, 0, bytes.size)
                }
                return findClass(name)
            } catch (ex: ClassNotFoundException) {
                return super.loadClass(name, resolve)
            }
        }
    }

    fun process(input: Path, output: Path) {
        // runtime will be loaded by explicit classloader here, we should operate with it only via reflection
        val runtime = classloader.loadClass("org.eiennohito.sudachi.diff.iface.SudachiRuntime")
        val constructor = runtime.getConstructor(ClassLoader::class.java, SudachiRuntimeConfig::class.java)
        val instance = constructor.newInstance(classloader, config)
        val runMethod = runtime.getDeclaredMethod("run", Path::class.java, Path::class.java)
        runMethod.invoke(instance, input, output)
        classloader.close()
    }
}