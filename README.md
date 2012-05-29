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

* /etc/hosts file
* NFS shares
* GlusterFS shares
* Password-less SSH
* Security group
* User account
* Mount EBS volumes
* Mount EBS snapshots
* Open Grid Engine (SGE)


Configure your cluster in 5 minutes
-----------------------------------

#### 0. Prerequisites
* You need a JRE/JDK 6 installed on your machine
* An Amazon AWS account (the account key and the secret key)

#### 1. Download Blow

Download the <a href="http://dl.dropbox.com/u/376524/blow/blow.jar">Blow executable package here</a> and
store it somewhere on your machine.

If you are using a *nix system (Linux/MacOSX) grants the executable permission to the downloaded file with the command
`chmod +x ./blow.jar`, after that you will able to run it using the below command:

    ./blow.jar [program arguments]

If you are running a Windows OS, you will have to use the following syntax to run it:

    java -jar blow.jar [program arguments]

#### 2. Provide the cluster configuration

Create a file named `blow.conf` with the following properties and copy it
to the path where you downloaded Blow.

    mycluster {
        access-key = xxxx
        secret-key = yyyy
        region-id = us-east-1
        zone-id = us-east-1d
        image-id =  ami-e565ba8c
        size = 2
        instance-type = t1.micro

        operations = [
            hostname

            { nfs = {
                path: /soft
                device: /dev/sdh1
            }}

            { sge = { root: /soft/sge6, installation-type: deploy } }
        ]
    }

In the above configuration replace the `access-key` and `secret-property` with your Amazon AWS account credentials.

This will create a cluster with two nodes, based on the `Amazon Linux 2012.03 (x86_64)` public AMI, configure the
`/etc/hosts` file, a shared NFS and install the <a href="http://gridscheduler.sourceforge.net" target="_blank">Open Grid Engine</a>,
formerly Sun Grid Engine (SGE) batch-queuing system for distributed resource management.

### 3. Start the cluster

Move the folder that contains the configuration file and the download Blow binary file.

Start the Blow shell entering the command:

    ./blow.jar


Note: Windows users have to use the syntax:

    java -jar blow.jar


If you don't a key-pair in your $HOME/.ssh pair, it will ask you to create a new key-pair in the current local directory.
Confirm entering `y` and press enter to continue.

Launch the cluster by entering the command `start` at the Blow prompt. It will take a few minutes to start
the ami instances and configure them. You may monitor the instances creation in the
<a href="https://console.aws.amazon.com" target="_blank">Amazon management console</a>.

### 4. Access the remote nodes

When the configuration is complete, you can access any nodes in the cluster using any SSH client or the integrated SSH client.
In the Blow shell terminal enter the command `listnodes` to get IP address of each nodes.
A list similar to the one shown below will be displayed:

    i-75589112;    50.17.93.165; RUNNING; mycluster; master
    i-135a9374;     23.20.68.63; RUNNING; mycluster; worker

In the above list you can read (from left to right) the instance id, the public IP address, the node status,
the cluster name and finally the _role_ of the node in the cluster.

To access any node in the cluster, simply enter the following command in the Blow shell:

    ssh 23.20.68.63

(obviously replacing the IP address with the one available in your cluster).


### 5. Terminate the cluster

When you have done, remember to stop you cluster nodes using the command `terminate` from the Blow shell.

That's all!

Documentation
-------------

Blow documentation is available at this link: http://readthedocs.org/docs/blow/en/latest/

License
-------

The *Blow* framework source code is released under the GNU GPL3 License.

Contact
-------
Paolo Di Tommaso - paolo (dot) ditommaso (at) gmail (dot) com