import blow.shell.BlowShell
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.encoder.LayoutWrappingEncoder
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import blow.util.PrettyConsoleLayout

/*
* Copyright (c) 2012, the authors.
*
*   This file is part of Blow.
*
*   Blow is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   Blow is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with Blow.  If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * Application logging configuration
 * <p>
 * Read more here http://logback.qos.ch/manual/index.html
 *
 *
 * @author Paolo Di Tommaso
 */

/**
 * Defines the console appender level on program cmdline arguments
 */

def debugPackages = []
def tracePackages = []

def consoleLevel = {

    if( BlowShell.options?.debug instanceof String ) {
        debugPackages = BlowShell.options.debug.split(',')
    }

    if( BlowShell.options?.trace instanceof String ) {
        tracePackages = BlowShell.options.trace.split(',')
    }


    if( BlowShell.options?.trace ) { return Level.TRACE }
    else if( BlowShell.options?.debug ) { return Level.DEBUG }
    else { return Level.INFO }

}


appender("console", ConsoleAppender) {
    encoder(LayoutWrappingEncoder) {
        layout(PrettyConsoleLayout)
    }

    filter(ThresholdFilter) {
        level = consoleLevel()
    }
}


appender("rolling", RollingFileAppender) {
    file = ".blow.log"
    rollingPolicy(TimeBasedRollingPolicy) {
        fileNamePattern = ".blow-%d{yyyy-MM-dd}.%i.log"
        timeBasedFileNamingAndTriggeringPolicy(SizeAndTimeBasedFNATP) {
            maxFileSize = "10MB"
        }
    }
    encoder(PatternLayoutEncoder) {
        pattern = "%date{ISO8601} [%thread] %-5level %logger{25} - %msg%n"
    }

}

if( BlowShell.options?.trace ) {
    logger("blow", Level.TRACE, ['console','rolling'], false)
}
else  {
    logger("blow", Level.DEBUG, ['console','rolling'], false)
}

root(Level.INFO, ["console","rolling"])
logger("net.schmizz.sshj", Level.WARN, ["console","rolling"], false)
logger("org.jclouds.ec2.xml", Level.ERROR, ["console","rolling"], false)
/*
 * Add packages to debug or trace declared dynamically on the command line
 */
debugPackages.each {
    logger(it, Level.DEBUG, ["console","rolling"], false)
}

tracePackages.each {
    logger(it, Level.TRACE, ["console","rolling"], false)
}