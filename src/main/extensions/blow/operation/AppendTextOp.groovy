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

import blow.events.OnAfterClusterStartedEvent
import blow.util.TraceHelper
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import org.jclouds.scriptbuilder.domain.Statements

/**
 * This operation enable to append the content provided to each nodes in the cluster.
 * <p>
 * The most common use case is to add configuration variables to nodes '.bash_profile'
 * or other system configuration files
 *
 *
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
@Operation("append")
class AppendTextOp {

    @Conf String text;
    @Conf String file;
    @Conf String to;
    @Conf('root') boolean rootPermission = false

    private File sourceFile;

    @Subscribe
    public void append( OnAfterClusterStartedEvent event ) {

        if( text == null ) text = "";

        if( sourceFile && sourceFile.exists() ) {
            AppendTextOp.log.debug("Reading file: '$sourceFile'")
            text += "\n" + sourceFile.text
        }

        TraceHelper.debugTime( "Appending to '${to}'", {

            def lines = []
            text.eachLine {  lines.add(it) }
            def appender = Statements.appendFile(to, lines)
            event.session.runStatementOnNodes(appender,null,rootPermission)

        })
    }

    /**
     * This method will invoked before the cluster is started. It has to guarantee that everything
     * will work after the cluster has started
     */
    @Validate
    public void validate() {

        assert to, "Missing  attribute 'to' in 'append' operation"
        assert text || file, "You need to provide at last one of 'text' or 'file' attributes in the 'append' operation configuration"

        if( file ) {
            assert (sourceFile = new File(file)).exists(), "The specified file must exist: '$file'"
        }
    }

}

