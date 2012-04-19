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

package blow.plugin

import groovy.util.logging.Log4j
import com.google.common.eventbus.Subscribe

import org.jclouds.scriptbuilder.domain.CreateRunScript
import org.jclouds.scriptbuilder.domain.StatementList

import blow.util.TraceHelper

import blow.BlowSession
import blow.events.OnAfterClusterCreateEvent

/**
 * Created with IntelliJ IDEA.
 * User: Paolo Di Tommaso
 * Date: 4/3/12
 * Time: 3:22 PM
 * To change this template use File | Settings | File Templates.
 */

@Log4j
@Plugin("bash_profile")
class BashProfile implements ConfHolder {

    // holds the environment variables to be exported
    def exports = [:]

    @Subscribe
    public void configureBashProfile( OnAfterClusterCreateEvent event ) {

        TraceHelper.debugTime( "Add exports to bash_profile", {

            addExports(event.session)

        } )

    }


    protected void addExports( BlowSession pilot ) {

        def target = "~/.bash_profile"
        def list = exports.collect {
            new CreateRunScript.AddExportToFile( it.key, it.value ?: "", target )
        }

        def allExports = new StatementList(list)

        pilot.runStatementOnNodes( allExports )

    }

    @Override
    void setConfProperty(String name, Object value) {
        assert name, "Export name cannot be empty"
        exports .put( name, value ?: "" );
    }
}
