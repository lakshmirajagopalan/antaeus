package io.pleo.antaeus.app

import mu.KotlinLogging
import org.joda.time.DateTime
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TimedScheduler(private val clock: Clock = Clock.systemDefaultZone()) {
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1);
    private val logger = KotlinLogging.logger {}

    fun schedule(times: Sequence<DateTime>, task: () -> Unit): Unit {
        val iterator = times.map { it.millis }.dropWhile { it < clock.millis() }.iterator()
        fun doSchedule() {
            if(iterator.hasNext()) {
                scheduler.schedule({
                    doSchedule()

                    logger.info("Starting to execute task at ${clock.instant()}")

                    task()
                }, iterator.next() - clock.millis(), TimeUnit.MILLISECONDS)
            }
        }
        doSchedule()
    }
    companion object {
        fun startOfEveryMonth(clock: Clock = Clock.systemDefaultZone()) = generateSequence (
                DateTime(org.joda.time.Instant(clock.instant().toEpochMilli()))
                .withDayOfMonth(1)
                .plusMonths(1)
                .withTimeAtStartOfDay()
        ) { current -> current.plusMonths(1) }
    }
}
