#!/bin/bash
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

d=$(dirname "$0")
MAIN_CLASS=$1
LIBRARIES=$2
[ "${MAIN_CLASS}" ] || { echo "Run 'ant eclipse' to generate Eclipse project files"; exit 1; }


cat >"$d/.project" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
	<name>sdnplatform</name>
	<comment></comment>
	<projects>
	</projects>
	<buildSpec>
		<buildCommand>
			<name>org.eclipse.jdt.core.javabuilder</name>
			<arguments>
			</arguments>
		</buildCommand>
	</buildSpec>
	<natures>
		<nature>org.eclipse.jdt.core.javanature</nature>
	</natures>
</projectDescription>
EOF


cat >"$d/.classpath" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
	<classpathentry kind="src" path="src/main/java" output="target/bin"/>
	<classpathentry kind="src" path="src/main/resources"/>
        <classpathentry kind="src" path="src/test/java" output="target/bin-test"/>
        <classpathentry kind="src" path="lib/gen-java" output="target/bin"/>
EOF
(
IFS=":"
for l in ${LIBRARIES}; do
cat >>$d/.classpath <<EOF
	<classpathentry exported="true" kind="lib" path="$l"/>
EOF
done
)
cat >>"$d/.classpath" <<EOF
	<classpathentry exported="true" kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
	<classpathentry kind="output" path="target/bin"/>
</classpath>
EOF

mkdir -p $d/.settings
cat >$d/.settings/edu.umd.cs.findbugs.core.prefs  <<EOF
excludefilter0=findbugs-exclude.xml|true
filter_settings=Medium|BAD_PRACTICE,CORRECTNESS,MALICIOUS_CODE,MT_CORRECTNESS,PERFORMANCE,SECURITY,STYLE|false|20
filter_settings_neg=NOISE,I18N,EXPERIMENTAL|
EOF
