============================
Getting started with Eclipse
=============================

First, at the shell, create the .project files with `make eclipse`
% make eclipse

Second, in Eclipse, create a new workspace:
Click File -> Switch WorkSpace -> Other and then enter the name of the new workspace, e.g. 
    "Workspace.net-virt-platform"

Third, once in the new workspace, import all of the eclipse projects
Click File -> Import -> General -> Existing Projects into Workspace -> Next
    in the "Select Root Directory" dialog, type in or navigate to your checkout of the
    net-virt-platform base directory, e.g., ~/git/net-virt-platform .
Eclipse should automatically find four eclipse projects: 
    * cli
    * django_cassandra_backend
    * sdncon
    * sdnplatform
Make sure they are all checked (the default) and click "Finish"

If you are looking to do python development, it is recommended that
you install the Eclipse "pydev" modules, as documented at http://pydev.org.

