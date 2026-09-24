var now      = java.lang.System.currentTimeMillis();
var oldTicks = globalMap.get('oshiTicks');
var lastTime = globalMap.get('oshiTicksTime');
// SAMPLE_FLOOR_MS is the OSHI tick memoizer window (300ms;
// OshiScriptSurfaceSeamTest#MEMOIZER_EXPIRATION_MS) plus a 150ms margin covering scheduler
// jitter around that window.
var SAMPLE_FLOOR_MS = 450;
var load = -1;
if (!oldTicks || !lastTime) {
    // First invocation, or no usable prior sample yet: prime the cache and take no reading
    // this invocation.
    globalMap.put('oshiTicks', processor.getSystemCpuLoadTicks());
    globalMap.put('oshiTicksTime', now);
} else if ((now - lastTime) >= SAMPLE_FLOOR_MS) {
    // The floor having elapsed does not guarantee the next read is fresh: read once into a
    // new local and vet it against the cached prior array before trusting it.
    var fresh = processor.getSystemCpuLoadTicks();
    var advanced = false;
    for (var i = 0; i < fresh.length; i++) {
        if (fresh[i] !== oldTicks[i]) { advanced = true; break; }
    }
    if (advanced) {
        // A real measurement: compute against the vetted prior ticks, THEN roll the cache
        // forward with the sample already read -- never a second tick read. Rolling forward
        // is what ends this measurement window and starts the next one, and it happens only
        // when a measurement was actually taken.
        load = processor.getSystemCpuLoadBetweenTicks(oldTicks);
        globalMap.put('oshiTicks', fresh);
        globalMap.put('oshiTicksTime', now);
    }
    // else: the platform returned a sample identical to the cached prior array. Leave the
    // cache and the -1 sentinel alone so the next invocation retries, rendering N/A rather
    // than fabricating a zero.
}
// else: a too-soon invocation. The cache is left alone and load stays at the not-sampled
// sentinel.
