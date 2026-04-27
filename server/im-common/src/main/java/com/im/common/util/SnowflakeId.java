package com.im.common.util;

public class SnowflakeId {

    private static final long START_TIMESTAMP = 1700000000000L;
    private static final long DATA_CENTER_BITS = 5;
    private static final long MACHINE_BITS = 5;
    private static final long SEQUENCE_BITS = 12;

    private static final long MAX_DATA_CENTER = ~(-1L << DATA_CENTER_BITS);
    private static final long MAX_MACHINE = ~(-1L << MACHINE_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long MACHINE_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_SHIFT = SEQUENCE_BITS + MACHINE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_BITS + DATA_CENTER_BITS;

    private final long dataCenterId;
    private final long machineId;
    private long sequence = 0;
    private long lastTimestamp = -1;

    public SnowflakeId(long dataCenterId, long machineId) {
        if (dataCenterId > MAX_DATA_CENTER || dataCenterId < 0) {
            throw new IllegalArgumentException("dataCenterId out of range");
        }
        if (machineId > MAX_MACHINE || machineId < 0) {
            throw new IllegalArgumentException("machineId out of range");
        }
        this.dataCenterId = dataCenterId;
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                while ((timestamp = System.currentTimeMillis()) <= lastTimestamp) {
                    // wait
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_SHIFT)
                | (machineId << MACHINE_SHIFT)
                | sequence;
    }
}
