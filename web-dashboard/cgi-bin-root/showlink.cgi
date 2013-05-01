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
# Python script to query link states from REST API
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
url = 'http://' + SDNPLATFORMADDR + '/rest/v1/switches'
url2 = 'http://' + SDNPLATFORMADDR + '/rest/v1/links'

# Query JSON from API and load into dictionary
switches = json.load(urllib2.urlopen(url))
switchlinks = json.load(urllib2.urlopen(url2))

# Dictionaries
sorteddict = []
aliasDict = switchalias.aliasDict(SDNPLATFORMADDR)
unsortdict = []
statedict = {0: 'FORWARDING', 1: 'DOWN', 2: 'FORWARD', 3: 'BLOCK', 4: 'MASK'}
 
# Step through master 'links' list, extract entry for each dictionary.
for index_links,value1_links in enumerate(switchlinks):
  tempdict = {}
  tempswitchdict = {}

  # get needed entries in 'links'
  tempdict['src-switch'] = value1_links.get('src-switch','')
  tempdict['src-port'] = value1_links.get('src-port','')
  tempdict['src-port-state'] = value1_links.get('src-port-state','')
  tempdict['dst-switch'] = value1_links.get('dst-switch','')
  tempdict['dst-port'] = value1_links.get('dst-port','')
  tempdict['dst-port-state'] = value1_links.get('dst-port-state','')
  tempdict['type'] = value1_links.get('type','')

  # append to final sorted output.
  unsortdict.append(tempdict)


sorteddict = sorted(unsortdict, key=lambda elem: "%s %02d" % (elem['src-switch'], elem['src-port']))

# Print table output
print 'Content-type: text/html'
print ''
print '<table id="showlinkoutput" >'
print '<tbody>'
print '<tr><td>ID</td><td>Source Switch</td><td>Source Port</td><td>Source Port State</td><td>Destination Switch</td><td>Destination Port</td><td>Destination Port State</td><td>Connection Type</td></tr>'
for index_output,value_output in enumerate(sorteddict):
  print '<tr>'
  print '  <td>' + str(index_output + 1) + '</td>'
  print '  <td>' 
  print aliasDict.get(value_output.get('src-switch', 'UNKNOWN'), value_output.get('src-switch', 'UNKNOWN'))
  print '</td>'
  print '  <td>' + str(value_output.get('src-port','')) + '</td>'
  print '  <td>' + statedict.get(value_output.get('src-port-state',0),'DOWN') + '</td>'
  print '  <td>' 
  print aliasDict.get(value_output.get('dst-switch', 'UNKNOWN'), value_output.get('dst-switch', 'UNKNOWN'))
  print '</td>'
  print '  <td>' + str(value_output.get('dst-port','')) + '</td>'
  print '  <td>' + statedict.get(value_output.get('dst-port-state',0),'DOWN') + '</td>'
  print '  <td>' + value_output.get('type','') + '</td>'
  print '</tr>'
print '</tbody>'
print '</table>'

