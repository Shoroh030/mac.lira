package com.lira.vmest;

final class WatchSignalOrder {
    private WatchSignalOrder() { }

    static int phase(String kind) {
        if ("offer".equals(kind) || "answer".equals(kind)) return 0;
        if ("ice".equals(kind)) return 1;
        return 2;
    }
}
