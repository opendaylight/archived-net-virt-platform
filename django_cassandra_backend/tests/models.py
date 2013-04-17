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

from django.db import models
from djangotoolbox.fields import ListField

class Slice(models.Model):
    name = models.CharField(max_length=64)
    
    class Meta:
        db_table = 'Slice'
        ordering = ['id']
        
class Host(models.Model):
    mac = models.CharField(max_length=20, db_index=True)
    ip = models.CharField(max_length=20, db_index = True)
    slice = models.ForeignKey(Slice, db_index=True)
    
    class Meta:
        db_table = 'Host'
        ordering = ['id']
        
class Tag(models.Model):
    name = models.CharField(max_length=64)
    value = models.CharField(max_length=256)
    host = models.ForeignKey(Host, db_index=True)
    
    class Meta:
        db_table = 'Tag'
        ordering = ['id']

class Test(models.Model):
    test_date = models.DateField(null=True)
    test_datetime = models.DateTimeField(null=True)
    test_time = models.TimeField(null=True)
    test_decimal = models.DecimalField(null=True, max_digits=10, decimal_places=3)
    test_text = models.TextField(null=True)
    #test_list = ListField(models.CharField(max_length=500))
    
    class Meta:
        db_table = 'Test'
        ordering = ['id']



class CompoundKeyModel(models.Model):
    name = models.CharField(max_length=64)
    index = models.IntegerField()
    extra = models.CharField(max_length=32, default='test')
    
    class CassandraSettings:
        COMPOUND_KEY_FIELDS = ('name', 'index')


class CompoundKeyModel2(models.Model):
    slice = models.ForeignKey(Slice)
    name = models.CharField(max_length=64)
    index = models.IntegerField()
    extra = models.CharField(max_length=32)
    
    class CassandraSettings:
        COMPOUND_KEY_FIELDS = ('slice', 'name', 'index')
        COMPOUND_KEY_SEPARATOR = '#'

class CompoundKeyModel3(models.Model):
    name = models.CharField(max_length=32)

    class CassandraSettings:
        COMPOUND_KEY_FIELDS = ('name')
