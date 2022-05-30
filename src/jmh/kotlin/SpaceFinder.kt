package org.eiennohito.sudachi.diff

import jdk.incubator.foreign.MemoryHandles
import jdk.incubator.foreign.MemorySegment
import jdk.incubator.vector.ByteVector
import org.openjdk.jmh.annotations.*
import java.lang.invoke.VarHandle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

@State(Scope.Benchmark)
@Fork(value = 1, warmups = 0, jvmArgs = ["--add-modules", "jdk.incubator.foreign,jdk.incubator.vector"])
@BenchmarkMode(Mode.Throughput)
@Measurement(iterations = 3)
@Warmup(iterations = 2)
open class SpaceFinder {

    companion object {
        const val EOL_BYTE = '\n'.code.toByte()

        @JvmStatic
        val BYTE_HANDLE: VarHandle = MemoryHandles.varHandle(Byte::class.java, ByteOrder.nativeOrder())

    }

    private val data: ByteArray = kotlin.run {
        val rng = Random(0xdeadbeef)
        val bytes = ByteArray(256 * 1024 * 1024)
        rng.nextBytes(bytes)
        bytes
    }

    private val outBuffer = kotlin.run {
        val buf = ByteBuffer.allocate(data.size)
        buf.order(ByteOrder.nativeOrder())
        data.forEach { buf.put(it) }
        buf.clear()
        buf
    }

    @Benchmark
    fun arrayNative(): Int {
        val d = data
        var count = 0
        for (i in d.indices) {
            if (d[i] == EOL_BYTE) {
                count += 1
            }
        }
        return count
    }

    @Benchmark
    fun arrayNativeNoIterators(): Int {
        val d = data
        var count = 0
        var i = 0
        val length = d.size
        while (i < length) {
            if (d[i] == EOL_BYTE) {
                count += 1
            }
            i += 1
        }
        return count
    }

    @Benchmark
    fun wrappedByteBuffer(): Int {
        var count = 0
        val buf = ByteBuffer.wrap(data)
        for (i in 0 until buf.remaining()) {
            if (buf.get() == EOL_BYTE) {
                count += 1
            }
        }
        return count
    }

    @Benchmark
    fun wrappedByteBufferExternal(): Int {
        var count = 0
        val buf = ByteBuffer.wrap(data)
        for (i in 0 until buf.remaining()) {
            if (buf.get(i) == EOL_BYTE) {
                count += 1
            }
        }
        return count
    }

    @Benchmark
    fun outOfHeapByteBuffer(): Int {
        var count = 0
        val buf = outBuffer.duplicate()
        buf.order(outBuffer.order())
        for (i in 0 until buf.remaining()) {
            if (buf.get() == EOL_BYTE) {
                count += 1
            }
        }
        return count
    }

    @Benchmark
    fun outOfHeapByteBufferExternal(): Int {
        var count = 0
        val buf = outBuffer.duplicate()
        buf.order(outBuffer.order())
        for (i in 0 until buf.remaining()) {
            if (buf.get(i) == EOL_BYTE) {
                count += 1
            }
        }
        return count
    }

    @Benchmark
    fun arrayBasedMemoryHandle(): Int {
        var count = 0
        val buf = MemorySegment.ofArray(data)

        for (i in 0 until buf.byteSize()) {
            val b: Byte = BYTE_HANDLE.get(buf, i) as Byte
            if (b == EOL_BYTE) {
                count += 1
            }
        }
        return count
    }

    @Benchmark
    fun byteBufferBasedMemoryHandle(): Int {
        var count = 0
        val buf = MemorySegment.ofByteBuffer(outBuffer)

        for (i in 0 until buf.byteSize()) {
            val b: Byte = BYTE_HANDLE.get(buf, i) as Byte
            if (b == EOL_BYTE) {
                count += 1
            }
        }
        return count
    }


    @Benchmark
    fun arrayVectorApi(): Int {
        val vector = ByteVector.SPECIES_PREFERRED
        val d = data
        var offset = 0
        var count = 0
        val length = d.size
        val eol = vector.broadcast(EOL_BYTE.toLong())
        while (offset < length) {
            val vec = vector.fromArray(d, offset)
            val mask = vec.eq(eol)
            count += mask.trueCount()
            offset += vector.length()
        }
        return count
    }
}