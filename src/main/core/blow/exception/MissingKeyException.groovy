/*
 * Copyright (c) 2012. Paolo Di Tommaso.
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

package blow.exception

/**
 *  This exception is raised when a key file declared in the
 *  configuration does not exists
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

class MissingKeyException extends BlowConfigException {

    def keyFile

    MissingKeyException( def keyFile ) {
        super("The following key file does not exist: '$keyFile'")
        this.keyFile = keyFile?.toString()
    }


}
