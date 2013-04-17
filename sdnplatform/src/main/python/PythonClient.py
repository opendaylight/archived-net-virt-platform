#!/usr/bin/env python
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

import sys
sys.path.append('../../../target/gen-py')

from packetstreamer import PacketStreamer
from packetstreamer.ttypes import *

from thrift import Thrift
from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol

try:

    # Make socket
    transport = TSocket.TSocket('localhost', 9090)

    # Buffering is critical. Raw sockets are very slow
    transport = TTransport.TFramedTransport(transport)

    # Wrap in a protocol
    protocol = TBinaryProtocol.TBinaryProtocol(transport)

    # Create a client to use the protocol encoder
    client = PacketStreamer.Client(protocol)

    # Connect!
    transport.open()

    while 1:
        packets = client.getPackets("session1")
        print 'session1 packets num: %d' % (len(packets))
        count = 1
        for packet in packets:
            print "Packet %d: %s"% (count, packet)
            if "FilterTimeout" in packet:
                sys.exit()
            count += 1 

    # Close!
    transport.close()

except Thrift.TException, tx:
    print '%s' % (tx.message)

except KeyboardInterrupt, e:
    print 'Bye-bye'
