package com.microsoft.applicationinsights.internal.etw;

import java.io.File;
import java.io.IOException;

import com.google.common.base.Preconditions;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO move native methods to abstract class?
public class EtwProvider {
    private static final String LIB_FILENAME_32_BIT = "applicationinsights-java-etw-provider-x86.dll";
    private static final String LIB_FILENAME_64_BIT = "applicationinsights-java-etw-provider-x86-64.dll";

    private static Logger LOGGER;
    static {
        if (SystemUtils.IS_OS_WINDOWS) {
            LOGGER = LoggerFactory.getLogger(EtwProvider.class);
            File dllPath = null;
            try {
                dllPath = loadLibrary();
                LOGGER.info("EtwProvider initialized. Lib path={}", dllPath.getAbsolutePath());
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                try {
                    LOGGER.error("Error initializing EtwProvider", t);
                    if (dllPath != null) {
                        dllPath.deleteOnExit();
                    }
                } catch (ThreadDeath td) {
                    throw td;
                } catch (Throwable chomp) {
                    // ignore
                }
            }
        } else {
            LoggerFactory.getLogger(EtwProvider.class).info("Non-Windows OS. Loading ETW library skipped.");
        }
    }

    static void load() {
        // triggers static initializer
    }

    private static File loadLibrary() throws IOException {
        final String fileName = getDllFilenameForArch();

        final File targetDir = DllFileUtils.buildDllLocalPath();
        final File dllPath = new File(targetDir, fileName);

        if (!dllPath.exists()) {
            DllFileUtils.extractToLocalFolder(dllPath, fileName);
        }

        System.load(dllPath.getAbsolutePath());

        return dllPath;
    }

    static String getDllFilenameForArch() {
        final boolean is32bit = StringUtils.defaultIfEmpty(System.getProperty("os.arch"), "null").equalsIgnoreCase("x86");
        return is32bit ? LIB_FILENAME_32_BIT : LIB_FILENAME_64_BIT;
    }

    private native void cppInfo(String logger, String message, String extensionVersion, String subscriptionId, String appName, String resourceType);
    private native void cppError(String logger, String message, String stacktrace, String extensionVersion, String subscriptionId, String appName, String resourcetype);
    private native void cppCritical(String logger, String message, String stacktrace, String extensionVersion, String subscriptionId, String appName, String resourcetype);

    private static String s_extensionVersion = "ev";
    private static String s_subscriptionId = "sid";
    private static String s_appName = "app";
    private static String s_resourceType = "rt";

    public static void setExtensionVersion(String version) {
        Preconditions.checkNotNull(version, "version cannot be null");
        s_extensionVersion = version;
    }

    public static void setSubscriptionId(String subscriptionId) {
        Preconditions.checkNotNull(subscriptionId, "subscriptionId cannot be null");
        EtwProvider.s_subscriptionId = subscriptionId;
    }

    public static void setAppName(String appName) {
        Preconditions.checkNotNull(appName, "appName cannot be null");
        EtwProvider.s_appName = appName;
    }

    public static void setResourceType(String resourceType) {
        Preconditions.checkNotNull(resourceType, "resourceType cannot be null");
        EtwProvider.s_resourceType =  resourceType;
    }

    public void info(String logger, String messageFormat, Object... messageArgs) {
        cppInfo(logger, String.format(messageFormat, messageArgs), s_extensionVersion, s_subscriptionId, s_appName, s_resourceType);
    }

    public void error(String logger, Throwable throwable, String messageFormat, Object... messageArgs) {
        cppError(logger, String.format(messageFormat, messageArgs), ExceptionUtils.getStackTrace(throwable), s_extensionVersion, s_subscriptionId, s_appName, s_resourceType);
    }

    public void error(String logger, String messageFormat, Object... messageArgs) {
        cppError(logger, String.format(messageFormat, messageArgs), "", s_extensionVersion, s_subscriptionId, s_appName, s_resourceType);
    }

    public void critical(String logger, Throwable throwable, String messageFormat, Object... messageArgs) {
        cppCritical(logger, String.format(messageFormat, messageArgs), ExceptionUtils.getStackTrace(throwable), s_extensionVersion, s_subscriptionId, s_appName, s_resourceType);
    }

    public void critical(String logger, String messageFormat, Object... messageArgs) {
        cppCritical(logger, String.format(messageFormat, messageArgs), "", s_extensionVersion, s_subscriptionId, s_appName, s_resourceType);
    }
}