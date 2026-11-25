package com.mikuwwl.wakeguard.alarm

/** Owner confined to one thread. Only its current resource may act on callbacks. */
internal class PlaybackOwner<T : AutoCloseable> : AutoCloseable {
    private var eventId: Long? = null
    private var resource: T? = null

    fun start(eventId: Long, create: () -> T): T? {
        if (this.eventId == eventId) return null
        close()
        return create().also {
            resource = it
            this.eventId = eventId
        }
    }

    fun owns(candidate: T) = resource === candidate

    override fun close() {
        val old = resource
        resource = null
        eventId = null
        old?.close()
    }
}
