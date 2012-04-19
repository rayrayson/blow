/*
 * Copyright (c) 2012. Paolo Di Tommaso
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

package blow.util;


import org.apache.log4j.Logger

public class TraceHelper {

	static final Logger log = Logger.getLogger(TraceHelper)



	/**
	 * Trace the execution time 
	 * 
	 * @param task
	 */
	static def void debugTime( Closure task ) { debugTime(null, task) }
	
	/**
	 * Trace the execution time 
	 * 
	 * @parama message 
	 * @param task
	 */
	static def void debugTime( String message, Closure task ) {
		def startTime = System.currentTimeMillis()
		if( message ) log.debug message
		
		try {
			task.call()
		}
		finally {
            def duration = asDuration(System.currentTimeMillis() - startTime)
			def elapsed = "Elapsed: ${duration}"
			message = (message) ? "$message - DONE ($elapsed)" : elapsed
			log.debug message
		} 
		
	}



    /**
     * Converts an amount of time (specified as millisecond) in a duration string. For example:
     *
     * @param millis the elapsed time in milliseconds
     * @return time period as string e.g. 100 ms, 10 secs, 1 hour ..
     */
    public static String asDuration( long millis ) {

        if( millis < 1000 ) {
            return millis + " ms";
        }

        double secs = millis / (double)1000;
        if( secs < 60 ) {
            return String.format("%.0f sec", secs);
        }

        double mins = secs / 60;
        if( mins < 60 ) {
            double i = Math.floor(mins);
            double r = ((mins-i) * 60);
            String result = String.format( "%.0f min", i);
            if( r > 0 ) {
                result += String.format( " %.0f sec", r);
            }
            return result;
        }

        double hours = mins / 60;
        if( hours < 24 ) {
            double i = Math.floor(hours);
            double r = ((hours-i) * 60);
            String result = String.format( "%.0f hour", i);
            if( r > 0 ) {
                result += String.format( " %.0f min", r);
            }
            return result;
        }

        double days = hours / 24;
        double i = Math.floor(days);
        double r = ((days-i) * 24);
        String result = String.format( "%.0f day", i);
        if( r > 0 ) {
            result += String.format( " %.0f hour", r);
        }
        return result;

    }
	
	
}
