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
import blow.util.TraceHelper
import com.google.common.collect.ImmutableMap
import com.google.common.eventbus.Subscribe
import groovy.util.logging.Slf4j
import org.jclouds.scriptbuilder.domain.CreateRunScript
import org.jclouds.scriptbuilder.domain.OsFamily
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.domain.StatementList
import org.jclouds.scriptbuilder.util.Utils

import static com.google.common.base.Preconditions.checkNotNull

/**
 * Append a list of exported variables to the '~/.bash_profile' file to the remote instances
 *
 * @author Paolo Di Tommaso
 */

@Slf4j
@Operation("exports")
class ProfileExports implements ConfHolder {

    // holds the environment variables to be exported
    def exports = [:]

    @Subscribe
    public void configureBashProfile( OnAfterClusterStartedEvent event ) {

        TraceHelper.debugTime( "Add exports to bash_profile", {

            addExports(event.session)

        } )

    }


    protected void addExports( BlowSession pilot ) {

        def target = "~/.bash_profile"
        def list = exports.collect {
            new AddExportToFile( it.key, it.value ?: "", target )
        }

        def allExports = new StatementList(list)

        pilot.runStatementOnNodes( allExports )

    }

    @Override
    void setConfProperty(String name, Object value) {
        assert name, "Export name cannot be empty"
        exports .put( name, value ?: "" );
    }

    /**
     * Modified version of {@link CreateRunScript.AddExportToFile}
     */
    private static class AddExportToFile implements Statement {
        final String export;
        final String value;
        final String file;

        public AddExportToFile(String export, String value, String file) {
            this.export = checkNotNull(export, "export");
            this.value = checkNotNull(value, "value");
            this.file = checkNotNull(file, "file");
        }

        public static final Map<OsFamily, String> OS_TO_EXPORT_PATTERN = ImmutableMap.of(OsFamily.UNIX,
                "echo \"export {export}='{value}'\">>{file}\n", OsFamily.WINDOWS,
                "echo set {export}={value}>>{file}\r\n");

        @Override
        public Iterable<String> functionDependencies(OsFamily family) {
            return Collections.emptyList();
        }

        @Override
        public String render(OsFamily family) {
            return CreateRunScript.addSpaceToEnsureWeDontAccidentallyRedirectFd(Utils.replaceTokens(OS_TO_EXPORT_PATTERN.get(family),
                    ImmutableMap.of("export", export, "value", value, "file", file)));
        }
    }

}
