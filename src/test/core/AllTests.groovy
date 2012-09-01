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

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses
import blow.util.CmdLineTest;


/**
 * All tests 
 * 
 * <p>
 * Give a try to this as an alternative to load all tests from the classpath
 * 
 * http://johanneslink.net/projects/cpsuite.jsp
 * 
 * @author Paolo Di Tommaso
 *
 */

@RunWith(Suite.class)
@SuiteClasses([
    blow.BlowConfigTest.class,
    blow.BlowSessionTest.class,

    blow.builder.BlowConfigBuilderTest.class,
    blow.builder.BuilderExtendedTest.class,

    blow.DynLoaderTest.class,
    blow.DynLoaderFactoryTest.class,
    blow.shell.BlowShellTest.class,

    CmdLineTest.class,
    blow.util.KeyPairBuilderTest.class,
    blow.util.ArrayListMultimapConverterTest.class,
    blow.util.HashBiMapConverterTest.class,
    blow.util.InjectorHelperTest.class,
    blow.util.KeyPairBuilderTest.class,
    blow.util.QuoteStringTokenizerTest.class,

    blow.command.SshCommandTest.class,
    blow.command.S3CommandTest.class,

    blow.operation.AppendTextOpTest.class,
    blow.operation.NfsOpTest.class,
    blow.operation.EbsVolumeOpTest.class,
    blow.operation.SgeOpTest.class,
    blow.operation.AwsCredetentialsOpTest.class,
    blow.operation.PilotHelpTest.class,
    blow.operation.OperationHelperTest.class,
    blow.operation.OperationFactoryTest.class,
    blow.operation.GlusterFSOpTest.class,

	blow.ssh.ScpClientTest.class,
]
)
public class AllTests {

}
