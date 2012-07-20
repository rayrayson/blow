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

package blow.operation

import blow.BlowSession
import blow.events.OnAfterClusterStartedEvent
import com.google.common.eventbus.Subscribe

/**
 *  Execute a generic BASH script in the remote nodes
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract public class GenericScriptOp {

    @Conf("run-as-root")
    boolean runAsRoot = false

    def abstract String script();

    @Subscribe
    public void run( OnAfterClusterStartedEvent event ) {

        def BlowSession session = event.session
        def filter = event.session.filterAll();

        session.runScriptOnNodes( script(), filter, runAsRoot )

    }

}
