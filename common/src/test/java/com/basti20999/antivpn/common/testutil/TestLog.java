package com.basti20999.antivpn.common.testutil;

import com.basti20999.antivpn.common.PlatformLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TestLog implements PlatformLog {

    public final List<String> infos = new CopyOnWriteArrayList<>();
    public final List<String> warns = new CopyOnWriteArrayList<>();

    @Override
    public void info(String message) {
        infos.add(message);
    }

    @Override
    public void warn(String message) {
        warns.add(message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        warns.add(message + " (" + cause + ")");
    }
}
