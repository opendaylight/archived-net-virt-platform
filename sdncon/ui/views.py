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

#  Views for the web UI
#

from django.shortcuts import render_to_response, render
from django.utils import simplejson
from django.http import HttpResponse
from django.contrib.auth.decorators import login_required
from django.contrib.auth.models import User
from django.template import RequestContext
from django.template.loader import render_to_string
from sdncon.apploader import AppLoader, AppLister
from sdncon.clusterAdmin.utils import conditionally, isCloudBuild
from sdncon.clusterAdmin.models import Customer, Cluster, CustomerUser
from scripts.showswitch import show_switch_data
from scripts.buildtopology import build_topology_data
from scripts.showlink import switch_link_data
from scripts.showhost import show_host_data
import os


JSON_CONTENT_TYPE = 'application/json'

# --- View for the root page of any application
def index(request):
    return render_to_response('ui/templates/index.html')

def show_switch(request):
    text = show_switch_data(request)
    response = HttpResponse(text)
    return response

def show_link(request):
    text = switch_link_data(request)
    response = HttpResponse(text)
    return response

def show_host(request):
    text = show_host_data(request)
    response = HttpResponse(text)
    return response


def build_topology(request):
    text = build_topology_data(request)
    response = HttpResponse(text)
    return response


   
