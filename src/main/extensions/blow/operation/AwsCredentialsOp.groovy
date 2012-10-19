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

/**
 *  Install the AWS credentials to the target environment.
 *  <p>
 *  Practically it add the following variables in the remote nodes:
 *  <li>AWS_ACCESS_KEY_ID</li>
 *  <li>AWS_SECRET_ACCESS_KEY</li>
 *  <li>AWS_CREDENTIALS_FILE</li>
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@Operation('awsCredentials')
class AwsCredentialsOp extends GenericScriptOp {

    private BlowSession session

    @Override
    String script() {

        """\
        cat >> ~/.bash_profile << 'EOF'
        export AWS_ACCESS_KEY=${session.conf.accessKey}
        export AWS_SECRET_KEY=${session.conf.secretKey}
        export AWS_CREDENTIALS_FILE=\$HOME/.aws-credentials
        EOF

        cat > \$HOME/.aws-credentials <<'EOF'
        AWSAccessKeyId=${session.conf.accessKey}
        AWSSecretKey=${session.conf.secretKey}
        EOF
        """
        .stripIndent()

    }
}
