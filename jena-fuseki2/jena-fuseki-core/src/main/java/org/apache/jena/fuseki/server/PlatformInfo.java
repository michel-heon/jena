/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.fuseki.server;

import java.io.IOException;
import java.util.function.Function;

import org.apache.jena.atlas.logging.FmtLog;
import org.slf4j.Logger;

/** Platform inforamtion - OS and JVM */
/*package*/ class PlatformInfo {

    public static void main(String ...args) throws IOException {
        long maxMem = Runtime.getRuntime().maxMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long usedMem = totalMem - freeMem;
        Function<Long, String> f = PlatformInfo::strNum2;
        System.out.printf("max=%s  total=%s  used=%s  free=%s\n", f.apply(maxMem), f.apply(totalMem), f.apply(usedMem), f.apply(freeMem));
    }

    /** System details */
    /*package*/ static void logDetailsSystem(Logger log) {
        String prefix = "  ";
        long maxMem = Runtime.getRuntime().maxMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long usedMem = totalMem - freeMem;
        Function<Long, String> f = PlatformInfo::strNum2;

        long pid = getProcessId();
        FmtLog.info(log, "%sMemory: %s",        prefix, f.apply(maxMem));
        //FmtLog.info(log, "%sMemory: max=%s  total=%s  used=%s  free=%s", prefix, f.apply(maxMem), f.apply(totalMem), f.apply(usedMem), f.apply(freeMem));
        FmtLog.info(log, "%sJava:   %s",        prefix, System.getProperty("java.version"));
        FmtLog.info(log, "%sOS:     %s %s %s",  prefix, System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
        if ( pid != -1)
            FmtLog.info(log, "%sPID:    %s", prefix, pid);
    }

    /** JVM details section. */
    /*package*/ static void logDetailsJVM(Logger log) {
        String prefix = "  ";
        logOne(log, prefix, "java.vendor");
        logOne(log, prefix, "java.home");
        logOne(log, prefix, "java.runtime.version");
        logOne(log, prefix, "java.runtime.name");
        logOne(log, prefix, "user.language");
        logOne(log, prefix, "user.timezone");
        logOne(log, prefix, "user.country");
        logOne(log, prefix, "user.dir");
    }

    private static void logOne(Logger log, String prefix, String property) {
        if ( prefix == null )
            prefix = "";
        FmtLog.info(log, "%s%-20s = %s", prefix, property, System.getProperty(property));
    }

    private static long getProcessId() {
        return ProcessHandle.current().pid();
    }

    /** Create a human-friendly string for a number based on Kilo/Mega/Giga/Tera (powers of 10) */
    private static String strNum10(long x) {
        if ( x < 1_000 )
            return Long.toString(x);
        if ( x < 1_000_000 )
            return String.format("%.1fK", x/1000.0);
        if ( x < 1_000_000_000 )
            return String.format("%.1fM", x/(1000.0*1000));
        if ( x < 1_000_000_000_000L )
            return String.format("%.1fG", x/(1000.0*1000*1000));
        return String.format("%.1fT", x/(1000.0*1000*1000*1000));
    }

    /** Create a human-friendly string for a number based on Kibi/Mebi/Gibi/Tebi (powers of 2) */
    private static String strNum2(long x) {
        // https://en.wikipedia.org/wiki/Kibibyte
        if ( x < 1024 )
            return Long.toString(x);
        if ( x < 1024*1024 )
            return String.format("%.1f KiB", x/1024.0);
        if ( x < 1024*1024*1024 )
            return String.format("%.1f MiB", x/(1024.0*1024));
        if ( x < 1024L*1024*1024*1024 )
            return String.format("%.1f GiB", x/(1024.0*1024*1024));
        return String.format("%.1fTiB", x/(1024.0*1024*1024*1024));
    }
}
