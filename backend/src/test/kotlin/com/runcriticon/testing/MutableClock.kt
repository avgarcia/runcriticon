package com.runcriticon.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Reloj mutable para tests de tiempo controlado. */
class MutableClock(
    var instant: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = instant

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
}
