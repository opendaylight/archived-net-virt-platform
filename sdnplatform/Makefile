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

.PHONY: docs doc all test tests count install clean

all:
	ant

init:
	ant init

docs:
	ant javadoc

doc:
	ant javadoc

javadoc:
	ant javadoc

check: tests
test: tests

tests: all unit-tests

unit-tests:
	ant tests

regression-tests:
	make -C regress tests

count: 
	@find src -name \*.java | xargs wc -l | sort -n

clean:
	ant clean
