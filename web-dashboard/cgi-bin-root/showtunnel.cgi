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
#
# Python script to query tunnel states from REST API
#
#

#Importing modules
import re
import sys
import time
import json
import urllib2
import switchalias
import dashboardsettings

# Set IP:port of SDNPLATFORM
SDNPLATFORMADDR = dashboardsettings.SDNPLATFORMIP + ':' + dashboardsettings.SDNPLATFORMPORT
 
# define the REST API urls
url = 'http://' + SDNPLATFORMADDR + '/rest/v1/tunnel-manager/all'

# Query JSON from API and load into dictionary
tunnels = json.load(urllib2.urlopen(url))

# Dictionaries
sorteddict = []
aliasDict = switchalias.aliasDict(SDNPLATFORMADDR)
unsortdict = []
statedict = {0: 'FORWARDING', 1: 'DOWN', 2: 'FORWARD', 3: 'BLOCK', 4: 'MASK'}
 
# Step through master 'tunnels' list, extract entry for each dictionary.
for index_tunnels,value1_tunnels in tunnels.iteritems():
  tempdict = {}
  temptunneldict = {}

  # get needed entries in 'links' 
  tempdict['dpid'] = value1_tunnels.get('hexDpid','')
  tempdict['tunnelEnabled'] = value1_tunnels.get('tunnelEnabled','')
  tempdict['tunnelIPAddr'] = value1_tunnels.get('tunnelIPAddr','')
  tempdict['tunnelActive'] = value1_tunnels.get('tunnelActive','')
  tempdict['tunnelState'] = value1_tunnels.get('tunnelState','')
  tempdict['tunnelIf'] = value1_tunnels.get('tunnelEndPointIntfName','')
  tempdict['tunnelCapable'] = value1_tunnels.get('tunnelCapable','')
  # append to final sorted output.
  if value1_tunnels.get('tunnelCapable',''):
    unsortdict.append(tempdict)

sorteddict = sorted(unsortdict, key=lambda elem: "%s %s" % (elem['dpid'], elem['tunnelIPAddr']))

# Print table output
print 'Content-type: text/html'
print ''
print '<table id="showtunneloutput" >'
print '<tbody>'
print '<tr><td>ID</td><td>Switch</td><td>Tunnel Source IP</td><td>Tunnel Interface</td><td>Tunnel Enabled</td><td>Tunnel State</td></tr>'

for index_output,value_output in enumerate(sorteddict):
  print '<tr>'
  print '  <td>' + str(index_output + 1) + '</td>'
  print '  <td>' 
  print aliasDict.get(value_output.get('dpid', 'UNKNOWN'), value_output.get('dpid', 'UNKNOWN'))
  print '</td>'
  print '  <td>' + str(value_output.get('tunnelIPAddr','')) + '</td>'
  print '  <td>' + value_output.get('tunnelIf','UNKNOWN') + '</td>'
  print '  <td>' + str(value_output.get('tunnelActive','')) + '</td>'
  print '  <td>' + value_output.get('tunnelState','') + '</td>'
  print '</tr>'
print '</tbody>'
print '</table>'

