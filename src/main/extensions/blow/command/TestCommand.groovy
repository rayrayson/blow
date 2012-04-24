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

package blow.command

import blow.plugin.S3cmd
import blow.shell.AbstractShellCommand

/**
 * Created with IntelliJ IDEA.
 * Author: ptommaso
 * Date: 4/4/12
 * Time: 12:11 PM
 * To change this template use File | Settings | File Templates.
 */
class TestCommand extends AbstractShellCommand {

    public String getName() { "tests3cmd" }

    @Override
    void invoke() {
         new S3cmd() .execute( shell.session )
    }


//    @Override
//    void invoke() {
//        def appender = new AppendText( text: "export SOFT_HOME=/soft", file:"labtools.profile", to: "~/.bash_profile" )
//        appender.append( new OnAfterClusterCreateEvent( blow: shell.blow ) )
//    }
}
