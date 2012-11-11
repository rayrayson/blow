Blow
====
Easy and replicable cluster configuration and deployments in the cloud

What is Blow?
-------------
Blow makes it easy provisioning large clusters in public clouds and
provides an open and easily extensible framework to implement custom configurations.

The problem
-----------
Create a cluster in a cloud environment is a terrible burden that requires many different
repetitive tasks to setup all required files and components. Also a small error can affect
the operation of the whole system, forcing to repeat the entire configuration and wasting
resources (and money).

What Blow does
--------------
Blow aims to simplify the cluster configuration and deployment process in the cloud using a simple
configuration file in which declares the configuration properties and the required components.

It manages automatically the various installation tasks, making the overall procedure faster,
safer and replicable.

Currently Blow is able to configure:

* Instances hostname
* NFS shares
* GlusterFS shares
* Password-less SSH
* Security group
* User account
* EBS volumes
* EBS snapshots
* Open Grid Engine (SGE)
* Hadoop cluster

Moreover it provides:

* Integrated S3 client
* Integrated SSH shell
* Fast Data Transfer (FDT) - http://monalisa.cern.ch/FDT/


Configure your cluster in 5 minutes
-----------------------------------

#### 0. Prerequisites
* You need a JRE/JDK 6 (or higher version) installed on your machine
* An Amazon AWS account (the account key and the secret key)

#### 1. Download Blow

Download the <a href="http://dl.dropbox.com/u/376524/blow/blow.run">Blow executable package here</a> and
store it somewhere on your machine.

If you are using a *nix system (Linux/MacOSX) grants the executable permission to the downloaded file with the command
`chmod +x ./blow.run`, after that you will able to run it using the below command:

    ./blow.run [program arguments]

If you are running a Windows OS, you will have to use the following syntax to run it:

    java -jar blow.run [program arguments]

#### 2. Provide the cluster configuration

Create a file named `blow.conf` with the following properties and copy it
to the path where you downloaded Blow.

    mycluster {
        accessKey '123...'
        secretKey 'xyz...'
        regionId 'us-east-1'
        zoneId 'us-east-1d'
        imageId '﻿ami-6d555119'
        instanceNum 3
        instanceType 't1.micro'

        operations {
    	    nfs(path: '/soft')
            sge(path: '/soft/sge6', installationMode: 'deploy')
        }
    }

In the above configuration replace the `accessKey` and `secretKey` with your Amazon AWS account credentials.

This will create a cluster with three nodes, based on the `Amazon Linux 2012.03 (x86_64)` public AMI, configure the
`/etc/hosts` file, a shared NFS and install the <a href="http://gridscheduler.sourceforge.net" target="_blank">Open Grid Engine</a>,
formerly Sun Grid Engine (SGE) batch-queuing system for distributed resource management.

### 3. Start the cluster

Move the folder that contains the configuration file and the download Blow binary file.

Start the Blow shell entering the command:

    ./blow.run


Note: Windows users have to use the syntax:

    java -jar blow.run


If you don't a key-pair in your $HOME/.ssh pair, it will ask you to create a new key-pair in the current local directory.
Confirm entering `y` and press enter to continue.

Launch the cluster by entering the command `start` at the Blow prompt. It will take a few minutes to start
the ami instances and configure them. You may monitor the instances creation in the
<a href="https://console.aws.amazon.com" target="_blank">Amazon management console</a>.

### 4. Access the remote nodes

When the configuration is complete, you can access any nodes in the cluster using any SSH client or the integrated SSH client.
In the Blow shell terminal enter the command `listnodes` to get IP address of each nodes.
A list similar to the one shown below will be displayed:

    master  (i-e808e592); 23.22.132.166; RUNNING
    worker1 (i-ea05e890); 23.23.29.40  ; RUNNING
    worker2 (i-e805e892); 50.19.32.102 ; RUNNING

In the above list you can read (from left to right) the node name, the instance id (as provided by Amazon), the public IP address
and the node status.

To access any node in the cluster, simply enter in the Blow shell the `ssh` command followed by the node name. For example:

    ssh master

Once you have accessed the remote node, enter the `qhost` command to verify the SGE cluster is up and running:

    [master ~]$ qhost
    HOSTNAME                ARCH         NCPU  LOAD  MEMTOT  MEMUSE  SWAPTO  SWAPUS
    -------------------------------------------------------------------------------
    global                  -               -     -       -       -       -       -
    master                  linux-x64       1  0.01  595.4M   53.8M     0.0     0.0
    worker1                 linux-x64       1  0.37  595.4M   50.1M     0.0     0.0
    worker2                 linux-x64       1  0.02  595.4M   43.4M     0.0     0.0


### 5. Terminate the cluster

When you have done, remember to stop you cluster nodes using the command `terminate` from the Blow shell.

That's all!

Development status
------------------
Blow is in early stages of development. It has been tested with the following AMIs:

* Amazon Linux 2012.03
* Amazon Linux 2012.09
* Cloud BioLinux (Ubuntu 12.04)
* Scientific Linux 6.3
* Fedora 16

It may not work properly using a different Linux distribution/ami.

Our commitment is to support the most commonly used Linux distributions (RedHat, Ubuntu and Suse) in future releases.

Documentation
-------------

Blow documentation is available at this link: http://readthedocs.org/docs/blow/en/latest/

License
-------

The *Blow* framework source code is released under the GNU GPL3 License.

Contact
-------
Paolo Di Tommaso - paolo (dot) ditommaso (at) gmail (dot) com