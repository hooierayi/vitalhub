package com.smarthealth.vitalhub.foundation.device.protocol

class ExpandableRingByteBuffer(
    initialCapacity: Int = 2_048,
    private val maxCapacity: Int = 256 * 1_024,
) {
    private var bytes = ByteArray(initialCapacity.coerceAtLeast(1))
    private var readIndex = 0
    private var writeIndex = 0

    var size: Int = 0
        private set
    val capacity: Int get() = bytes.size

    init {
        require(maxCapacity >= bytes.size)
    }

    fun write(source: ByteArray) {
        if (source.isEmpty()) return
        ensureCapacity(size + source.size)
        source.forEach { value ->
            bytes[writeIndex] = value
            writeIndex = (writeIndex + 1) % bytes.size
        }
        size += source.size
    }

    operator fun get(offset: Int): Byte {
        require(offset in 0 until size) { "offset=$offset, size=$size" }
        return bytes[(readIndex + offset) % bytes.size]
    }

    fun copy(offset: Int, length: Int): ByteArray {
        require(offset >= 0 && length >= 0 && offset + length <= size)
        return ByteArray(length) { get(offset + it) }
    }

    fun skip(length: Int) {
        require(length in 0..size)
        readIndex = (readIndex + length) % bytes.size
        size -= length
        if (size == 0) {
            readIndex = 0
            writeIndex = 0
        }
    }

    fun clear() {
        readIndex = 0
        writeIndex = 0
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= bytes.size) return
        require(required <= maxCapacity) {
            "Protocol buffer overflow: required=$required, max=$maxCapacity"
        }
        var nextCapacity = bytes.size
        while (nextCapacity < required) {
            nextCapacity = (nextCapacity * 2).coerceAtMost(maxCapacity)
        }
        val expanded = copy(0, size).copyOf(nextCapacity)
        bytes = expanded
        readIndex = 0
        writeIndex = size
    }
}

class ReadOnlyRingBuffer internal constructor(
    private val source: ExpandableRingByteBuffer,
) {
    val size: Int get() = source.size
    operator fun get(offset: Int): Byte = source[offset]
    fun unsigned(offset: Int): Int = get(offset).toInt() and 0xFF
    fun copy(offset: Int, length: Int): ByteArray = source.copy(offset, length)

    fun indexOf(vararg headers: ByteArray): Int {
        for (offset in 0 until size) {
            for (header in headers) {
                if (offset + header.size <= size && header.indices.all { get(offset + it) == header[it] }) {
                    return offset
                }
            }
        }
        return -1
    }
}
