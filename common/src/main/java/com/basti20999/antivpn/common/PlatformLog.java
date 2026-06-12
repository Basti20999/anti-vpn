package com.basti20999.antivpn.common;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal logging facade so the core works with java.util.logging (Paper)
 * and SLF4J (Velocity) alike.
 */
public interface PlatformLog {

    void info(String message);

    void warn(String message);

    void warn(String message, Throwable cause);

    static PlatformLog javaUtil(Logger logger) {
        return new PlatformLog() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warning(message);
            }

            @Override
            public void warn(String message, Throwable cause) {
                logger.log(Level.WARNING, message, cause);
            }
        };
    }
}
