#!/usr/bin/python
#
# Copyright (c) 2013 Big Switch Networks, Inc.
#
# Licensed under the Eclipse Public License, Version 1.0 (the
# "License"); you may not use this file except in compliance with the
# License. You may obtain a copy of the License at
#
#      http://www.eclipse.org/legal/epl-v10.html
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied. See the License for the specific language governing
# permissions and limitations under the License.
#

import urllib2
import json
import sys


def simple_json_get(url):
    return json.loads(urllib2.urlopen(url).read())


def shorten(s):
    return  s.replace('org.sdnplatform','o.s'

def usage(s):
    sys.stderr.write("Usage:\ngrahTopo.py hostname [port]\n%s" % s)
    sys.stderr.write("\n\n\n\n    writes data to 'hostname.dot' for use with graphviz\n")
    sys.exit(1)


if __name__ == '__main__':

    host='localhost'
    port=8080

    if len(sys.argv) == 1 or sys.argv[1] == '-h' or sys.argv[1] == '--help':
        usage("need to specify hostname")

    host = sys.argv[1]
    if len(sys.argv) > 2:
        port = int(sys.argv[2])

    sys.stderr.write("Connecting to %s:%d ..." % (host,port))
    URL="http://%s:%d/wm/topology/links/json" % (host,port)

    # {
    # "dst-port": 2, 
    # "dst-switch": "00:00:00:00:00:00:00:0a", 
    # "src-port": 3, 
    # "src-switch": "00:00:00:00:00:00:00:0c"
    # }

    links = simple_json_get(URL)
    nodeMap = {}

    sys.stderr.write("Writing to %s.dot ..." % (host))
    f = open("%s.dot" % host, 'w')

    f.write( "digraph Deps {\n")

    for link in links:
        # sys.stderr.write("Discovered module %s\n" % mod)
        if not link['dst-switch'] in nodeMap:
            sw = link['dst-switch']
            nodeMap[sw] = "n%d" % len(nodeMap)
            f.write("     %s [ label=\"dpid=%s\", color=\"blue\"];\n" % (nodeMap[sw], sw))

        if not link['src-switch'] in nodeMap:
            sw = link['src-switch']
            nodeMap[sw] = "n%d" % len(nodeMap)
            f.write("     %s [ label=\"dpid=%s\", color=\"blue\"];\n" % (nodeMap[sw], sw))


        f.write("     %s -> %s [ label=\"%s\"];\n" % (
                nodeMap[link['dst-switch']],
                nodeMap[link['src-switch']],
                "src_port %d --> dst_port %d" % (link['src-port'],link['dst-port'])
                )
                )
        

    f.write("}\n")
    f.close();
    sys.stderr.write("Now type\ndot -Tpdf -o %s.pdf %s.dot\n" % (
        host, host))
        
