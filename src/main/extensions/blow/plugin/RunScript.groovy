/*
 *   Copyright (c) 2012. Paolo Di Tommaso
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

package blow.plugin

import com.google.common.eventbus.Subscribe
import blow.events.OnAfterClusterCreateEvent
import blow.util.TraceHelper

/**
 * Created with IntelliJ IDEA.
 *
 * User: Paolo Di Tommaso
 * Date: 4/10/12
 * Time: 6:55 PM
 * To change this template use File | Settings | File Templates.
 */

@Plugin("run-script")
class RunScript {

    /** The file containing the shell script to run on the remote nodes */
    @Conf
    String file

    /** The script shell text to run on the remote nodes */
    @Conf
    String text

    /** The 'role' of the nodes on which run the script */
    @Conf
    String role

    @Conf("run-as-root")
    boolean runAsRoot


    @Validate
    public void validate() {

        if( file ) {
            assert new File(file).exists(), "The script file: '$file' does not exists"
        }

        if( !file && !text ) {
            throw new AssertionError("Provide either the 'file' or 'text' attributes for the script plugin")
        }

        if( file && text ) {
            throw new AssertionError("Provide one of the 'file' or 'text' attributes for the script plugin")
        }
    }


    @Subscribe
    public void run( OnAfterClusterCreateEvent event ) {

        def script = file ? new File(file).text : text;
        def session = event.session;

        TraceHelper.debugTime("RunScript", {

            def filter = role ? session.filterByRole( role ) : session.filterAll();

            session.runScriptOnNodes( script, filter, runAsRoot )

        } )

    }

}
