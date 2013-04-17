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
    return  s.replace('org.sdnplatform','o.s')

def usage(s):
    sys.stderr.write("Usage:\ngrahDeps.py hostname [port]\n%s" % s)
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
    URL="http://%s:%d/wm/core/module/loaded/json" % (host,port)

    deps = simple_json_get(URL)
    serviceMap = {}
    nodeMap = {}
    nodeCount = 0

    sys.stderr.write("Writing to %s.dot ..." % (host))
    f = open("%s.dot" % host, 'w')

    f.write( "digraph Deps {\n")

    for mod, info in deps.iteritems():
        # sys.stderr.write("Discovered module %s\n" % mod)
        nodeMap[mod] = "n%d" % nodeCount
        nodeCount += 1
        label = shorten(mod) + "\\n"
        for service, serviceImpl in info['provides'].iteritems():
            # sys.stderr.write("     Discovered service %s implemented with %s\n" % (service,serviceImpl))
            label += "\\nService=%s" % shorten(service)
            serviceMap[serviceImpl] = mod
        f.write("     %s [ label=\"%s\", color=\"blue\"];\n" % (nodeMap[mod], label))

    f.write("\n")      # for readability

    for mod, info in deps.iteritems():
        for dep, serviceImpl in info['depends'].iteritems():
            f.write("     %s -> %s [ label=\"%s\"];\n" % (
                    nodeMap[mod],
                    shorten(nodeMap[serviceMap[serviceImpl]]),
                    shorten(dep)))
        

    f.write("}\n")
    f.close();
    sys.stderr.write("Now type\ndot -Tpdf -o %s.pdf %s.dot\n" % (
        host, host))
        
