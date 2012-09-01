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

import org.codehaus.groovy.runtime.InvokerHelper
import org.jclouds.compute.callables.RunScriptOnNodeUsingSsh
import org.jclouds.compute.domain.ExecResponse

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TraceRunUsingSsh extends groovy.lang.DelegatingMetaClass {

    TraceRunUsingSsh()
    {
        super(RunScriptOnNodeUsingSsh.class);
        initialize()
        InvokerHelper.metaRegistry.setMetaClass(RunScriptOnNodeUsingSsh.class, this)
    }

    public Object invokeMethod(Object obj, String methodName, Object[] args)
    {
        super.println "************ node: " + super.getNode()

        def result = super.invokeMethod(obj, methodName, args)
        if( methodName == "runCommand" ) {
            super.println ">>> runCommand ... "
            ExecResponse response = result
        }
        return result
    }

}
