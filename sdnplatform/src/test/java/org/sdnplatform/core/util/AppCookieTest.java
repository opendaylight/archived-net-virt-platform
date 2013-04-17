/*
 * Copyright (c) 2011,2013 Big Switch Networks, Inc.
 * Originally created by David Erickson, Stanford University 
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.sdnplatform.core.util;

import org.sdnplatform.core.util.AppCookie;
import org.sdnplatform.test.PlatformTestCase;

import junit.framework.TestCase;


public class AppCookieTest extends PlatformTestCase {
    public void testAppCookie(){
        int appID = 12;
        int user = 12345;
        long cookie = AppCookie.makeCookie(appID, user);
        TestCase.assertEquals(appID, AppCookie.extractApp(cookie));
        TestCase.assertEquals(user, AppCookie.extractUser(cookie));
        
        // now ensure that we don't exceed our size
        cookie = AppCookie.makeCookie(appID + 0x10000, user);
        TestCase.assertEquals(appID, AppCookie.extractApp(cookie));
        TestCase.assertEquals(user, AppCookie.extractUser(cookie));

    }
}
