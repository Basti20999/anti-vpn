package com.basti20999.antivpn.velocity;

import com.basti20999.antivpn.common.PlatformLog;
import org.slf4j.Logger;

final class Slf4jLog implements PlatformLog {

    private final Logger logger;

    Slf4jLog(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        logger.warn(message, cause);
    }
}
