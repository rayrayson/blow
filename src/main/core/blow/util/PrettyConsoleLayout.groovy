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

package blow.util


/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.CoreConstants
import ch.qos.logback.core.LayoutBase

class PrettyConsoleLayout extends LayoutBase<ILoggingEvent> {

    public String doLayout(ILoggingEvent event) {
        StringBuilder buffer = new StringBuilder(128);
        buffer.append("~ ")
        if( event.getLevel() != Level.INFO ) {
            buffer.append( event.getLevel().toString() ) .append(": ")
        }

        return buffer
                .append(event.getFormattedMessage())
                .append(CoreConstants.LINE_SEPARATOR)
                .toString()
    }
}
