package com.worksap.nlp.sudachi.diff

import java.io.InputStream
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.notExists

data class SudachiAdditionalSettings(
    val systemDict: Path? = null,
    val userDicts: List<Path> = emptyList(),
)

data class SudachiRuntimeConfig(
    val classpath: List<Path>,
    val sudachiConfigFile: Path?,
    val addSettings: String = "{}",
    val addSettings2: SudachiAdditionalSettings? = null,
    val mode: String = "C"
)

private fun Path.absoluteUrl(): URL {
    if (notExists()) {
        throw FileSystemException(toFile(), reason = "file does not exist")
    }
    return absolute().toUri().toURL()
}

class SudachiAnalysisTaskRunner(private val config: SudachiRuntimeConfig) {
    private val jars = config.classpath.map { it.toUri().toURL() }.toTypedArray()


    // this classloader needs to load
    // 1. Classes from com.worksap.nlp.sudachi.diff.iface package
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
                if (name.startsWith("com.worksap.nlp.sudachi.diff.iface.")) {
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

    fun process(input: Path, output: Path, filter: String) {
        // runtime will be loaded by explicit classloader here, we should operate with it only via reflection
        val runtime = classloader.loadClass("com.worksap.nlp.sudachi.diff.iface.SudachiRuntime")
        val constructor = runtime.getConstructor(ClassLoader::class.java, SudachiRuntimeConfig::class.java)
        val instance = constructor.newInstance(classloader, config) as SuRuntime
        instance.run(input, output, filter)
        classloader.close()
    }

    fun interactiveDebug(data: InputStream, mode: String) {
        val runtime = classloader.loadClass("com.worksap.nlp.sudachi.diff.iface.SudachiRuntime")
        val constructor = runtime.getConstructor(ClassLoader::class.java, SudachiRuntimeConfig::class.java)
        val instance = constructor.newInstance(classloader, config) as SuRuntime
        val analyzer = instance.analyzer(mode)
        val reader = data.bufferedReader()
        reader.lines().use { it.forEach { s -> analyzer.analyzeSentence(s, System.out, true) } }
    }
}