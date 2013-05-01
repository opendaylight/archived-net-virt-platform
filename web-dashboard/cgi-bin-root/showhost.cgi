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
# Python script for querying API and displaying connected hosts in a table
#
#

#Importing modules
import re
import sys
import time
import datetime
import json
import urllib2
import switchalias
import dashboardsettings

# Set IP:port of SDNPLATFORM
SDNPLATFORMADDR = dashboardsettings.SDNPLATFORMIP + ':' + dashboardsettings.SDNPLATFORMPORT
 
# define the REST API urls
url = 'http://' + SDNPLATFORMADDR + '/rest/v1/switches'
url2 = 'http://' + SDNPLATFORMADDR + '/rest/v1/device'


# Query JSON from API and load into dictionary
switches = json.load(urllib2.urlopen(url))
switchdevices = json.load(urllib2.urlopen(url2))

# Dictionaries
sorteddict = []
aliasDict = switchalias.aliasDict(SDNPLATFORMADDR)
unsortdict = []
 
# Step through master 'device' list, extract entry for each dictionary.
for index_devices,value1_devices in enumerate(switchdevices):
  tempdict = {}
  tempswitchdict = {}

  # get needed entries in 'devices'
  tempdict['mac'] = value1_devices.get('mac','')
  tempdict['entityClass'] = value1_devices.get('entityClass','')
  tempdict['vlan'] = value1_devices.get('vlan','')
  tempdict['ipv4'] = value1_devices.get('ipv4','Unknown')
  tempdict['switch'] = value1_devices.get('attachmentPoint','')
  tempdict['lastSeen'] = value1_devices.get('lastSeen','')

  # append to final sorted output.
  unsortdict.append(tempdict)


sorteddict = sorted(unsortdict, key=lambda elem: "%s" % (elem['mac']))




#print sorteddict
#print time.strftime('%Y-%m-%d %H:%M:%S %Z', time.gmtime(sorteddict[0]['connectedSince'] / float(1000)))

# Print table output
print 'Content-type: text/html'
print ''
print '<table id="showdeviceoutput" >'
print '<tbody style="border-top: 0px;">'
print '<tr><td>ID</td><td>MAC Address</td><td>Address Space</td><td>VLAN</td><td>IP</td><td>Switch</td><td>Last Seen</td></tr>'
for index_output,value_output in enumerate(sorteddict):
  print '<tr>'
  print '  <td>' + str(index_output + 1) + '</td>'
  print '  <td>'
  for tmp_index,tmp_value in enumerate(value_output['mac']):
    if tmp_index > 0:
      print ', ',
    print tmp_value,
  print '</td>'
  print '  <td>' + value_output.get('entityClass','') + '</td>'
  print '  <td>'
  for tmp_index,tmp_value in enumerate(value_output['vlan']):
    if tmp_index > 0:
      print ', ',
    print tmp_value,
  print '</td>'
  print '  <td>'
  for tmp_index,tmp_value in enumerate(value_output['ipv4']):
    if tmp_index > 0:
      print ', ',
    print tmp_value,
  print '</td>'
  print '  <td>'
  for tmp_index,tmp_value in enumerate(value_output['switch']):
    if tmp_index > 0:
      print ', ',
    print aliasDict.get(tmp_value.get('switchDPID', 'UNKNOWN'), tmp_value.get('switchDPID', 'UNKNOWN')) + ' Port ' + str(tmp_value.get('port', 'UNKNOWN'))
  print '</td>'
  delta = round(time.time(),0) - (round(value_output.get('lastSeen',time.time()) / int(1000)))
  if delta <= 0:
    print '  <td> Now </td>'
  else:
    print '  <td>' + str(datetime.timedelta(seconds=delta))  + '</td>'
  print '</tr>'
print '</tbody>'
print '</table>'

