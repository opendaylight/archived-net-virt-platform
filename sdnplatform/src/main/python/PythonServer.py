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
import logging
sys.path.append('../../../target/gen-py')

from packetstreamer import PacketStreamer
from packetstreamer.ttypes import *

from thrift.transport import TSocket
from thrift.transport import TTransport
from thrift.protocol import TBinaryProtocol
from thrift.server import TServer

class PacketStreamerHandler:
  def __init__(self):
    logging.handlers.codecs = None
    self.log = logging.getLogger("packetstreamer")
    self.log.setLevel(logging.DEBUG)
    handler = logging.handlers.SysLogHandler("/dev/log")
    handler.setFormatter(logging.Formatter("%(name)s: %(levelname)s %(message)s"))
    self.log.addHandler(handler)

  def ping(self):
    self.log.debug('ping()')
    return true

  def pushPacketSync(self, packet):
    self.log.debug('receive a packet synchronously: %s' %(packet))
    return 0

  def pushPacketAsync(self, packet):
    self.log.debug('receive a packet Asynchronously: %s' %(packet))

handler = PacketStreamerHandler()
processor = PacketStreamer.Processor(handler)
transport = TSocket.TServerSocket(9090)
tfactory = TTransport.TBufferedTransportFactory()
pfactory = TBinaryProtocol.TBinaryProtocolFactory()

server = TServer.TSimpleServer(processor, transport, tfactory, pfactory)

# You could do one of these for a multithreaded server
#server = TServer.TThreadedServer(processor, transport, tfactory, pfactory)
#server = TServer.TThreadPoolServer(processor, transport, tfactory, pfactory)

print 'Starting the server...'
server.serve()
print 'done.'
