Blow
====
Easy and replicable cluster deployments in the cloud

What is Blow?
-------------
Blow makes it easy provisioning large clusters in public clouds and
provides an open and easily extensible framework to implement custom configurations.

The problem
-----------
Create a cluster in a cloud environment is a terrible burden that requires many different
repetitive tasks to setup all the required files and components. Also a small error can affect
the operation of the whole system, forcing you to repeat the entire configuration and wasting
resources (and money).

What Blow does
--------------
Blow aims to simply the cluster configuration and deployment process in the cloud using a simple
configuration file in which you declare the configuration properties and the required components.

It manages automatically the various installation tasks, making the overall procedure faster,
safer and replicable.

Currently Blow is able to configure:

* /etc/hosts file
* NFS shares
* Password-less SSH
* Security group
* User account
* Mount EBS volumes
* Mount EBS snapshots
* Open Grid Engine (SGE)


Configure your cluster in 5 minutes
-----------------------------------

#### Prerequisites
* You need a JRE/JDK 6 installed on your machine
* An Amazon AWS account (the account key and the secret key)

#### Download Blow

Download the <a href="http://dl.dropbox.com/u/376524/blow/blow.jar">Blow executable package here</a> and
store it somewhere on your machine.

If you are using a *nix system (Linux/MacOSX) grants the executable permission to the downloaded file with the command
`chmod +x ./blow.jar`, after that you will able to run it using the below command:

    ./blow.jar [program arguments]

If you are running a Windows OS, you will have to use the following syntax to run it:

    java -jar blow.jar [program arguments]

#### Provide the cluster configuration

Create a file named `blow.conf` with the following properties and copy it
to the path where you downloaded Blow.

    mycluster {
        access-key = xxxx
        secret-key = yyyy
        region-id = us-east-1
        zone-id = us-east-1d
        image-id =  ami-e565ba8c
        size = 2
        istance-type = t1.micro

        plugin = [
            hosts

            { nfs = {
                path: /soft
                device: /dev/sdh1
            }}

            { sge = { root: /soft/sge6, installation-type: deploy } }
        ]
    }

In the above configuration replace the `access-key` and `secret-property` with your Amazon AWS account credentials.

This will create a cluster with two nodes, based on the `Amazon Linux 2012.03 (x86_64)` public AMI, configure the
`/etc/hosts' file, a shared NFS and install the <a href="http://gridscheduler.sourceforge.net" target="_blank">Open Grid Engine</a>
(formerly Sun Grid Engine) job processor.

### Create the SSH public key

Blow will configure a SSH password-less access for each node in the cluster. For this reason make sure to have
a public-private key files named `id_rsa.pub` and `id_rsa` on your machine in the path `$HOME/.ssh`.

If you have these keys go to the next step, otherwise create it using the command below:

    ssh-keygen -f ~/.ssh/id_rsa -N ''


### Create the cluster

Move the folder that contains the configuration file and the download Blow binary file.

Start the Blow shell entering the command:

    ./blow.jar mycluster


Where `mycluster` is the name used in the configuration file to identify this configuration.

To create your cluster, just enter the command `create` in the Blow shell. It will take some minutes to start
the ami instances and configure them. You may monitor the instances creation in the
<a href="https://console.aws.amazon.com" target="_blank">Amazon management console</a>.

When the configuration is complete, you can access any nodes in the cluster using a SSH client.
In the Blow shell terminal enter the command `listnodes` to get IP address of each nodes.
It will return something like this:

    i-75589112;    50.17.93.165; RUNNING; mycluster; master
    i-135a9374;     23.20.68.63; RUNNING; mycluster; worker

In the above list you can read (from left to right) the instance id, the public IP address, the node status,
the cluster name and finally the _role_ of the node in the cluster.

To access any node in the cluster, simply open a new terminal window an enter the following command

    ssh 23.20.68.63

(obviously replacing the IP address with the one available in your cluster).


### Terminate the cluster

When you have done, remember to stop you cluster nodes using the command `terminate` from the Blow shell.


That's all!

Build from source
-----------------

#### Prerequisites
* Groovy 1.8.6 or higher.
* Make sure is defined the variable `GROOVY_HOME` or define it is missing

The build script is written on Groovy and uses the Ant Builder feature to build the project.
All required dependencies are defined in the Maven `pom.xml` file, and all the required libraries
will be download automatically by the script using the provided Maven Ant task integration.

Launch the build script running the following command in the project root:

    groovy ./build.groovy

#### Build target

The script create the self-contained executable JAR package as default target.

The following other targets can be provided optionally on the build script command line:
* info:     Print out the build variables.
* clean:    Remove all temporary files in the build folder.
* compile:  Compile all the sources.
* run:      Launch the application shell (useful to test changes in the sources).
* pack:     Create the big distribution jar package.
* all (default)



Development status
------------------
Blow is in early stage of development. It has been tested with the Amazon Linux 2012.03 (x86_64)
public ami (ami-e565ba8c). It may not work properly using different Linux distribution/ami.

Our commitment is to support the most common used distributions (RedHat, Ubuntu and Suse)

Blow is developed in <a href="http://groovy.codehaus.org" target="_blank">Groovy</a>.
The cloud connectivity is provided by <a href="http://www.jclouds.org" target="_blank">JClouds</a>
and the cluster configuration files are managed using <a href="https://github.com/typesafehub/config" target="_blank">Typesafe Config</a>.

This project is partially inspired to <a href="http://web.mit.edu/star/cluster/" target="_blank">StarCluster</a>.


Contact
-------
Paolo Di Tommaso - paolo (dot) ditommaso (at) gmail (dot) com